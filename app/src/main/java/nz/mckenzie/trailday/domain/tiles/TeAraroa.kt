package nz.mckenzie.trailday.domain.tiles

/**
 * The Te Araroa trail, as the app carries it: the whole route, bundled, drawn as a layer of its own.
 *
 * **A feature of the app rather than something to find and import.** The Trust publishes the trail as a
 * GPX of 466 tracks - one per section, sorted by name rather than by the trail - and a walker who wants
 * it on the map wants it there with no reception and without first finding a file. So it is kept in the
 * app's own assets as the GeoJSON the map already draws, made from the Trust's own
 * `TeAraroaTrail_2026_27.gpx` with the coordinates rounded to six decimal places - about 10 cm, which is
 * nothing against a generalised trail. `docs/te-araroa.md` says where the file came from and how to
 * make it again.
 *
 * **The licence is the Trust's, and it is named wherever the line is drawn.** The trail is Creative
 * Commons 4.0 New Zealand, so [SHORT_ATTRIBUTION] rides in the map's own credit strip, linked to
 * [ATTRIBUTION_LINK], for as long as the layer is switched on - the same arrangement the basemap and
 * LINZ's place names already have.
 */
object TeAraroa {

    /** The bundled route: a FeatureCollection of one LineString per section, each one named. */
    const val ASSET = "te-araroa.geojson"

    /** What the credit strip calls it, beside the basemap's own credit. */
    const val SHORT_ATTRIBUTION = "\u00a9 Te Araroa Trust"

    /** Where that credit links: the Trust's own page for the data and the terms it is published under. */
    const val ATTRIBUTION_LINK = "https://www.teararoa.org.nz/notes-and-maps/"
}
