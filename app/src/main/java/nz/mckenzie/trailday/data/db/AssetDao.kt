package nz.mckenzie.trailday.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Extremes of every planned asset's geometry. Null columns when there is no geometry
 * at all, which is how "nothing planned yet" is told apart from "assets at 0,0".
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

    @Insert
    abstract suspend fun insert(asset: AssetEntity): Long

    @Update
    abstract suspend fun update(asset: AssetEntity)

    @Query("DELETE FROM assets WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query(
        """
        UPDATE assets
        SET lastSprayedAtEpochMs = MAX(COALESCE(lastSprayedAtEpochMs, 0), :sprayedAtEpochMs)
        WHERE id = :assetId
        """
    )
    abstract suspend fun setLastSprayedAt(assetId: Long, sprayedAtEpochMs: Long)

    /**
     * Moves the stored last-sprayed date wherever the record now says it belongs - backwards,
     * or to nothing at all.
     *
     * [setLastSprayedAt] only ever moves it forwards, which is right when a spray is being
     * added and wrong when one is being taken away. A deleted spray has to put the date back
     * to the spray before it, and with no sprays left the date goes back to null, which is
     * what "never sprayed" means - so the line reads red again instead of keeping the colour
     * of a spray that is no longer on the device.
     */
    @Query("UPDATE assets SET lastSprayedAtEpochMs = :sprayedAtEpochMs WHERE id = :assetId")
    abstract suspend fun setLastSprayedAtExactly(assetId: Long, sprayedAtEpochMs: Long?)

    @Query("UPDATE assets SET lengthM = :lengthM, areaM2 = :areaM2 WHERE id = :assetId")
    abstract suspend fun updateMeasures(assetId: Long, lengthM: Double, areaM2: Double)

    @Query("SELECT * FROM asset_points WHERE assetId = :assetId ORDER BY pathIndex, sequence")
    abstract suspend fun getGeometry(assetId: Long): List<AssetPointEntity>

    /**
     * Every vertex of every asset, in one query, grouped by the caller.
     *
     * For the two documents that are built for the whole farm at once - the GeoJSON the map draws and
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
     * Swaps an asset's geometry and refreshes its cached length and ground atomically.
     *
     * Both numbers are written with the vertices they come from, in one transaction, so a list can
     * never read a length that belongs to the previous line.
     */
    @androidx.room.Transaction
    open suspend fun replaceGeometry(
        assetId: Long,
        points: List<AssetPointEntity>,
        lengthM: Double,
        areaM2: Double
    ) {
        deleteGeometry(assetId)
        if (points.isNotEmpty()) insertGeometry(points)
        updateMeasures(assetId, lengthM, areaM2)
    }
}
