package nz.mckenzie.trailday.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nz.mckenzie.trailday.domain.tiles.Basemap
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches aerial tiles from LINZ.
 *
 * Status codes are mapped deliberately rather than collapsed into "failed":
 *
 *  - `200` -> the tile
 *  - `404` -> [TileFetcher.Result.NotFound]; the source has no imagery for some tiles
 *    (the edge of coverage, offshore), and those are not errors to retry
 *  - `400` -> a bad or expired key at the proxy, surfaced as a failure with that wording
 *  - `429` -> rate limited; the downloader's backoff and pacing are the answer,
 *    so it is worth saying so rather than reporting a generic failure
 *
 * The app identifies itself in the usual way ([sprayDayUserAgent]). Neither LINZ nor the app's own
 * proxy requires it; being a well-behaved client of someone else's service does.
 */
class LinzTileFetcher(
    /**
     * Where to fetch from. Overridden in tests so the status-code mapping can be
     * asserted against the real local tile server rather than the live service.
     */
    private val urlFor: (zoom: Int, x: Int, y: Int) -> String =
        { zoom, x, y -> Basemap.LINZ_AERIAL.tileUrl(zoom, x, y) },
    /** What the caller will accept: a picture, or the vector tiles of the Topographic map. */
    private val accept: String = "image/webp,image/*",
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 20_000
) : TileFetcher {

    override suspend fun fetch(zoom: Int, x: Int, y: Int): TileFetcher.Result =
        withContext(Dispatchers.IO) {
            // **A tile is asked for again before it is called a failure.** A walker reported the "this map is
            // not drawing" notice arriving while their key was fine and they were online - and one dropped
            // connection during a pan is enough to fail a screenful, which is the run that raises the
            // notice. Three attempts a few hundred milliseconds apart turn that blip into a slower tile; a
            // source that is genuinely not answering still fails all three.
            var result = attempt(zoom, x, y)
            var tries = 1
            while (tries < ATTEMPTS && worthRetrying(result)) {
                delay(RETRY_PAUSE_MS * tries)
                result = attempt(zoom, x, y)
                tries += 1
            }

            // Every LINZ tile this app fetches comes through here - the map's own, the place names', the
            // heights, and a download's - so this is where "nothing is coming" can be told from "one
            // tile did not". See [TileTrouble].
            when (result) {
                is TileFetcher.Result.Tile, TileFetcher.Result.NotFound -> TileTrouble.arrived()
                is TileFetcher.Result.Failed -> TileTrouble.failed()
            }

            result
        }

    /** One request for one tile, with no retrying: see [fetch]. */
    private fun attempt(zoom: Int, x: Int, y: Int): TileFetcher.Result {
        val url = URL(urlFor(zoom, x, y))

        return runCatching {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", sprayDayUserAgent())
                setRequestProperty("Accept", accept)
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

                    HttpURLConnection.HTTP_BAD_REQUEST ->
                        TileFetcher.Result.Failed(KEY_REFUSED)

                    TOO_MANY_REQUESTS ->
                        TileFetcher.Result.Failed("rate limited by LINZ (HTTP 429)")

                    else -> TileFetcher.Result.Failed("HTTP $code")
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { error ->
            TileFetcher.Result.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    private companion object {
        const val TOO_MANY_REQUESTS = 429

        /** How many times one tile is asked for. */
        const val ATTEMPTS = 3

        /** The pause before the second attempt, doubled before the third: a moment for a blip to pass. */
        const val RETRY_PAUSE_MS = 250L
    }
}

/** What a tile said when the key itself was refused. */
private const val KEY_REFUSED = "LINZ rejected the key (HTTP 400): it may have expired"

/**
 * Whether asking for the same tile again could give a different answer.
 *
 * **A key LINZ has rejected reads the same three times** - the request is right and the key is not - so the
 * retries are not spent on it, and the notice is reached the sooner for it. Everything else that fails is a
 * dropped connection, a timeout, an empty body or a server having a moment, which is exactly what a second
 * request is for.
 */
internal fun worthRetrying(result: TileFetcher.Result): Boolean = when (result) {
    is TileFetcher.Result.Failed -> result.message != KEY_REFUSED
    else -> false
}
