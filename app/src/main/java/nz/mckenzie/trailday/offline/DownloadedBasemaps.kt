package nz.mckenzie.trailday.offline

import nz.mckenzie.trailday.data.db.OfflineAreaDao
import nz.mckenzie.trailday.data.db.OfflineAreaEntity
import nz.mckenzie.trailday.domain.tiles.Basemap
import kotlin.math.floor

/**
 * What is downloaded that would draw where the map is looking.
 *
 * With no connection the map stops drawing the moment the walker pans past the edge of the tiles
 * on the phone, and the app knows two things worth putting together: **where the map is looking,
 * and which boxes of tiles have been downloaded.** When one of those boxes holds a different
 * basemap than the one being drawn, the map has an answer rather than an apology - "use aerial
 * imagery", one tap, and the ground appears.
 *
 * **Only a finished area counts.** A row exists from the moment a download starts and its tiles
 * land one at a time, so an area still in flight is a promise about what will be on the phone
 * rather than what is. Offering it would swap one blank map for another.
 */
class DownloadedBasemaps(private val dao: OfflineAreaDao) {

    /**
     * The basemap a finished download covers this spot with, or null when none does.
     *
     * [except] is the basemap the walker is already drawing: offering it back would be the notice
     * suggesting the map they are looking at.
     *
     * Imagery first, then the Topographic map, and the order is deliberate: imagery is the map the
     * app exists for and the one an area is usually downloaded for, so it is the better answer when
     * a walker has kept both.
     */
    suspend fun covering(lat: Double, lng: Double, zoom: Double, except: Basemap): Basemap? {
        for ((pack, basemap) in PACKS) {
            if (basemap == except) continue
            if (dao.byPack(pack.id).any { it.covers(lat, lng, zoom) }) return basemap
        }
        return null
    }

    private companion object {
        /** Which pack draws which basemap - the one place the two tables are joined. */
        val PACKS = listOf(
            OfflinePack.IMAGERY to Basemap.LINZ_AERIAL,
            OfflinePack.TOPOGRAPHY to Basemap.LINZ_TOPO_LIDAR_HILLSHADE
        )
    }
}

/**
 * Whether this downloaded area has tiles for the spot the map is looking at.
 *
 * Three questions, each of which the map would otherwise answer by drawing nothing: **is the
 * point inside the box**, **was the download finished**, and **are the tiles at a level the map is
 * asking for**. The last one has one allowance: a map zoomed deeper than the box's deepest level
 * is drawn from those deep tiles by the renderer's own overzoom, so only the levels above the top
 * of the range are missing.
 */
internal fun OfflineAreaEntity.covers(lat: Double, lng: Double, zoom: Double): Boolean =
    isComplete &&
        lat in minLat..maxLat &&
        lng in minLng..maxLng &&
        floor(zoom).toInt() >= minZoom
