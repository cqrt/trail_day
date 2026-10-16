package nz.mckenzie.trailday.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AssetEntity::class,
        AssetPointEntity::class,
        ProductEntity::class,
        SprayEventEntity::class,
        SprayEventProductEntity::class,
        AssetProductDefaultEntity::class,
        RecordedSessionEntity::class,
        RecordedPointEntity::class,
        RecordedBreakEntity::class,
        OfflineAreaEntity::class,
        OfflineDocTrackEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class TrailDayDatabase : RoomDatabase() {

    abstract fun assetDao(): AssetDao
    abstract fun productDao(): ProductDao
    abstract fun sprayEventDao(): SprayEventDao
    abstract fun recordingDao(): RecordingDao
    abstract fun offlineAreaDao(): OfflineAreaDao

    /** The DOC tracks kept for offline use. */
    abstract fun offlineDocTrackDao(): OfflineDocTrackDao

    /** Reads and writes everything, for backup and restore. */
    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "trail_day.db"

        @Volatile
        private var instance: TrailDayDatabase? = null

        /** One database for the whole app; tests build their own in memory instead. */
        fun get(context: Context): TrailDayDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrailDayDatabase::class.java,
                    NAME
                )
                    .build().also { instance = it }
            }
    }
}
