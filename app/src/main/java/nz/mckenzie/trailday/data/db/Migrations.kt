package nz.mckenzie.trailday.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Where the DOC cache gains the source it came from.
 *
 * **Why this exists at all.** DOC numbers its features per service, so the tracks, the huts, the
 * campsites and the bike locations each start at 1 - a hut and a track really can both be 123. The cache
 * was keyed on that number alone, which was right while there was one service and is a silent loss the
 * moment there are four: a downloaded hut could overwrite a downloaded track. The key is now the pair.
 *
 * **The download is carried across, not dropped.** Every row in the old table was a track, so each is
 * copied with `source = 'tracks'` and with the three facts the old table held as columns written into
 * the facts column the new model reads. A walker who downloaded an area keeps it and keeps its fields -
 * and nothing is fetched again because a version changed.
 *
 * The table is rebuilt rather than altered because SQLite cannot change a primary key in place. The
 * facts are written by SQL rather than by the app because a migration is the only code that runs
 * between two schemas, and it has no Kotlin model to hand.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS offline_doc_items (
                source TEXT NOT NULL,
                objectId INTEGER NOT NULL,
                name TEXT NOT NULL,
                lengthM REAL NOT NULL,
                pathsJson TEXT NOT NULL,
                factsJson TEXT NOT NULL,
                downloadedAtEpochMs INTEGER NOT NULL,
                PRIMARY KEY(source, objectId)
            )
            """.trimIndent()
        )

        // `kind`, `trackType` and `flocId` were columns; they are facts now, and they keep their DOC
        // field names (`SubObjectType`, `WEB_TRACK_TYPE`, `FlocID`) so the reader finds them exactly as
        // it finds them on the wire. A quote inside a value is escaped because these are JSON strings -
        // no DOC value has one today, and a value that grew one must not break the row.
        db.execSQL(
            """
            INSERT INTO offline_doc_items
                (source, objectId, name, lengthM, pathsJson, factsJson, downloadedAtEpochMs)
            SELECT
                'tracks',
                objectId,
                name,
                lengthM,
                pathsJson,
                '{"SubObjectType":"' || REPLACE(COALESCE(kind, ''), '"', '\"') ||
                '","WEB_TRACK_TYPE":"' || REPLACE(COALESCE(trackType, ''), '"', '\"') ||
                '","FlocID":"' || REPLACE(COALESCE(flocId, ''), '"', '\"') || '"}',
                downloadedAtEpochMs
            FROM offline_doc_tracks
            """.trimIndent()
        )

        db.execSQL("DROP TABLE offline_doc_tracks")

        // Room checks the indices against the schema it expects, so this one is named the way Room names
        // it - the name is part of what a migration has to get right.
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_offline_doc_items_downloadedAtEpochMs " +
                "ON offline_doc_items (downloadedAtEpochMs)"
        )
    }
}

/**
 * Where the DOC cache gains the pictures of what it holds.
 *
 * **Nothing is carried across, because there is nothing to carry.** The table is new and every picture
 * arrives with the next download of an area: a picture is a copy of something DOC publishes, and DOC is
 * still publishing it. A walker's downloaded material is untouched - what changes is that the next area
 * they download brings the huts' and campsites' pictures with it.
 *
 * From here on, clearing the cache clears the pictures too: they belong to the material, and a picture
 * left behind would be storage nobody could see or get rid of.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS offline_doc_pictures (
                url TEXT NOT NULL,
                bytes BLOB NOT NULL,
                fetchedAtEpochMs INTEGER NOT NULL,
                PRIMARY KEY(url)
            )
            """.trimIndent()
        )
    }
}

/**
 * Where a trip remembers following one of DOC's lines.
 *
 * The column is nullable and nothing is guessed for the trips already on the phone: a pass that was made
 * before this could have followed DOC, and saying so now would be the app inventing a fact about somebody's
 * walk. A pass with no value is a pass that followed nothing of DOC's.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recorded_sessions ADD COLUMN docRef TEXT")
    }
}

/**
 * Where a place gains the photograph the walker attached to it.
 *
 * A new table rather than a column on `assets`: the place list reads every asset on every map
 * refresh, and a photograph has no business being carried by a row that is not being looked at.
 * Nothing is guessed for places already on the phone - a place with no row here is a place with no
 * photograph, which is what it had before.
 */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `asset_photos` (
                `assetId` INTEGER NOT NULL,
                `jpeg` BLOB NOT NULL,
                `addedAtEpochMs` INTEGER NOT NULL,
                PRIMARY KEY(`assetId`),
                FOREIGN KEY(`assetId`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}

/**
 * Where the cache stops holding tracks from DOC's deprecated dataset.
 *
 * The replacement service numbers its tracks differently, so the old rows would sit beside the new
 * ones as apparent duplicates rather than being replaced. The tracks are the one service that moved;
 * the huts, campsites and bike locations are untouched. A walker re-downloads an area to get tracks
 * again, which is the same act that was always needed to refresh a cache.
 */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM offline_doc_items WHERE source = 'tracks'")
    }
}

