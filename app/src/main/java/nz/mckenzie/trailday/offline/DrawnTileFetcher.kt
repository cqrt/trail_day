package nz.mckenzie.trailday.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.mckenzie.trailday.BuildConfig
import nz.mckenzie.trailday.domain.tiles.Basemap
import java.net.HttpURLConnection
import java.net.URL

/**
 * What the app calls itself when it asks a map service for a tile.
 *
 * OpenStreetMap's usage policy requires a `User-Agent` that identifies the application, and says
 * in as many words not to rely on a library's default - which is what a map library would send.
 * A name and a URL are enough: the point is that whoever runs the service can tell who is asking
 * and can get in touch.
 */
internal fun sprayDayUserAgent(appVersion: String = BuildConfig.VERSION_NAME): String =
    "TrailDay/$appVersion (+https://github.com/cqrt/trail_day)"

/**
 * Fetches the tiles of a drawn basemap - OpenStreetMap's standard layer and the topographic map.
 *
 * Two rules from their tile usage policy are built into this being the app's own fetcher at all,
 * rather than the map library talking to them directly:
 *
 *  - **A real user agent is sent** ([sprayDayUserAgent]); a library's default is what their policy
 *    names as not enough.
 Nothing in this app walks a
 *    bounding box or a zoom range over these tiles: offline areas are imagery only, and every drawn
 *    basemap is marked as not prefetchable so that the rule is in the data rather than in someone's
 *    memory. Bulk downloading and offline packs are prohibited by both services' policies, and anyone
 *    who wants offline tiles from this data is pointed at self-hosted ones instead.
 *
 * Status codes are mapped the way the LINZ fetcher maps them, with the two answers that mean
 * something different here: `429` (and OpenStreetMap's older `418`) are their rate limiter and
 * block, and both are worth naming rather than reporting as a generic failure.
 */
class DrawnTileFetcher(
    private val appVersion: String = BuildConfig.VERSION_NAME,
    /** Overridden in tests, so the mapping can be asserted without the live service. */
    private val urlFor: (zoom: Int, x: Int, y: Int) -> String =
        { zoom, x, y -> Basemap.OPENSTREETMAP.tileUrl(zoom, x, y) },
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 20_000
) : TileFetcher {

    override suspend fun fetch(zoom: Int, x: Int, y: Int): TileFetcher.Result =
        withContext(Dispatchers.IO) {
            val url = URL(urlFor(zoom, x, y))

            val result = runCatching {
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    setRequestProperty("User-Agent", sprayDayUserAgent(appVersion))
                    setRequestProperty("Accept", "image/png,image/*")
                }
                try {
                    when (val code = connection.responseCode) {
                        HttpURLConnection.HTTP_OK -> {
                            val bytes = connection.inputStream.use { it.readBytes() }
                            if (bytes.isEmpty()) {
                                TileFetcher.Result.Failed("empty response")
                            } else {
                                TileFetcher.Result.Tile(bytes)
                            }
                        }

                        HttpURLConnection.HTTP_NOT_FOUND -> TileFetcher.Result.NotFound

                        TOO_MANY_REQUESTS, IM_A_TEAPOT ->
                            TileFetcher.Result.Failed("OpenStreetMap is rate limiting (HTTP $code)")

                        HttpURLConnection.HTTP_FORBIDDEN ->
                            TileFetcher.Result.Failed("OpenStreetMap refused the request (HTTP 403)")

                        else -> TileFetcher.Result.Failed("HTTP $code")
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { error ->
                TileFetcher.Result.Failed(error.message ?: error::class.java.simpleName)
            }

            // **A drawn map says when it stops answering, the same way a LINZ one does.** Until this
            // was here, panning off the end of what is cached with no reception gave a blank square and
            // no explanation at all: only the LINZ fetcher counted its failures, so nothing ever
            // reached the notice. Their tiles arrive in a screenful at a time, so one arriving puts the
            // run back to zero - see [TileTrouble].
            when (result) {
                is TileFetcher.Result.Tile, TileFetcher.Result.NotFound -> TileTrouble.arrived()
                is TileFetcher.Result.Failed -> TileTrouble.failed()
            }

            result
        }

    private companion object {
        const val TOO_MANY_REQUESTS = 429

        /** What their servers answered before 429 existed, and still do for a blocked client. */
        const val IM_A_TEAPOT = 418
    }
}
