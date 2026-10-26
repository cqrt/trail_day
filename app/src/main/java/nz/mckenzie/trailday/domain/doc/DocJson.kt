package nz.mckenzie.trailday.domain.doc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nz.mckenzie.trailday.domain.track.GeoJsonParser

/**
 * What one of DOC's services answered, read into the app's own record of it.
 *
 * One parser for four services, because the answers differ in two ways and neither is the reading: the
 * shape (a line or a place) and which facts come back. Both are the source's own business - see
 * [DocSource] - so this walks the features and hands each one to the source's rules.
 *
 * **A feature that cannot be read is dropped rather than guessed at**: a track with one vertex is not a
 * track, a place with no coordinate is nowhere, and neither becomes a row the walker can do nothing
 * with. Everything else about the feature is kept exactly as DOC sent it, ready for whoever reads it.
 */
object DocJson {

    /** The items in [text], as the service [source] publishes them. */
    fun parse(source: DocSource, text: String): List<DocItem> =
        GeoJsonParser.parse(text).mapNotNull { feature -> itemOf(source, feature) }

    /**
     * How many records a count query answered with, or null when the answer was not a count.
     *
     * `returnCountOnly` answers with `{"count": N}` and nothing else. An error answers with an `error`
     * object instead, and a body that is neither is not a number to put in front of a walker: null says
     * "not counted", and the caller decides what to say about that.
     */
    fun parseCount(text: String): Int? = runCatching {
        CountJson.parseToJsonElement(text).jsonObject["count"]?.jsonPrimitive?.int
    }.getOrNull()

    private val CountJson = Json { ignoreUnknownKeys = true }

    private fun itemOf(source: DocSource, feature: GeoJsonParser.Feature): DocItem? {
        val objectId = feature.properties[DocSource.OBJECT_ID_FIELD]?.toLongOrNull() ?: 0L
        val paths = when (source.shape) {
            // A place is one coordinate, held as the path of one point the rest of the app speaks in.
            DocShape.POINT -> listOfNotNull(feature.point).map { point -> listOf(point) }

            // A line keeps DOC's own paths: whole, and **apart**.
            //
            // Deliberately not read through [TrackInterchange]'s file rule. That rule joins paths that do
            // not meet into one line, which is right for a file a tool cut a track up into and wrong for a
            // service that publishes a track in pieces: DOC's own `line` is a multi-path polyline, and
            // every renderer draws one as separate lines. "Breast Hill Track" is four paths, one of them
            // 18 km away near the Timaru River, and joining them drew a straight line across the lake -
            // and `AssetGeometry.lengthM` counts every metre of every path, so the join was counted as
            // distance the track does not have. A path of one point is not a line and is left out.
            DocShape.LINE -> feature.paths.filter { it.size >= 2 }
        }
        if (paths.isEmpty()) return null

        return DocItem(
            source = source,
            objectId = objectId,
            name = feature.name?.takeIf { it.isNotBlank() } ?: nameFor(source, objectId),
            paths = paths,
            // The id is a field like any other on the wire and is not a fact about the place, so it does
            // not travel twice.
            facts = feature.properties.minus(DocSource.OBJECT_ID_FIELD)
        )
    }

    /**
     * What a thing DOC gave no name to is called.
     *
     * A name is needed rather than optional: it is what a list row and a card are about, and a blank
     * one would be a row the walker cannot tell from its neighbour. DOC's number is the one thing every
     * feature has.
     */
    private fun nameFor(source: DocSource, objectId: Long): String =
        if (objectId != 0L) "DOC ${source.singular.lowercase()} $objectId" else "DOC ${source.singular.lowercase()}"
}