/**
 * Where an area's row says which pack of tiles it is.
 *
 * The drawn Topographic map became something a walker can download in named pieces, and those pieces want
 * the same list, size and state an imagery area has always had - so one table serves both, and this is the
 * column that says which is which. **Every row that exists was imagery**, because that was the only pack
 * with areas until now, so that is what they are given.
 */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE offline_areas ADD COLUMN pack TEXT NOT NULL DEFAULT 'imagery'")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_areas_pack ON offline_areas (pack)")
    }
}

/**
 * Where the phone gains a second publisher's tracks.
 *
 * **Nothing is carried across, because there is nothing to carry.** The table is new and every row
 * arrives with the next download of an area: this is a cache of what somebody else publishes, and they
 * are still publishing it. A walker's downloaded imagery and DOC material are untouched - what changes
 * is that the next area they download can bring the council and community tracks with it.
 *
 * Its own table rather than a second kind of row in `offline_doc_items`, because the two are different
 * publishers with different keys: DOC's rows are keyed by a number the service gives per service, and
 * these by a UUID. One table holding both would be keyed by a string meaning one thing in one half of it
 * and another in the other.
 *
 * From here on, clearing this cache clears only this: a walker who clears DOC's material keeps the
 * tracks their council publishes, and the other way round.
 */
internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS offline_other_tracks (
                globalId TEXT NOT NULL,
                name TEXT NOT NULL,
                lengthM REAL NOT NULL,
                pathsJson TEXT NOT NULL,
                factsJson TEXT NOT NULL,
                downloadedAtEpochMs INTEGER NOT NULL,
                PRIMARY KEY(globalId)
            )
            """.trimIndent()
        )

        // Room checks the indices against the schema it expects, so this one is named the way Room names
        // it - the name is part of what a migration has to get right.
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_offline_other_tracks_downloadedAtEpochMs " +
                "ON offline_other_tracks (downloadedAtEpochMs)"
        )
    }
}

/**
 * Somebody else's tracks could be downloaded but not **named**, so three downloads of three hillsides read
 * as one number of tracks with no way to tell what had been asked for.
 *
 * The tracks stay where they are: this adds the walker's own record of each download beside them, which is
 * a name, the box, how many tracks came back and when. Nothing is backfilled - a box downloaded before this
 * build was never named, and inventing a name for it would be making one up.
 */
internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS offline_other_track_areas (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                minLat REAL NOT NULL,
                minLng REAL NOT NULL,
                maxLat REAL NOT NULL,
                maxLng REAL NOT NULL,
                trackCount INTEGER NOT NULL,
                downloadedAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_offline_other_track_areas_downloadedAtEpochMs " +
                "ON offline_other_track_areas (downloadedAtEpochMs)"
        )
    }
}

/**
 * DOC's material could be downloaded but not **named**, for the same reason somebody else's tracks could
 * not: three hillsides read as one number of records, with no way to tell "Otago" from a box drawn by
 * accident.
 *
 * The material stays where it is. This adds the walker's own record of each download beside it, which is a
 * name, the ground, how many records came back and when. Nothing is backfilled: an area downloaded before
 * this build was never named, and inventing a name for it would be making one up.
 */
internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS offline_doc_areas (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                minLat REAL NOT NULL,
                minLng REAL NOT NULL,
                maxLat REAL NOT NULL,
                maxLng REAL NOT NULL,
                itemCount INTEGER NOT NULL,
                downloadedAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_offline_doc_areas_downloadedAtEpochMs " +
                "ON offline_doc_areas (downloadedAtEpochMs)"
        )
    }
}
/**
 * Journeys: **one optional column on an asset, saying what a journey was made from.**
 *
 * A journey is an ordinary track - that is the whole point of it - so it needs nowhere to live and
 * nothing new to follow, export or back up. This column is the only thing that tells the app one track
 * is a journey and which of DOC's sections it came from. Every row written before this build is null,
 * which is exactly right: none of them was a journey.
 */
internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE assets ADD COLUMN journey TEXT")
    }
}
