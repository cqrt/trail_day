package nz.mckenzie.trailday.domain.track

import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Minimal GPX reader: pulls every track out of a GPX 1.1 (or 1.0) file, each as the paths the file
 * itself is in.
 *
 * **A `<trk>` is a track and a `<trkseg>` is a segment of it.** The difference matters: the Te Araroa
 * Trust's own export of the trail is **466 `<trk>` elements**, one per section, sorted by name rather
 * than by the trail - and reading the whole document as one track joined 465 section ends to 465
 * section starts, 424 of those joins over a kilometre and the longest 1,331 km, which is a spider's web
 * over the country rather than a trail. So a track is read where the format says a track is, and a
 * segment where the format says a segment is, and what a track's own segments mean is left to
 * [TrackInterchange], which is the one place that rule lives.
 *
 * Namespace-agnostic lookups are used because GPX files in the wild appear both
 * with and without a default `xmlns`, and with various prefixes.
 */
object GpxParser {

    /**
     * Every track the file holds, in document order: what the file calls it, and its own segments.
     *
     * A `<trk>` with no `<trkseg>` is one path of its own points - every point loose under the track,
     * which some tools write - and a file with no `<trk>` at all is one unnamed track of every point in
     * it, which is what this reader has always made of those. A `<trk>` with nothing in it at all is
     * dropped rather than handed back as a track of no points.
     */
    internal fun tracksOf(document: Document): List<FileTrack> {
        val tracks = elementsOf(document, "trk")
        if (tracks.isEmpty()) {
            val loose = pointsOf(document.documentElement)
            return if (loose.isEmpty()) emptyList() else listOf(FileTrack(null, listOf(loose)))
        }
        return tracks.mapNotNull { track ->
            val segments = elementsOf(track, "trkseg").map { pointsOf(it) }.filter { it.isNotEmpty() }
            val paths = segments.ifEmpty { listOf(pointsOf(track)).filter { it.isNotEmpty() } }
            if (paths.isEmpty()) null else FileTrack(childText(track, "name"), paths)
        }
    }

    /**
     * The paths of the file's **first** track, each as the file's own segment.
     *
     * The one-track reading, for a caller that wants a file's points and nothing else. An import wants
     * [tracksOf], because a file of several tracks is several tracks.
     */
    fun parseSegments(xml: String): List<List<GeoPoint>> = segmentsOf(xmlDocument(xml))

    /** The same paths, from a document already parsed. */
    internal fun segmentsOf(document: Document): List<List<GeoPoint>> =
        tracksOf(document).firstOrNull()?.paths.orEmpty()

    /**
     * Every point of the file's **first** track, in document order, as one line.
     *
     * The flattening a single track's segments get when they are not a track with side tracks. Kept
     * beside [parseSegments] because the two answer different questions about the same file - "where
     * does the file say one path ends" and "what are all its points" - and the tests use both.
     */
    fun parse(xml: String): List<GeoPoint> = parseSegments(xml).flatten()

    /** The one `<trkpt>` conversion: what a fix is, wherever it is read from. */
    private fun pointsOf(parent: Element): List<GeoPoint> {
        val trackPoints = elementsOf(parent, "trkpt")
        return trackPoints.mapNotNull { element ->
            val lat = element.getAttribute("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lon = element.getAttribute("lon").toDoubleOrNull() ?: return@mapNotNull null
            GeoPoint(
                lat = lat,
                lng = lon,
                altitudeM = childText(element, "ele")?.toDoubleOrNull(),
                accuracyM = childText(element, "hdop")?.toDoubleOrNull()?.let { (it * 5.0).toFloat() },
                timeMs = childText(element, "time")?.let { parseIsoTime(it) } ?: 0L
            )
        }
    }
}
