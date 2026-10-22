package nz.mckenzie.trailday.doc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.mckenzie.trailday.domain.doc.DocAlertsState
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocProxyJson
import nz.mckenzie.trailday.domain.doc.DocTrackCompanion
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Reads a DOC track's website facts and alerts through the Trail Day proxy.
 *
 * **The DOC key is not in the app.** The proxy is a small Worker that holds it; this reader speaks
 * only to that proxy, and falls back to the public website layer and alert endpoint when the proxy
 * cannot answer. That fallback is deliberate: an app with no proxy is still an app, and an app
 * whose proxy is briefly down is not an app that has lost its detail pages.
 */
class DocProxyDetails(
    private val baseUrl: String = BASE_URL,
    private val fallback: DocDetailReader = DocWebDetails(),
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 15_000
) : DocDetailReader {

    override suspend fun companionFor(track: DocItem): DocTrackCompanion? {
        val fromProxy = proxyCompanion(track)
        return fromProxy ?: fallback.companionFor(track)
    }

    override suspend fun alertsFor(item: DocItem): DocAlertsState {
        val fromProxy = proxyAlerts(item)
        return fromProxy ?: fallback.alertsFor(item)
    }

    /** The proxy's detail for [track], or null when the proxy has no match or cannot answer. */
    private suspend fun proxyCompanion(track: DocItem): DocTrackCompanion? {
        if (baseUrl.isBlank()) return null
        return withContext(Dispatchers.IO) {
            val assetId = lookup(track) ?: return@withContext null
            val body = read("$baseUrl/v1/track/$assetId/detail") ?: return@withContext null
            DocProxyJson.detail(body)
        }
    }

    /** The proxy's current alerts for [item], or null when the proxy cannot answer. */
    private suspend fun proxyAlerts(item: DocItem): DocAlertsState? {
        if (baseUrl.isBlank()) return null
        return withContext(Dispatchers.IO) {
            val assetId = lookup(item) ?: return@withContext null
            val body = read("$baseUrl/v1/track/$assetId/alerts") ?: return@withContext null
            DocAlertsState.Found(DocProxyJson.alerts(body))
        }
    }

    /** The API's own id for [item], matched by name and the middle of its line. */
    private fun lookup(item: DocItem): String? {
        val point = item.paths.flatten().let { it.getOrNull(it.size / 2) } ?: return null
        val name = URLEncoder.encode(item.name, "UTF-8")
        val url = "$baseUrl/v1/lookup?name=$name&lat=${point.lat}&lon=${point.lng}"
        val body = read(url) ?: return null
        return DocProxyJson.lookup(body)?.assetId
    }

    /** One GET to the proxy, or null when it does not answer with a body. */
    private fun read(url: String): String? {
        val connection = runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", ArcGisDoc.USER_AGENT)
            }
        }.getOrNull() ?: return null
        return try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    connection.inputStream.use { it.readBytes().decodeToString() }

                HttpURLConnection.HTTP_NO_CONTENT -> ""
                else -> null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** Where the Trail Day proxy answers. Blank disables the proxy and keeps the public path. */
        const val BASE_URL = "https://trailday-doc-proxy.le-mckenzies.workers.dev"
    }
}
