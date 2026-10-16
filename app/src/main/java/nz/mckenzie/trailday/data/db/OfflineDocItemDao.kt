package nz.mckenzie.trailday.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** DOC's material kept for offline use: read by the browser, written by a download. */
@Dao
abstract class OfflineDocItemDao {

    @Query("SELECT * FROM offline_doc_items WHERE source = :source ORDER BY name COLLATE NOCASE")
    abstract suspend fun getAll(source: String): List<OfflineDocItemEntity>

    /** A live count, for the offline screen to say what is stored. Every source together. */
    @Query("SELECT COUNT(*) FROM offline_doc_items")
    abstract fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM offline_doc_items")
    abstract suspend fun count(): Int

    /** By source and service key, so something downloaded again is updated rather than doubled. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<OfflineDocItemEntity>)

    @Query("DELETE FROM offline_doc_items")
    abstract suspend fun deleteAll()
}
