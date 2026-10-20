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