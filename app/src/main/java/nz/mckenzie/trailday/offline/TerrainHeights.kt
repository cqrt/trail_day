package nz.mckenzie.trailday.offline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.TerrainRgb
import nz.mckenzie.trailday.domain.tiles.Basemap

/**
 * A height for each point of a line, read from LINZ's height tiles.
 *
 * **The tiles are the ones the drawn map is already shaded from**, so this adds no new service and no
 * new key: they go through the app's own tile server when it is up, which keeps them beside the
 * Topographic map a walker may have downloaded, and through the proxy when it is not. A place with no
 * tile - offline, and that ground never looked at - comes back as no height rather than as a zero.
 *
 * The decode is [TerrainRgb]'s, which is plain Kotlin and tested; what is here is the fetching and the
 * reading of pixels, which cannot be tested without a picture.
 */
class TerrainHeights {

    private val fetcher = LinzTileFetcher(
        urlFor = { zoom, x, y ->
            // The app's own server when it is up, so the tile is stored where the map keeps its shade;
            // the proxy directly when it is not, which is the map's own fallback too.
            TileServerHolder.elevationTemplateUrl()
                ?.replace("{z}", zoom.toString())
                ?.replace("{x}", x.toString())
                ?.replace("{y}", y.toString())
                ?: Basemap.elevationUrl(zoom, x, y)
        },
        // The DEM endpoint answers PNG, and asking for anything else gets bytes that decode to nothing.
        accept = "image/png"
    )

    /** The tiles read so far, so a line that crosses one tile a hundred times asks for it once. */
    private val tiles = HashMap<Long, Bitmap?>()

    /**
     * One height per point, in metres, or null where the ground could not be read.
     *
     * Read on the IO dispatcher and one tile at a time: this runs while a walker is tapping, and a
     * journey of fifty kilometres is forty-odd tiles, not four hundred requests.
     */
    suspend fun along(points: List<GeoPoint>): List<Double?> = withContext(Dispatchers.IO) {
        val zoom = TerrainRgb.ZOOM
        points.map { point ->
            val pixel = TerrainRgb.pixelFor(point, zoom)
            val tile = tileAt(zoom, pixel.x, pixel.y) ?: return@map null
            val colour = runCatching { tile.getPixel(pixel.px, pixel.py) }.getOrNull() ?: return@map null
            TerrainRgb.metres(
                red = (colour shr 16) and 0xFF,
                green = (colour shr 8) and 0xFF,
                blue = colour and 0xFF
            )
        }
    }

    private suspend fun tileAt(zoom: Int, x: Int, y: Int): Bitmap? {
        val key = (zoom.toLong() shl 40) or (x.toLong() shl 20) or y.toLong()
        if (tiles.containsKey(key)) return tiles[key]
        val bytes = (fetcher.fetch(zoom, x, y) as? TileFetcher.Result.Tile)?.bytes
        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        tiles[key] = bitmap
        return bitmap
    }

    fun forget() = tiles.clear()
}
