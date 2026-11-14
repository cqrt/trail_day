package nz.mckenzie.trailday.map

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 *
 * **One request per tile, however many ask at once.** The prefetch and a Settings screen opened while
 * it is still running ask for the same four pictures, and without this gate the screen started a
 * second request for every tile the prefetch had not reached yet - so the pictures arrived after a
 * round trip of their own rather than the moment the one already in flight landed. The second caller
 * waits on the first and takes its answer.
 */
object BasemapPreviewCache {

    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    /**
     * One gate per basemap, so a fetch already on its way is waited on rather than repeated.
     *
     * Four entries for the life of the process: a gate is never removed, because keeping it is what
     * makes a *later* pair of callers share a retry after a fetch that failed with no reception.
     */
    private val gates = ConcurrentHashMap<String, Mutex>()

    /** The tile for [basemap], fetched once and remembered. */
    suspend fun load(basemap: Basemap): ImageBitmap? {
        cache[cacheKey(basemap)]?.let { return it }
        return gates.getOrPut(cacheKey(basemap)) { Mutex() }.withLock {
            // Asked again inside the gate: the caller this one waited behind may have just filled it.
            cache[cacheKey(basemap)]?.let { return@withLock it }
            val picture = fetch(basemap) ?: return@withLock null
            cache[cacheKey(basemap)] = picture
            picture
        }
    }

    /**
     * Fetches every basemap's tile, so the picker has nothing left to wait for.
     *
     * **All at once rather than one after another**: four small tiles in parallel are ready in about
     * the time of the slowest, where four in a queue are ready in the time of all four added up - and
     * this runs while the walker is looking at the map, so the shorter it is the less of it they can
     * walk into by opening Settings.
     */
    suspend fun loadAll() = coroutineScope {
        Basemap.entries.map { basemap -> async { load(basemap) } }.awaitAll()
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
