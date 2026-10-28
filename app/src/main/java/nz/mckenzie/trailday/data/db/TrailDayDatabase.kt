package nz.mckenzie.trailday.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AssetEntity::class,
        AssetPointEntity::class,
        AssetPhotoEntity::class,
        RecordedSessionEntity::class,
        RecordedPointEntity::class,
        RecordedBreakEntity::class,
        OfflineAreaEntity::class,
        OfflineDocItemEntity::class,
        OfflineDocPictureEntity::class,
        OfflineOtherTrackEntity::class,
        OfflineOtherTrackAreaEntity::class,
        OfflineDocAreaEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class TrailDayDatabase : RoomDatabase() {

    abstract fun assetDao(): AssetDao
    abstract fun recordingDao(): RecordingDao
    abstract fun offlineAreaDao(): OfflineAreaDao

    /** DOC's material kept for offline use. */
    abstract fun offlineDocItemDao(): OfflineDocItemDao

    /** The national tracks dataset's rows kept for offline use, which are a different publisher's. */
    abstract fun offlineOtherTrackDao(): OfflineOtherTrackDao

    /** The named boxes those rows were downloaded for: the offline screen's list of areas. */
    abstract fun offlineOtherTrackAreaDao(): OfflineOtherTrackAreaDao

    /** The named boxes DOC's material was downloaded for: the same list, on DOC's own card. */
    abstract fun offlineDocAreaDao(): OfflineDocAreaDao

    /** Reads and writes everything, for backup and restore. */
    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "trail_day.db"

        @Volatile
        private var instance: TrailDayDatabase? = null

        /**
         * One database for the whole app; tests build their own in memory instead.
         *
         * The migrations are named here rather than left to be discovered: an install from before the DOC
         * cache was keyed by source has data in the old shape, and an install from before it kept the
         * pictures has no table for them. Room refuses to open a database it has no path to - which would
         * be the app failing to start rather than a cache being rebuilt.
         */
        fun get(context: Context): TrailDayDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrailDayDatabase::class.java,
                    NAME
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10
                    )
                    .build().also { instance = it }
            }
    }
}
