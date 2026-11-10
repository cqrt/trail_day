package nz.mckenzie.trailday.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import nz.mckenzie.trailday.domain.cache.FactsJson
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The database changes this app has made, each run on a database that already had something in it.
 *
 * **Carry what is still true; drop what is no longer.** The DOC cache gaining a source carried a
 * walker's downloaded tracks across, because they were still that service's tracks. Moving to DOC's
 * replacement tracks dataset is the other case: its object ids are different, so the old rows would sit
 * beside the new ones as duplicates, and they leave the cache instead.
 *
 * Room's own harness is what makes these real checks rather than descriptions: it builds a database from
 * the exported schema, runs the migration, and validates the result against the schema the build
 * produces.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrailDayDatabase::class.java
    )

    @Test
    fun theDocCacheGainsItsSourceAndKeepsWhatWasDownloaded() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO offline_doc_tracks
                    (objectId, name, kind, trackType, flocId, lengthM, pathsJson, downloadedAtEpochMs)
                VALUES
                    (35239046, 'Glory Tk', 'Walking Track', 'One way', 'BLUFFHIL-TK02', 1234.5,
                     '[[-46.6,168.3],[-46.61,168.31]]', 1790000000000)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DATABASE, 2, true, MIGRATION_1_2)

        migrated.query(
            "SELECT source, objectId, name, lengthM, pathsJson, factsJson FROM offline_doc_items"
        ).use { rows ->
            assertTrue("the downloaded track is still there", rows.moveToFirst())
            assertEquals("and it came from the tracks service", "tracks", rows.getString(0))
            assertEquals(35239046L, rows.getLong(1))
            assertEquals("Glory Tk", rows.getString(2))
            assertEquals(1234.5, rows.getDouble(3), 1e-9)
            assertEquals("[[-46.6,168.3],[-46.61,168.31]]", rows.getString(4))

            // The three columns the old table had are facts now, under the names DOC uses for them - so
            // the reader finds them exactly as it finds them on the wire.
            val facts = FactsJson.read(rows.getString(5))
            assertEquals("Walking Track", facts["SubObjectType"])
            assertEquals("One way", facts["WEB_TRACK_TYPE"])
            assertEquals("BLUFFHIL-TK02", facts["FlocID"])

            assertEquals("and nothing was invented", 1, rows.count)
        }
    }

    @Test
    fun aHutAndATrackMayShareDOCsNumberOnceTheKeyIsThePair() {
        // The reason for the change: DOC numbers its features per service, so the number alone is not an
        // identity. Two rows with the same number and different sources coexist; before this they could
        // not, and one would have overwritten the other without saying so.
        helper.createDatabase(TEST_DATABASE_SHARED, 1).close()
        val migrated = helper.runMigrationsAndValidate(TEST_DATABASE_SHARED, 2, true, MIGRATION_1_2)

        migrated.execSQL(
            """
            INSERT INTO offline_doc_items
                (source, objectId, name, lengthM, pathsJson, factsJson, downloadedAtEpochMs)
            VALUES
                ('tracks', 123, 'A track', 0.0, '[[-46.6,168.3]]', '{}', 1),
                ('huts', 123, 'A hut', 0.0, '[[-46.6,168.3]]', '{}', 2)
            """.trimIndent()
        )

        migrated.query("SELECT COUNT(*) FROM offline_doc_items").use { rows ->
            rows.moveToFirst()
            assertEquals("both are kept", 2, rows.getInt(0))
        }
    }

    @Test
    fun theDocCacheGainsThePicturesOfWhatItHolds() {
        // From version 2, which had the material and nowhere to put a picture of it. Nothing is carried
        // across, because there is nothing to carry: a picture arrives with the next download of an area,
        // and DOC is still publishing it.
        helper.createDatabase(TEST_DATABASE_PICTURES, 2).close()

        val migrated = helper.runMigrationsAndValidate(TEST_DATABASE_PICTURES, 3, true, MIGRATION_2_3)

        migrated.execSQL(
            """
            INSERT INTO offline_doc_pictures (url, bytes, fetchedAtEpochMs)
            VALUES ('https://www.doc.govt.nz/thumbs/large/link/abc.jpg', X'01020304', 1790000000000)
            """.trimIndent()
        )

        migrated.query("SELECT url, bytes FROM offline_doc_pictures").use { rows ->
            assertTrue("the picture is there", rows.moveToFirst())
            assertEquals("https://www.doc.govt.nz/thumbs/large/link/abc.jpg", rows.getString(0))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), rows.getBlob(1))
        }
    }

    @Test
    fun aPlaceGainsSomewhereToKeepItsPhoto() {
        // Version 4 held places and their geometry but nowhere for a photograph. The table is new,
        // so nothing is guessed about places already on the phone; a new row is what proves it is
        // usable, and the foreign key is what lets deleting the place take the picture with it.
        helper.createDatabase(TEST_DATABASE_PHOTOS, 4).apply {
            execSQL(
                """
                INSERT INTO assets
                    (id, name, kind, shape, notes, active, createdAtEpochMs, lengthM, sourceRef)
                VALUES
                    (1, 'Creek camp', 'WAYPOINT', 'POINT', NULL, 1, 1790000000000, 0.0, NULL)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE_PHOTOS,
            5,
            true,
            MIGRATION_4_5
        )

        migrated.execSQL(
            "INSERT INTO asset_photos (assetId, jpeg, addedAtEpochMs) VALUES (1, X'01020304', 1790000000000)"
        )
        migrated.query("SELECT assetId, jpeg FROM asset_photos").use { rows ->
            assertTrue("the photograph is there", rows.moveToFirst())
            assertEquals(1L, rows.getLong(0))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), rows.getBlob(1))
        }
    }

    @Test
    fun oldTracksLeaveTheCacheWhenTheDatasetChanges() {
        // Version 5's tracks came from the deprecated service, whose object ids differ from the
        // replacement's. Keeping them would make the same track appear twice after the next download.
        helper.createDatabase(TEST_DATABASE_TRACKS, 5).apply {
            execSQL(
                """
                INSERT INTO offline_doc_items
                    (source, objectId, name, lengthM, pathsJson, factsJson, downloadedAtEpochMs)
                VALUES
                    ('tracks', 35239127, 'Old Forks Flat', 0.0, '[[-46.0,168.0]]', '{}', 1),
                    ('huts', 35239127, 'A hut that stays', 0.0, '[[-46.0,168.0]]', '{}', 2)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE_TRACKS,
            6,
            true,
            MIGRATION_5_6
        )

        migrated.query("SELECT source, name FROM offline_doc_items ORDER BY source").use { rows ->
            assertTrue("the hut is still there", rows.moveToFirst())
            assertEquals("huts", rows.getString(0))
            assertEquals("A hut that stays", rows.getString(1))
            assertEquals("and the old track is gone", 1, rows.count)
        }
    }

    @Test
    fun areasAlreadyDownloadedBecomeImageryAreas() {
        // Version 6 had one pack with areas, and every row in the table was one of its. The column that says
        // which pack a row belongs to has to answer "imagery" for all of them, or a season of downloaded
        // imagery would vanish from the list that describes it.
        helper.createDatabase(TEST_DATABASE_AREAS, 6).apply {
            execSQL(
                """
                INSERT INTO offline_areas
                    (name, minLat, minLng, maxLat, maxLng, minZoom, maxZoom,
                     plannedTiles, downloadedTiles, bytes, createdAtEpochMs)
                VALUES
                    ('Tararuas', -41.0, 175.0, -40.8, 175.3, 10, 16, 300, 300, 12000000, 1)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE_AREAS,
            7,
            true,
            MIGRATION_6_7
        )

        migrated.query("SELECT name, pack, downloadedTiles FROM offline_areas").use { rows ->
            assertTrue("the area is still there", rows.moveToFirst())
            assertEquals("Tararuas", rows.getString(0))
            assertEquals("and it is an imagery area", "imagery", rows.getString(1))
            assertEquals("with everything else it had", 300L, rows.getLong(2))
        }
    }

    @Test
    fun walksAlreadyKeptAreTheWalkerSOwnAndNotImports() {
        // Version 11 had walks and no way to tell one that came in a file from one that was walked with
        // the phone, because a file could only ever become a place. Every row that exists was therefore
        // recorded here, so the new column has to answer "not imported" for all of them - a build that
        // guessed the other way would call a season of the walker's own walks somebody else's.
        helper.createDatabase(TEST_DATABASE_RECORDINGS, 11).apply {
            execSQL(
                """
                INSERT INTO recorded_sessions
                    (id, name, assetId, docRef, startedAtEpochMs, endedAtEpochMs, status,
                     distanceM, durationMs, pointCount)
                VALUES
                    (1, 'Home block 14 Sep', NULL, NULL, 1790000000000, 1790003600000, 'FINISHED',
                     2350.0, 3600000, 412)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE_RECORDINGS,
            12,
            true,
            MIGRATION_11_12
        )

        migrated.query("SELECT name, imported, distanceM FROM recorded_sessions").use { rows ->
            assertTrue("the walk is still there", rows.moveToFirst())
            assertEquals("Home block 14 Sep", rows.getString(0))
            assertEquals("and it was walked with the phone", 0, rows.getInt(1))
            assertEquals("with everything else it had", 2350.0, rows.getDouble(2), 1e-9)
        }

        // And a row written by this build can say otherwise, so the column is usable rather than merely
        // present.
        migrated.execSQL(
            """
            INSERT INTO recorded_sessions
                (name, startedAtEpochMs, status, distanceM, durationMs, pointCount, imported)
            VALUES ('Morning_Walk_2026-11-03', 1790000000000, 'FINISHED', 1240.0, 0, 1500, 1)
            """.trimIndent()
        )
        migrated.query("SELECT COUNT(*) FROM recorded_sessions WHERE imported = 1").use { rows ->
            rows.moveToFirst()
            assertEquals("an imported activity is kept as one", 1, rows.getInt(0))
        }
    }

    @Test
    fun walksAlreadyKeptFollowedOneLineAndSaySo() {
        // Version 12 kept one reference per walk, because the recorder could only ever be measured against
        // one published line at a time. The list arrives with this build, and every walk already saved
        // followed exactly one line - or none - so its one reference becomes a list of one. A walk that
        // followed nothing keeps nothing, which is the truth about it rather than a list of no lines.
        helper.createDatabase(TEST_DATABASE_FOLLOWED, 12).apply {
            execSQL(
                """
                INSERT INTO recorded_sessions
                    (id, name, assetId, docRef, startedAtEpochMs, endedAtEpochMs, status,
                     distanceM, durationMs, pointCount, imported)
                VALUES
                    (1, 'Major Hornbrook Track', NULL, 'doc-tracks:35237557', 1790000000000,
                     1790003600000, 'FINISHED', 2350.0, 3600000, 412, 0),
                    (2, 'Home block 14 Sep', NULL, NULL, 1790000000000, 1790003600000, 'FINISHED',
                     2350.0, 3600000, 412, 0)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE_FOLLOWED,
            13,
            true,
            MIGRATION_12_13
        )

        migrated.query("SELECT name, docRef, docRefs FROM recorded_sessions ORDER BY id").use { rows ->
            assertTrue(rows.moveToFirst())
            assertEquals("the walk is still there", "Major Hornbrook Track", rows.getString(0))
            assertEquals("its reference is untouched", "doc-tracks:35237557", rows.getString(1))
            assertEquals("and it is now a walk of one line", "doc-tracks:35237557", rows.getString(2))

            assertTrue(rows.moveToNext())
            assertNull("a walk that followed nothing gained nothing", rows.getString(2))
        }

        // And a row written by this build can hold more than one, so the column is usable rather than
        // merely present.
        migrated.execSQL(
            """
            INSERT INTO recorded_sessions
                (name, docRef, docRefs, startedAtEpochMs, status, distanceM, durationMs, pointCount, imported)
            VALUES ('Two tracks', 'doc-tracks:2', 'doc-tracks:1' || char(10) || 'doc-tracks:2',
                    1790000000000, 'FINISHED', 1240.0, 0, 1500, 0)
            """.trimIndent()
        )
        migrated.query("SELECT COUNT(*) FROM recorded_sessions WHERE docRefs LIKE '%' || char(10) || '%'").use { rows ->
            rows.moveToFirst()
            assertEquals("a walk of two lines is kept as one", 1, rows.getInt(0))
        }
    }

    @Test
    fun tripsAlreadyKeptAreWalksUntilSomebodySaysOtherwise() {
        // Version 13 drew every recorded line in the same green and saved every one as a TRACK, so there is
        // no fact in the database to carry across: the honest answer for a trip of that age is a walk, and
        // a ride already on the phone is one tap away on its own page.
        helper.createDatabase(TEST_DATABASE_MODES, 13).apply {
            execSQL(
                """
                INSERT INTO recorded_sessions
                    (id, name, assetId, docRef, docRefs, startedAtEpochMs, endedAtEpochMs, status,
                     distanceM, durationMs, pointCount, imported)
                VALUES
                    (1, 'Home block 14 Sep', NULL, NULL, NULL, 1790000000000, 1790003600000, 'FINISHED',
                     2350.0, 3600000, 412, 0),
                    (2, 'Morning_Walk_2026-11-03', NULL, NULL, NULL, 1790000000000, 1790003600000,
                     'FINISHED', 1240.0, 0, 1500, 1)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE_MODES,
            14,
            true,
            MIGRATION_13_14
        )

        migrated.query("SELECT name, mode FROM recorded_sessions ORDER BY id").use { rows ->
            assertTrue("the walk is still there", rows.moveToFirst())
            assertEquals("Home block 14 Sep", rows.getString(0))
            assertEquals("and it reads as a walk", "WALK", rows.getString(1))

            assertTrue("so does the one that came from a file", rows.moveToNext())
            assertEquals("Morning_Walk_2026-11-03", rows.getString(0))
            assertEquals("WALK", rows.getString(1))
        }

        // And a row written by this build can say otherwise, so the column is usable rather than merely
        // present.
        migrated.execSQL(
            """
            INSERT INTO recorded_sessions
                (name, startedAtEpochMs, status, distanceM, durationMs, pointCount, imported, mode)
            VALUES ('Taupo ride', 1790000000000, 'FINISHED', 18200.0, 5460000, 2400, 0, 'RIDE')
            """.trimIndent()
        )
        migrated.query("SELECT COUNT(*) FROM recorded_sessions WHERE mode = 'RIDE'").use { rows ->
            rows.moveToFirst()
            assertEquals("a ride is kept as a ride", 1, rows.getInt(0))
        }
    }

    private companion object {
        /** Names of this test's own: the real database is never opened by a test. */
        const val TEST_DATABASE = "migration-test.db"

        /**
         * A second one, because the harness migrates the file it is given and two tests sharing a name
         * would be two tests fighting over one database rather than two cases of one change.
         */
        const val TEST_DATABASE_SHARED = "migration-test-shared.db"

        /** And a third, for the version 2 to 3 change, so neither file is migrated twice. */
        const val TEST_DATABASE_PICTURES = "migration-test-pictures.db"

        /** And a fourth, for the version 4 to 5 change. */
        const val TEST_DATABASE_PHOTOS = "migration-test-photos.db"

        /** And a fifth, for the version 5 to 6 change. */
        const val TEST_DATABASE_TRACKS = "migration-test-tracks.db"

        /** And a sixth, for the version 6 to 7 change. */
        const val TEST_DATABASE_AREAS = "migration-test-areas.db"

        /** And a seventh, for the version 11 to 12 change. */
        const val TEST_DATABASE_RECORDINGS = "migration-test-recordings.db"

        /** And an eighth, for the version 12 to 13 change. */
        const val TEST_DATABASE_FOLLOWED = "migration-test-followed.db"

        /** And a ninth, for the version 13 to 14 change. */
        const val TEST_DATABASE_MODES = "migration-test-modes.db"
    }
}
