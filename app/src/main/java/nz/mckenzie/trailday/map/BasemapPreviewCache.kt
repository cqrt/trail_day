package nz.mckenzie.trailday.map

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.offline.sprayDayUserAgent
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * The picker's example tiles, kept in memory for the life of the process.
 *
 * A preview is one small tile of a fixed place, so it costs one request per basemap and then never
 * changes: fetching it again every time Settings opens is what made the picker take seconds. The
 * application prefetches all of them at launch, so by the time the walker looks the pictures are
 * already here.
 */
object BasemapPreviewCache {

    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    /** The tile for [basemap], fetched once and remembered. */
    suspend fun load(basemap: Basemap): ImageBitmap? {
        cache[cacheKey(basemap)]?.let { return it }
        val picture = fetch(basemap) ?: return null
        cache[cacheKey(basemap)] = picture
        return picture
    }

    /** Fetches every basemap's tile, so the picker has nothing left to wait for. */
    suspend fun loadAll() {
        Basemap.entries.forEach { basemap -> load(basemap) }
    }

    /**
     * Forgets every picture, so the next look fetches them again.
     *
     * For the one thing that changes what a picture *is*: a key of the walker's own sends a tile to a
     * different host, and a picture fetched from the other one would be a picture of the wrong thing to
     * have beside the name. See [nz.mckenzie.trailday.domain.tiles.LinzAccess].
     */
    fun forget() {
        cache.clear()
    }

    private fun cacheKey(basemap: Basemap): String = basemap.id

    private suspend fun fetch(basemap: Basemap): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val (x, y) = tile(PREVIEW_LAT, PREVIEW_LNG, PREVIEW_ZOOM)
            val url = basemap.previewTileUrl(PREVIEW_ZOOM, x, y)
            val connection = runCatching {
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 12_000
                    setRequestProperty("User-Agent", sprayDayUserAgent())
                }
            }.getOrNull() ?: return@withContext null

            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val bytes = connection.inputStream.use { it.readBytes() }
                runCatching {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            } catch (_: Exception) {
                null
            } finally {
                connection.disconnect()
            }
        }

    /** The XYZ tile holding [lat]/[lng] at [zoom], in the usual Web Mercator scheme. */
    private fun tile(lat: Double, lng: Double, zoom: Int): Pair<Int, Int> {
        val side = 1 shl zoom
        val x = ((lng + 180.0) / 360.0 * side).toInt().coerceIn(0, side - 1)
        val latitude = Math.toRadians(lat)
        val y = ((1.0 - ln(tan(latitude) + 1.0 / cos(latitude)) / Math.PI) / 2.0 * side)
            .toInt()
            .coerceIn(0, side - 1)
        return x to y
    }

    /** A piece of ground every basemap shows the same way, so the pictures can be compared. */
    private const val PREVIEW_LAT = -45.9395714
    private const val PREVIEW_LNG = 168.3909702
    private const val PREVIEW_ZOOM = 12
}
