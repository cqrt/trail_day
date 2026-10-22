package nz.mckenzie.trailday.doc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocProxyJson
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.doc.DocTrackCompanion
import nz.mckenzie.trailday.domain.doc.withCompanion
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fills a downloaded area's tracks with the richer facts the Trail Day proxy holds.
 *
 * The public layer's summary is already on the track by the time this runs; anything the proxy adds
 * is merged over it, and a track the proxy cannot answer for is kept exactly as it arrived. The
 * batch is small on purpose: Cloudflare's free plan caps the subrequests one Worker request may make,
 * so an area is several short requests rather than one that dies half way.
 */
class DocProxyEnricher(
    private val baseUrl: String = DocProxyDetails.BASE_URL,
    private val batchSize: Int = BATCH,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 60_000
) : DocEnricher {

    override suspend fun enrich(items: List<DocItem>): List<DocItem> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext items
        val tracks = items.filter { it.source == DocSource.TRACKS && it.paths.isNotEmpty() }
        if (tracks.isEmpty()) return@withContext items

        val details = mutableMapOf<String, DocTrackCompanion>()
        for (batch in tracks.chunked(batchSize)) {
            val body = requestBody(batch) ?: continue
            val answer = post("$baseUrl/v1/enrich", body) ?: continue
            details += DocProxyJson.enriched(answer)
        }

        if (details.isEmpty()) {
            items
        } else {
            items.map { item ->
                details[item.sourceRef]?.let { item.withCompanion(it) } ?: item
            }
        }
    }

    /** The batch as JSON, or null when none of it has a point to match with. */
    private fun requestBody(tracks: List<DocItem>): String? {
        val entries = tracks.mapNotNull { track ->
            val point = track.paths.flatten().let { it.getOrNull(it.size / 2) }
                ?: return@mapNotNull null
            buildJsonObject {
                put("key", track.sourceRef)
                put("name", track.name)
                put("lat", point.lat)
                put("lon", point.lng)
            }
        }
        if (entries.isEmpty()) return null
        return buildJsonObject {
            put("tracks", buildJsonArray { entries.forEach { add(it) } })
        }.toString()
    }

    private fun post(url: String, body: String): String? {
        val connection = runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", ArcGisDoc.USER_AGENT)
            }
        }.getOrNull() ?: return null

        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { it.readBytes().decodeToString() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** Tracks per request, matching the proxy's own cap with room for its index read. */
        const val BATCH = 40
    }
}
