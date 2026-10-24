package nz.mckenzie.trailday.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import nz.mckenzie.trailday.domain.doc.DocFactsJson
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
            val facts = DocFactsJson.read(rows.getString(5))
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
    }
}
