package nz.mckenzie.trailday.domain.other

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nz.mckenzie.trailday.domain.track.GeoJsonParser

/**
 * What the national tracks dataset answered, read into the app's own record of a row.
 *
 * Read through [GeoJsonParser] - the same reader a GeoJSON file gets - because the service is asked for
 * `f=geojson&outSR=4326` and that is the format the app already reads everywhere. Nothing here learns a
 * second coordinate system or a second document shape.
 *
 * **A row that cannot be read is dropped rather than guessed at.** A row with no `GlobalID` has no key
 * and so cannot be cached or recognised again, and a line with no path of two vertices is not a line;
 * neither becomes a row the walker can do nothing with. Everything else about the row is kept exactly as
 * it arrived, ready for whoever reads it.
 */
object OtherTracksJson {

    /** The rows in [text]. */
    fun parse(text: String): List<OtherTrack> =
        GeoJsonParser.parse(text).mapNotNull { feature -> trackOf(feature) }

    /**
     * How many rows a count query answered with, or null when the answer was not a count.
     *
     * `returnCountOnly` answers with `{"count": N}` and nothing else; an error answers with an `error`
     * object. Null says "not counted", and the caller decides what to say about that.
     */
    fun parseCount(text: String): Int? = runCatching {
        CountJson.parseToJsonElement(text).jsonObject["count"]?.jsonPrimitive?.int
    }.getOrNull()

    private val CountJson = Json { ignoreUnknownKeys = true }

    private fun trackOf(feature: GeoJsonParser.Feature): OtherTrack? {
        val globalId = feature.properties[OtherTracksUrl.GLOBAL_ID_FIELD]?.trim().orEmpty()
        if (globalId.isEmpty()) return null

        // A path of one point is not a line, and the service publishes no point features in this layer.
        val paths = feature.paths.filter { it.size >= 2 }
        if (paths.isEmpty()) return null

        // **A row with no name of its own is dropped.** See [nameOf].
        val name = nameOf(feature) ?: return null

        return OtherTrack(
            globalId = globalId,
            name = name,
            paths = paths,
            // The key is a field like any other on the wire and is not a fact about the track, so it does
            // not travel twice.
            facts = feature.properties.minus(OtherTracksUrl.GLOBAL_ID_FIELD)
        )
    }

    /**
     * What the row is called, or **null when the dataset gave it no name at all** - which drops the row.
     *
     * **The dataset's own placeholders are not names, and these rows are not shown.** 2,699 of the rows the
     * app reads say "Unnamed Track", 676 say exactly "Track", 22 say "Track 7" and the like, 97 say
     * "Unnamed", four say "Track unnamed", and 739 have nothing at all: **4,130 rows of 16,893**, almost a
     * quarter of the set, with a value where a name goes.
     *
     * They used to be renamed after their **locality**, so a list could at least tell one from another. That
     * was wrong for a simpler reason: a row with no name is not a track a walker can ask for, recognise, or
     * say out loud, and a list of "Mount Victoria" repeated twelve times is a list of nothing. **If a council
     * names one, it comes back** - the drop is of this month's rows, not of the ground.
     *
     * The big families are also excluded in the query, so a count and a download agree; see
     * [OtherTracksUrl.where]. This is the exact rule those cannot express - `Track 7` needs a pattern, and a
     * pattern that also matched `Track to Crows Nest` would drop a track somebody named.
     */
    private fun nameOf(feature: GeoJsonParser.Feature): String? {
        val given = feature.name?.trim().orEmpty()
        return given.takeIf { it.isNotEmpty() && !isPlaceholderName(it) }
    }
}
