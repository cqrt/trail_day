package nz.mckenzie.trailday.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nz.mckenzie.trailday.domain.tiles.LinzAccess
import java.net.HttpURLConnection
import java.net.URL

/**
 * What the app's own LINZ proxy says about itself.
 *
 * The phone cannot tell "the proxy is down" from "LINZ refused the key" - all it sees is tiles that do
 * not arrive - so it asks the one thing that does know. The Worker's `/health` makes a single call to
 * LINZ with the key and answers with one sentence about it, and this is the app's side of that: the
 * sentence, repeated as it was given rather than guessed at here.
 *
 * See `worker-linz/README.md` for why that probe asks for a **style document** rather than a tile: a
 * tile can come back `200` for a dead key, so a tile cannot answer the question at all.
 */
sealed interface ProxyHealth {

    /** The proxy answered. [message] is its own sentence about the key, in its own words. */
    data class Answered(val message: String) : ProxyHealth

    /**
     * The proxy did not answer: no network, or the Worker is not deployed, or the account is over its
     * daily request allowance.
     */
    data object Unreachable : ProxyHealth
}

/**
 * Asks the Worker whether the key still works.
 *
 * Only ever called after a run of tiles has failed - see [TileTrouble] - so a map that is working
 * never makes the call, and a walker with no reception never makes it either.
 */
class ProxyHealthCheck(
    /** Overridden in tests: the live Worker is not part of a unit test. */
    private val urlFor: () -> String = { "${LinzAccess.PROXY}/health" },
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 5_000
) {

    suspend fun check(): ProxyHealth = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(urlFor()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = connection.responseCode
                // A 503 is still an *answer*: the Worker says "no key configured" that way, and that is
                // the most useful thing it could possibly say. So the body is read either way.
                val stream = if (code >= 400) connection.errorStream else connection.inputStream
                val body = stream?.use { it.readBytes().decodeToString() }.orEmpty()

                val message = proxyMessage(body)
                if (message == null) ProxyHealth.Unreachable else ProxyHealth.Answered(message)
            } finally {
                connection.disconnect()
            }
        }.getOrElse { ProxyHealth.Unreachable }
    }
}

/**
 * The sentence inside the Worker's answer, or null when this is not an answer it understands.
 *
 * Pure, and separated out so it can be held by a test with the real bodies in it - including the ones
 * that are *not* JSON. A Worker that is over its daily request allowance is answered by Cloudflare
 * rather than by the Worker, with an HTML error page, so "not JSON" is an ordinary thing to receive
 * here rather than a bug.
 */
internal fun proxyMessage(body: String): String? = runCatching {
    val element = Json.parseToJsonElement(body).jsonObject["message"]
    element?.jsonPrimitive?.contentOrNull
}.getOrNull()?.takeIf { it.isNotBlank() }
