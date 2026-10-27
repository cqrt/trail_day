package nz.mckenzie.trailday.domain.doc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nz.mckenzie.trailday.domain.text.PlainText

/** What the proxy's lookup route answered: the API's own id for a track the app already holds. */
data class DocProxyTrack(
    val assetId: String,
    val name: String,
    val distanceM: Double? = null
)

/** One track's alerts from the proxy's bulk route. */
data class DocProxyAlertTrack(
    val assetId: String?,
    val name: String?,
    val alerts: List<DocAlert>
)

/**
 * Reads the JSON the Trail Day DOC proxy returns.
 *
 * The proxy exists so the app never holds DOC's key; the shapes here are the app's own, not DOC's
 * raw answers, so a change at DOC's end is met by the Worker rather than by every installed app.
 *
 * Text that will not read is **nothing rather than a crash**: a proxy that answers with an error
 * page, or a later proxy that adds a field, must not be able to stop a detail page drawing.
 */
object DocProxyJson {

    /** The `assetId` for a track the proxy matched by name and position, or null when it matched none. */
    fun lookup(text: String): DocProxyTrack? = runCatching {
        val root = Json.parseToJsonElement(text).jsonObject
        val assetId = root["assetId"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: return null
        DocProxyTrack(
            assetId = assetId,
            name = root["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            distanceM = root["distanceM"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        )
    }.getOrNull()

    /** A track's detail as the website summary the rest of the app already reads. */
    fun detail(text: String): DocTrackCompanion? = runCatching {
        detailOf(Json.parseToJsonElement(text).jsonObject)
    }.getOrNull()

    /**
     * The batch enrich answer, by the key the app asked with.
     *
     * A track the proxy could not match, or could not read, is simply absent: the caller keeps what
     * the public layers already gave it.
     */
    fun enriched(text: String): Map<String, DocTrackCompanion> = runCatching {
        val root = Json.parseToJsonElement(text).jsonObject
        val tracks = (root["tracks"] as? JsonArray).orEmpty()
        tracks.mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val key = entry.text("key") ?: return@mapNotNull null
            val detail = entry["detail"] as? JsonObject ?: return@mapNotNull null
            key to detailOf(detail)
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun detailOf(root: JsonObject): DocTrackCompanion = DocTrackCompanion(
        introduction = root.text("introduction")?.let(PlainText::plain),
        difficulty = root.array("walkTrackCategory")?.joinToString(", "),
        completionTime = root.text("walkDuration"),
        webPage = root.text("staticLink"),
        thumbnail = root.text("introductionThumbnail"),
        walkingDuration = root.array("walkDurationCategory")?.joinToString(", "),
        permittedActivities = root.array("permittedActivities")?.joinToString(", "),
        wheelchairsAndBuggies = root.text("wheelchairsAndBuggies")?.let(PlainText::plain),
        mtbDuration = root.text("mtbDuration"),
        mtbCategory = root.array("mtbTrackCategory")?.joinToString(", "),
        kayakingDuration = root.text("kayakingDuration"),
        dogsAllowed = root.text("dogsAllowed")?.let(PlainText::plain),
        place = root.text("locationString"),
        region = root.array("region")?.joinToString(", ")
    )

    /** One track's alerts. */
    fun alerts(text: String): List<DocAlert> = runCatching {
        alertsOf(Json.parseToJsonElement(text).jsonObject)
    }.getOrDefault(emptyList())

    /** The bulk alert answer, track by track. */
    fun alertTracks(text: String): List<DocProxyAlertTrack> = runCatching {
        val root = Json.parseToJsonElement(text)
        val tracks = if (root is JsonArray) root else return emptyList()
        tracks.mapNotNull { element ->
            val track = element as? JsonObject ?: return@mapNotNull null
            DocProxyAlertTrack(
                assetId = track.text("assetId"),
                name = track.text("name"),
                alerts = alertsOf(track)
            )
        }
    }.getOrDefault(emptyList())

    private fun alertsOf(track: JsonObject): List<DocAlert> =
        (track["alerts"] as? JsonArray).orEmpty().mapNotNull { element ->
            val alert = element as? JsonObject ?: return@mapNotNull null
            val title = alert.text("title") ?: alert.text("heading") ?: return@mapNotNull null
            DocAlert(
                title = title,
                detail = alert.text("detail")?.let(PlainText::plain),
                date = alert.text("date") ?: alert.text("displayDate")
            )
        }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.array(key: String): List<String>? =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }

    private val Json = Json { ignoreUnknownKeys = true }
}
