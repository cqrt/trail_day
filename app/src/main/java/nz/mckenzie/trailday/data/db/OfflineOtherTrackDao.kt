package nz.mckenzie.trailday.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The national tracks dataset's rows kept for offline use.
 *
 * Its own DAO rather than a column on DOC's, because the two are different caches of different
 * publishers: a walker who downloaded DOC's huts for a trip has no reason to lose a council walkway
 * when they clear DOC's material, and the two are counted and cleared apart on the offline screen.
 */
@Dao
abstract class OfflineOtherTrackDao {

    @Query("SELECT * FROM offline_other_tracks ORDER BY name COLLATE NOCASE")
    abstract suspend fun getAll(): List<OfflineOtherTrackEntity>

    /** A live count, for the offline screen's own line and its Clear. */
    @Query("SELECT COUNT(*) FROM offline_other_tracks")
    abstract fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM offline_other_tracks")
    abstract suspend fun count(): Int

    /**
     * When the newest download of this source happened, or null when nothing is held.
     *
     * The licence asks for the moment the data was acquired to be stated, and this is the only place the
     * app knows it: the rows carry the moment each was read, and the newest of them is what the credit
     * says.
     */
    @Query("SELECT MAX(downloadedAtEpochMs) FROM offline_other_tracks")
    abstract suspend fun newestDownloadAtEpochMs(): Long?

    /** By the service's UUID, so a row read again is updated rather than doubled. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<OfflineOtherTrackEntity>)

    @Query("DELETE FROM offline_other_tracks")
    abstract suspend fun deleteAll()
}
