package nz.mckenzie.trailday.other

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.domain.other.OtherTracksJson
import nz.mckenzie.trailday.domain.other.OtherTracksQuery
import nz.mckenzie.trailday.domain.other.OtherTracksUrl

/** What one question to the national tracks dataset answered. */
sealed interface OtherTracksReadResult {

    data class Found(val tracks: List<OtherTrack>) : OtherTracksReadResult

    /**
     * The dataset could not be read, in a sentence for the walker.
     *
     * A refusal or an unreachable service is not an exception: reading somebody else's tracks is a
     * deliberate act and its failure should read like one.
     */
    data class Failed(val message: String) : OtherTracksReadResult
}

/** How many rows the dataset holds for one question. */
sealed interface OtherTracksCountResult {

    data class Done(val count: Int) : OtherTracksCountResult

    data class Failed(val message: String) : OtherTracksCountResult
}

/**
 * The national tracks dataset, asked over the network.
 *
 * An interface so that a screen and a download are tested against a fake rather than against the
 * Commission's live service.
 */
interface OtherTracksReader {

    /** One page of rows. */
    suspend fun read(query: OtherTracksQuery): OtherTracksReadResult

    /** How many rows match, without reading them. */
    suspend fun count(query: OtherTracksQuery): OtherTracksCountResult
}

/**
 * The live service, asked over HTTP.
 *
 * **No key and no proxy.** Unlike DOC's Tracks API, this dataset is public ArcGIS open data, so the
 * phone asks it directly - which is also how the app reads DOC's own ArcGIS layers, and the reason the
 * DOC Worker is not involved at all here.
 */
class ArcGisOtherTracks(
    /** Overridden in tests: the live service is not part of a unit test. */
    private val urlFor: (OtherTracksQuery) -> String = OtherTracksUrl::of,
    /** Overridden in tests, as [urlFor] is: a count asks the same service a smaller question. */
    private val countUrlFor: (OtherTracksQuery) -> String = OtherTracksUrl::countOf,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000
) : OtherTracksReader {

    override suspend fun read(query: OtherTracksQuery): OtherTracksReadResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = connectionTo(urlFor(query))
                try {
                    val status = connection.responseCode
                    if (status != HttpURLConnection.HTTP_OK) {
                        return@runCatching OtherTracksReadResult.Failed(
                            "The tracks service answered HTTP $status. Try again later."
                        )
                    }
                    val body = connection.inputStream.use { it.readBytes().decodeToString() }
                    OtherTracksReadResult.Found(OtherTracksJson.parse(body))
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { error ->
                // No network, DNS, TLS, or an answer that would not read: say so rather than crashing.
                OtherTracksReadResult.Failed(error.message ?: error::class.java.simpleName)
            }
        }

    override suspend fun count(query: OtherTracksQuery): OtherTracksCountResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = connectionTo(countUrlFor(query))
                try {
                    val status = connection.responseCode
                    if (status != HttpURLConnection.HTTP_OK) {
                        return@runCatching OtherTracksCountResult.Failed(
                            "The tracks service answered HTTP $status."
                        )
                    }
                    val body = connection.inputStream.use { it.readBytes().decodeToString() }
                    when (val counted = OtherTracksJson.parseCount(body)) {
                        null -> OtherTracksCountResult.Failed("The tracks service's answer was not a count.")
                        else -> OtherTracksCountResult.Done(counted)
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { error ->
                OtherTracksCountResult.Failed(error.message ?: error::class.java.simpleName)
            }
        }

    private fun connectionTo(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", USER_AGENT)
    }

    companion object {
        /** The same agent the app sends DOC. */
        const val USER_AGENT = "TrailDay-Android"
    }
}
