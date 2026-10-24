package nz.mckenzie.trailday.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The DOC material kept for offline use, and the pictures of it.
 *
 * The pictures live here rather than with anything else because they belong to the cache: they arrive
 * with a download, are cleared with it, and are of no use without it. One DAO over one cache rather than
 * a second store to thread through every screen that reads DOC's material.
 */
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

    /** How many pictures are held, for the offline screen to say what is on the phone. */
    @Query("SELECT COUNT(*) FROM offline_doc_pictures")
    abstract suspend fun pictureCount(): Int

    /**
     * A live count of the pictures, for the offline screen's own line.
     *
     * Live rather than refreshed with the material's count, because they arrive at different moments: the
     * records are written first and the pictures follow, and re-downloading an area changes no record at
     * all - so a line that only moved when the records moved would sit at nothing while the pictures
     * landed.
     */
    @Query("SELECT COUNT(*) FROM offline_doc_pictures")
    abstract fun observePictureCount(): Flow<Int>

    /**
     * The picture at DOC's address for it, or null when it is not held.
     *
     * Read whole: a picture is drawn, not queried into, and DOC's are thumbnails.
     */
    @Query("SELECT * FROM offline_doc_pictures WHERE url = :url")
    abstract suspend fun picture(url: String): OfflineDocPictureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPicture(picture: OfflineDocPictureEntity)

    @Query("DELETE FROM offline_doc_pictures")
    abstract suspend fun deletePictures()
}
