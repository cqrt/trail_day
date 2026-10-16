package nz.mckenzie.trailday.domain.doc

import nz.mckenzie.trailday.domain.track.GeoJsonParser
import nz.mckenzie.trailday.domain.track.TrackInterchange

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

    private fun itemOf(source: DocSource, feature: GeoJsonParser.Feature): DocItem? {
        val objectId = feature.properties[DocSource.OBJECT_ID_FIELD]?.toLongOrNull() ?: 0L
        val paths = when (source.shape) {
            // A place is one coordinate, held as the path of one point the rest of the app speaks in.
            DocShape.POINT -> listOfNotNull(feature.point).map { point -> listOf(point) }

            // A line goes through the app's own rule for what a line is: every path whole, side tracks
            // joined to the track they hang off. A service that sends something else is saying it is not
            // a track, and the app leaves it out rather than drawing it wrongly.
            DocShape.LINE -> when (val outcome = TrackInterchange.reading(feature.paths)) {
                is TrackInterchange.Outcome.Read -> outcome.reading.paths
                is TrackInterchange.Outcome.Invalid -> emptyList()
            }
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
