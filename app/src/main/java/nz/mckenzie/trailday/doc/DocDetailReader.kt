package nz.mckenzie.trailday.doc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.mckenzie.trailday.domain.doc.DocAlert
import nz.mckenzie.trailday.domain.doc.DocAlertsState
import nz.mckenzie.trailday.domain.doc.DocCompanionJson
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.text.PlainText
import nz.mckenzie.trailday.domain.doc.DocTrackCompanion
import nz.mckenzie.trailday.domain.doc.DocTrackCompanions
import nz.mckenzie.trailday.domain.doc.DocTrackNames
import nz.mckenzie.trailday.domain.doc.DocUrl
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The extra facts a DOC track page needs beyond the track line's own records. */
interface DocDetailReader {
    /** The website summary for a DOC track, or null when it cannot be read. */
    suspend fun companionFor(track: DocItem): DocTrackCompanion?

    /** The current alerts for any DOC thing, or a state saying why there are none to show. */
    suspend fun alertsFor(item: DocItem): DocAlertsState
}

/**
 * Reads the two things a DOC detail page needs over the public DOC web services.
 *
 * **The track's prose is on a second public layer, not the line service.** The walking experiences
 * layer carries the short introduction, difficulty, completion time and page link; this reader finds
 * it by the track's name and confirms the match with the point's distance to the line.
 *
 * **Alerts are read by the page's own GUID.** The website's public alert endpoint answers 204 when a
 * page has none, which is different from "we could not ask": the detail page says nothing for the
 * former and says it could not check for the latter.
 */
class DocWebDetails(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 20_000
) : DocDetailReader {

    override suspend fun companionFor(track: DocItem): DocTrackCompanion? =
        withContext(Dispatchers.IO) {
            val stem = searchStem(track.name)
            val where = "UPPER(name) LIKE UPPER('%${stem.replace("'", "''")}%')"
            val url = COMPANION_URL +
                "?where=${URLEncoder.encode(where, "UTF-8")}" +
                "&outFields=*&returnGeometry=true&outSR=4326&resultRecordCount=20&f=geojson"
            val body = read(url) ?: return@withContext null
            DocTrackCompanions.bestFor(track, DocCompanionJson.parse(body))?.summary()
        }

    override suspend fun alertsFor(item: DocItem): DocAlertsState = withContext(Dispatchers.IO) {
        val guid = alertGuid(item.link) ?: return@withContext DocAlertsState.NotAvailable
        val connection = runCatching {
            (URL("$ALERTS_URL/$guid").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("User-Agent", ArcGisDoc.USER_AGENT)
            }
        }.getOrNull() ?: return@withContext DocAlertsState.Failed("Could not check DOC alerts")

        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NO_CONTENT, HttpURLConnection.HTTP_NOT_FOUND ->
                    DocAlertsState.Found(emptyList())

                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.use { it.readBytes().decodeToString() }
                    DocAlertsState.Found(DocAlertsParser.parse(body))
                }

                else -> DocAlertsState.Failed("DOC alerts answered HTTP ${connection.responseCode}")
            }
        } catch (failure: Exception) {
            DocAlertsState.Failed(failure.message ?: "Could not check DOC alerts")
        } finally {
            connection.disconnect()
        }
    }

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
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { it.readBytes().decodeToString() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private val COMPANION_URL = DocUrl.COMPANION_SERVICE_URL
        private const val ALERTS_URL = "https://www.doc.govt.nz/api/alerts/guid"

        /**
         * The part of a track's name worth asking the companion layer about.
         *
         * The line service says "Forks Flat Tk" where the website layer says "Forks Flat Track"; the
         * trailing kind is not part of the name. The point is still checked against the line, so a
         * common stem cannot pull in a different track that happens to share a prefix.
         */
        internal fun searchStem(name: String): String = DocTrackNames.stem(name)

        /**
         * The alert GUID inside one of DOC's `/link/<guid>.aspx` page links, in the dashed form the
         * alert endpoint takes. Null when the link does not carry one.
         */
        internal fun alertGuid(link: String?): String? {
            if (link.isNullOrBlank()) return null
            val raw = runCatching { java.net.URI(link).path.orEmpty() }.getOrDefault(link)
            val dashed = Regex(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
                    "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
            ).find(raw)?.value
            if (dashed != null) return dashed
            val compact = Regex("[0-9a-fA-F]{32}").find(raw)?.value ?: return null
            return "${compact.substring(0, 8)}-${compact.substring(8, 12)}-" +
                "${compact.substring(12, 16)}-${compact.substring(16, 20)}-" +
                compact.substring(20, 32)
        }
    }
}

/** The website alert JSON is shaped by grouping; this reads the alert objects out of any wrapper. */
internal object DocAlertsParser {

    fun parse(body: String): List<DocAlert> {
        if (body.isBlank()) return emptyList()
        val root = runCatching { JSONTokener(body).nextValue() }.getOrNull()
            ?: return emptyList()
        val found = mutableListOf<JSONObject>()
        collect(root, found)
        return found.mapNotNull { objectOf ->
            val title = objectOf.text("heading")
                ?: objectOf.text("summary")
                ?: objectOf.text("title")
                ?: objectOf.text("name")
                ?: return@mapNotNull null
            val detail = objectOf.text("description")
                ?: objectOf.text("detail")
                ?: objectOf.text("subText")
            DocAlert(
                title = title,
                detail = detail?.let(PlainText::plain),
                date = objectOf.text("displayDate") ?: objectOf.text("sortDate")
            )
        }.distinctBy { it.title to it.detail }
    }

    private fun collect(value: Any?, into: MutableList<JSONObject>) {
        when (value) {
            is JSONArray -> for (index in 0 until value.length()) collect(value.opt(index), into)
            is JSONObject -> {
                if (value.has("alerts")) {
                    collect(value.opt("alerts"), into)
                } else if (
                    value.has("heading") || value.has("summary") ||
                    value.has("title") || value.has("description")
                ) {
                    into += value
                } else {
                    for (key in value.keys()) collect(value.opt(key), into)
                }
            }
        }
    }

    private fun JSONObject.text(key: String): String? =
        optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
}
