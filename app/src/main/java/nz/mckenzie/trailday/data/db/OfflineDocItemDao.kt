package nz.mckenzie.trailday.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** How many things one of DOC's services has on the phone, as the offline screen counts them. */
data class OfflineDocCount(val source: String, val count: Int)

/** The DOC material kept for offline use: read by the browser, written by a download. */
@Dao
abstract class OfflineDocItemDao {

    @Query("SELECT * FROM offline_doc_items WHERE source = :source ORDER BY name COLLATE NOCASE")
    abstract suspend fun getAll(source: String): List<OfflineDocItemEntity>

    /** A live count, for the offline screen to say what is stored. Every source together. */
    @Query("SELECT COUNT(*) FROM offline_doc_items")
    abstract fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM offline_doc_items")
    abstract suspend fun count(): Int

    /**
     * How many are held of each service, by the source's own id.
     *
     * What the offline screen lists: "412 tracks, 38 huts" says far more about what is on the phone than
     * "543 things", and it is the shape a walker checks their area against.
     */
    @Query("SELECT source, COUNT(*) AS count FROM offline_doc_items GROUP BY source")
    abstract suspend fun counts(): List<OfflineDocCount>

    /** By source and service key, so something downloaded again is updated rather than doubled. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<OfflineDocItemEntity>)

    @Query("DELETE FROM offline_doc_items")
    abstract suspend fun deleteAll()
}
