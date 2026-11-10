package nz.mckenzie.trailday.domain.track

import nz.mckenzie.trailday.domain.geo.GeoPoint

/**
 * One track a file holds, before the app's join rule is applied to it: what the format calls it, and
 * its own paths in the order the file has them.
 *
 * **A file holds tracks, and a track holds paths.** GPX says that with a `<trk>` and its `<trkseg>`s,
 * KML with a `<Placemark>` and the geometry inside it, and GeoJSON with a feature and its lines - so
 * all three readers hand back one shape, and [TrackInterchange] has one thing to apply its rule to.
 *
 * What those paths *mean* as a track - a line, or a line with side tracks hanging off it - is decided
 * there, by the rule the app's own drawing and its own recorder both produce. The format only says
 * where one path ends and the next begins.
 *
 * [type] is what the file says the track was - Garmin's `<type>cycling</type>`, a GeoJSON feature's
 * `sport`, a KML placemark's ExtendedData - read as the text the file used and interpreted by
 * [ActivityHint]. Null is a file that says nothing, which is most of them.
 */
internal data class FileTrack(
    val name: String?,
    val paths: List<List<GeoPoint>>,
    val type: String? = null
)
