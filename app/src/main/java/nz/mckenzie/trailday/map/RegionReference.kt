package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.region.Region

/**
 * New Zealand's regions on the offline picker's map: **an outline for each, and a wash under the one the
 * walker has chosen.**
 *
 * A reference layer like DOC's and the tracks dataset's, and for the same reason - nothing here is the
 * walker's own work, and nothing on the map can act on a region - but it is drawn **under everything**,
 * because it is not a thing to read on the ground, it is the choice of the ground itself.
 *
 * Two sources rather than one, which is the whole of the design: [LayerIds.SOURCE] holds all sixteen
 * outlines as unfilled polygons and is drawn with a hairline, while [LayerIds.PICKED_SOURCE] holds only the
 * chosen region and is filled. One source with a data-driven colour would be a style expression doing what
 * two documents do more plainly, and the document is rebuilt on every tap anyway.
 */
object RegionLayerIds {

    /** All sixteen, as polygons: drawn as outlines, never filled. */
    const val SOURCE = "trailday-regions"

    /**
     * The white line under the hairlines, as every reference line on this map wears.
     *
     * A boundary between two regions runs over dark bush, pale scree and water in the space of one screen,
     * and a single grey line disappears over half of it. The app's own answer for a reference line is a
     * casing, so this is the same answer at country scale.
     */
    const val CASING = "trailday-regions-casing"

    /** The hairlines between regions. */
    const val EDGE = "trailday-regions-edge"

    /** The one region that has been chosen. */
    const val PICKED_SOURCE = "trailday-region-picked"

    /** The wash under the chosen region, so the choice is visible without hiding the map under it. */
    const val PICKED_FILL = "trailday-region-picked-fill"

    /** The chosen region's own border, heavier than the hairlines. */
    const val PICKED_EDGE = "trailday-region-picked-edge"

    /** The app's outline colour: present enough to read a country map, quiet enough to ignore. */
    const val EDGE_COLOUR = "#6F7869"

    const val EDGE_WIDTH = 1.2f

    /** Wider than the line it sits under, so a little of it shows on both sides. */
    const val CASING_WIDTH = 2.6f

    /** The app's primary green, which is the colour every "chosen" thing in the app already wears. */
    const val PICKED_COLOUR = "#2E7D32"

    /**
     * How much of the chosen region is washed in.
     *
     * Light enough that the map under it is still the map - at country scale the ground is the point, and a
     * solid fill would hide exactly what the walker is choosing by.
     */
    const val PICKED_FILL_OPACITY = 0.22f

    const val PICKED_WIDTH = 2.4f
}

/** The regions as the map's own document. */
object RegionGeoJson {

    /** Every region's outline: a polygon per ring, unfilled, so the map shows through. */
    fun build(regions: List<Region>): String = document(
        regions.flatMap { region -> region.rings.map { ring -> polygon(ring) } }
    )

    /** The chosen region, or nothing at all when the walker has not chosen one. */
    fun picked(region: Region?): String =
        if (region == null) EMPTY else document(region.rings.map { ring -> polygon(ring) })

    val EMPTY: String = document(emptyList())

    /**
     * One ring as a GeoJSON polygon, **closed**.
     *
     * A ring in the app does not repeat its first point, and a GeoJSON ring must: an unclosed one is not
     * a polygon at all, and a renderer is free to draw it as a line and leave the shape unfilled.
     */
    private fun polygon(ring: List<GeoPoint>): String {
        val closed = if (ring.first() == ring.last()) ring else ring + ring.first()
        val coordinates = closed.joinToString(",") { point ->
            // GeoJSON's own order, longitude first: the one place in the app where that is written.
            "[${point.lng},${point.lat}]"
        }
        return """{"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[[$coordinates]]}}"""
    }

    private fun document(polygons: List<String>): String =
        """{"type":"FeatureCollection","features":[${polygons.joinToString(",")}]}"""
}
