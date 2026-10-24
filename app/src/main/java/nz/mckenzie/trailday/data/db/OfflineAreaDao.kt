package nz.mckenzie.trailday.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class OfflineAreaDao {

    /**
     * One pack's areas, newest first.
     *
     * Every read is by pack: an imagery area and an area of the drawn map are two different lists on two
     * different cards, and a query that returned both would be one screen showing the other's rows.
     */
    @Query("SELECT * FROM offline_areas WHERE pack = :pack ORDER BY createdAtEpochMs DESC")
    abstract fun observePack(pack: String): Flow<List<OfflineAreaEntity>>

    @Query("SELECT * FROM offline_areas WHERE pack = :pack ORDER BY createdAtEpochMs DESC")
    abstract suspend fun byPack(pack: String): List<OfflineAreaEntity>

    @Query("SELECT * FROM offline_areas WHERE id = :id")
    abstract suspend fun getById(id: Long): OfflineAreaEntity?

    @Insert
    abstract suspend fun insert(area: OfflineAreaEntity): Long

    /** Progress is written as tiles land, so a killed download is resumable. */
    @Query("UPDATE offline_areas SET downloadedTiles = :downloaded, bytes = :bytes WHERE id = :id")
    abstract suspend fun updateProgress(id: Long, downloaded: Long, bytes: Long)

    @Query(
        """
        UPDATE offline_areas
        SET completedAtEpochMs = :completedAtEpochMs,
            downloadedTiles = :downloaded,
            bytes = :bytes,
            lastError = NULL
        WHERE id = :id
        """
    )
    abstract suspend fun markComplete(
        id: Long,
        completedAtEpochMs: Long,
        downloaded: Long,
        bytes: Long
    )

    @Query("UPDATE offline_areas SET lastError = :message WHERE id = :id")
    abstract suspend fun recordError(id: Long, message: String)

    @Query("DELETE FROM offline_areas WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /** Used when a pack's tiles are cleared: its records describe nothing then. */
    @Query("DELETE FROM offline_areas WHERE pack = :pack")
    abstract suspend fun deletePack(pack: String)
}
