package nz.mckenzie.trailday.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Extremes of every planned asset's geometry. Null columns when there is no geometry
 * at all, which is how "nothing planned yet" is told apart from "places at 0,0".
 */
data class AssetPointBounds(
    val minLat: Double?,
    val minLng: Double?,
    val maxLat: Double?,
    val maxLng: Double?
)

/**
 * Declared as an abstract class rather than an interface so that
 * [replaceGeometry] can be a real transactional method - Kotlin interface
 * default methods need extra compiler flags to work reliably with Room.
 */
@Dao
abstract class AssetDao {
    @Query("SELECT * FROM assets WHERE active = 1 ORDER BY name COLLATE NOCASE")
    abstract fun observeActiveAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets ORDER BY name COLLATE NOCASE")
    abstract fun observeAllAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :id")
    abstract fun observeAsset(id: Long): Flow<AssetEntity?>

    @Query("SELECT * FROM assets WHERE id = :id")
    abstract suspend fun getAsset(id: Long): AssetEntity?

    /**
     * Every source reference any asset carries, for telling a service track already imported from one
     * that has not. Nulls are left out: nothing drawn or imported from a file has a source.
     */
    @Query("SELECT sourceRef FROM assets WHERE sourceRef IS NOT NULL")
    abstract suspend fun sourceRefs(): List<String>

    /**
     * How many places in the walker's own list came from one of DOC's services.
     *
     * **Matched on the shape of the reference rather than on one form of it**: an install that imported
     * a DOC track before the sources were namespaced wrote `doc:35239046`, and one that imported it
     * since wrote `doc-tracks:35239046`. Both are DOC's, and both are what the one-time tidy takes out.
     * Nothing else ever writes a reference, so nothing else is caught by this.
     */
    @Query("SELECT COUNT(*) FROM assets WHERE sourceRef LIKE 'doc-%' OR sourceRef LIKE 'doc:%'")
    abstract suspend fun countFromDoc(): Int

    /** Takes them out, returning how many went. Their points go with them, by the foreign key. */
    @Query("DELETE FROM assets WHERE sourceRef LIKE 'doc-%' OR sourceRef LIKE 'doc:%'")
    abstract suspend fun deleteFromDoc(): Int

    @Insert
    abstract suspend fun insert(asset: AssetEntity): Long

    @Update
    abstract suspend fun update(asset: AssetEntity)

    @Query("DELETE FROM assets WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /** The photograph attached to a place, when one is watched by the place's own page. */
    @Query("SELECT jpeg FROM asset_photos WHERE assetId = :assetId")
    abstract fun observePhoto(assetId: Long): Flow<ByteArray?>

    @Query("SELECT jpeg FROM asset_photos WHERE assetId = :assetId")
    abstract suspend fun photo(assetId: Long): ByteArray?

    @Upsert
    abstract suspend fun upsertPhoto(photo: AssetPhotoEntity)

    @Query("DELETE FROM asset_photos WHERE assetId = :assetId")
    abstract suspend fun deletePhoto(assetId: Long)

    @Query("UPDATE assets SET lengthM = :lengthM WHERE id = :assetId")
    abstract suspend fun updateMeasures(assetId: Long, lengthM: Double)

    @Query("SELECT * FROM asset_points WHERE assetId = :assetId ORDER BY pathIndex, sequence")
    abstract suspend fun getGeometry(assetId: Long): List<AssetPointEntity>

    /**
     * Every vertex of every asset, in one query, grouped by the caller.
     *
     * For the two documents that are built for the whole map at once - the GeoJSON the map draws and
     * the state document, which needs each asset's points to say which version of the line a desk was
     * handed. One query rather than one per asset: the desk asks for both documents on opening, and a
     * per-asset read would be a query per track for points the map is about to ask for anyway.
     *
     * Ordered by path as well as by position, so the caller can group by asset and split by path
     * without sorting anything itself.
     */
    @Query("SELECT * FROM asset_points ORDER BY assetId, pathIndex, sequence")
    abstract suspend fun allGeometry(): List<AssetPointEntity>

    /**
     * The box containing every planned asset, for answering "where is the work?" with
     * one query rather than loading every point.
     */
    @Query(
        "SELECT MIN(lat) AS minLat, MIN(lng) AS minLng, " +
            "MAX(lat) AS maxLat, MAX(lng) AS maxLng FROM asset_points"
    )
    abstract suspend fun pointBounds(): AssetPointBounds?

    @Query("SELECT * FROM asset_points WHERE assetId = :assetId ORDER BY pathIndex, sequence")
    abstract fun observeGeometry(assetId: Long): Flow<List<AssetPointEntity>>

    @Insert
    abstract suspend fun insertGeometry(points: List<AssetPointEntity>)

    @Query("DELETE FROM asset_points WHERE assetId = :assetId")
    abstract suspend fun deleteGeometry(assetId: Long)

    /**
     * Swaps a place's geometry and refreshes its cached length atomically.
     *
     * The number is written with the vertices it comes from, in one transaction, so a list can
     * never read a length that belongs to the previous line.
     */
    @androidx.room.Transaction
    open suspend fun replaceGeometry(
        assetId: Long,
        points: List<AssetPointEntity>,
        lengthM: Double
    ) {
        deleteGeometry(assetId)
        if (points.isNotEmpty()) insertGeometry(points)
        updateMeasures(assetId, lengthM)
    }
}
