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

        return OtherTrack(
            globalId = globalId,
            name = nameOf(feature),
            paths = paths,
            // The key is a field like any other on the wire and is not a fact about the track, so it does
            // not travel twice.
            facts = feature.properties.minus(OtherTracksUrl.GLOBAL_ID_FIELD)
        )
    }

    /**
     * What the row is called.
     *
     * **The dataset's own placeholders are not names.** Of the 15,973 rows this app reads, 2,610 say
     * "Unnamed Track", 652 say exactly "Track" and 22 say "Track 7" and the like - 21% of the set with a
     * value where a name goes. A list of rows all called "Track" tells the walker nothing, so a row whose
     * name is blank *or* one of those takes its **locality** instead, which is the dataset's own word for
     * the area a track is in ("Mount Victoria", "Richmond"). A row with neither keeps the app's own words
     * for an unnamed track.
     *
     * The locality alone rather than "Mount Victoria track": a row's second line already says what it is
     * ("Walking Track · Wellington City Council"), and the place is the useful half of the name.
     *
     * **A name made up here is never used to join rows into one walkway** - see `publishedName` - which is
     * why twelve unnamed tracks in one suburb, all showing the suburb's name, stay twelve rows.
     */
    private fun nameOf(feature: GeoJsonParser.Feature): String {
        val given = feature.name?.trim().orEmpty()
        if (given.isNotEmpty() && !isPlaceholderName(given)) return given
        return feature.properties[LOCALITY]?.trim()?.takeIf { it.isNotEmpty() } ?: UNNAMED_FALLBACK
    }

    /** What a row with neither a name nor a locality is left saying, in the app's own casing. */
    private const val UNNAMED_FALLBACK = "Unnamed track"

    /** The dataset's own field for the area a track sits in, which names an unnamed row. */
    private const val LOCALITY = "Locality"
}
