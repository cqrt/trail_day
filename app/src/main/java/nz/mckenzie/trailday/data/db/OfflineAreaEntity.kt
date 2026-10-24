package nz.mckenzie.trailday.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import nz.mckenzie.trailday.offline.OfflinePack

/**
 * A downloaded offline area: **the record of one named box of tiles, whichever pack it belongs to.**
 *
 * The tiles themselves live on the filesystem (see the offline tile store); this
 * row is the *record* of what was downloaded, so the UI can list areas, show
 * progress and delete them. Progress columns exist so an interrupted download can
 * be resumed honestly rather than restarted.
 *
 * [pack] is why there is one table rather than two: an imagery area and an area of the drawn Topographic
 * map are the same thing to a walker - a named box they chose, with a size and a state - and the only
 * difference is which tiles they are made of. See [OfflinePack].
 */
@Entity(
    tableName = "offline_areas",
    indices = [Index("createdAtEpochMs"), Index("pack")]
)
data class OfflineAreaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** Which pack's tiles this area is: [OfflinePack.id], never renamed. */
    val pack: String = OfflinePack.IMAGERY.id,
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
    val minZoom: Int,
    val maxZoom: Int,
    /** Tiles the plan says this area needs. */
    val plannedTiles: Long,
    /** Tiles actually on disk. */
    val downloadedTiles: Long = 0L,
    val bytes: Long = 0L,
    val createdAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
    /** Why the last attempt stopped early, if it did. */
    val lastError: String? = null
) {
    val isComplete: Boolean get() = completedAtEpochMs != null

    val percent: Int
        get() = when {
            plannedTiles <= 0L -> 0
            isComplete -> 100
            else -> ((downloadedTiles * 100) / plannedTiles).toInt().coerceIn(0, 99)
        }
}
