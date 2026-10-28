package nz.mckenzie.trailday.domain.region

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.tiles.LatLngBounds

/**
 * A region of New Zealand: **what a walker calls the part of the country they are going to.**
 *
 * "We're going to Otago" is how a trip is described, and drawing a box around Otago on a phone is how the
 * app used to ask for it. A region is the same answer already worked out: one tap, a name everybody knows,
 * and a count that can be stated before anything is downloaded.
 *
 * A region is **not** an administrative unit the dataset knows about. The national tracks dataset writes a
 * *district* on every row - Dunedin City, Clutha - so a region is [districts] gathered together: see
 * [RegionDistricts]. Its [rings] come from Stats NZ and are used for two things only, drawing the shape and
 * deciding which one a finger landed in.
 */
data class Region(
    /** What the walker reads: "Otago", "Wellington", "Manawatū-Whanganui". */
    val name: String,
    /** The dataset's own words for the districts inside it, matched against `LocalAuthority`. */
    val districts: List<String>,
    /**
     * The outline, as the rings Stats NZ publishes.
     *
     * A region with islands has more than one, and a ring is a closed line - the last point is not
     * repeated, because a ring that repeats it draws a zero-length segment at the join.
     */
    val rings: List<List<GeoPoint>>
) {

    /** The box the region fits in, for asking a service that only understands rectangles. */
    val bounds: LatLngBounds? get() {
        val points = rings.flatten()
        if (points.isEmpty()) return null
        return LatLngBounds(
            minLat = points.minOf { it.lat },
            minLng = points.minOf { it.lng },
            maxLat = points.maxOf { it.lat },
            maxLng = points.maxOf { it.lng }
        )
    }

    /** Whether [point] falls inside the outline: which region a tapped finger means. */
    fun holds(point: GeoPoint): Boolean = rings.any { it.holds(point) }

    /** Whether this region has anything the dataset is asked for by name. */
    val isOffered: Boolean get() = districts.isNotEmpty() && rings.isNotEmpty()
}

/**
 * Point in a ring, by counting the crossings of a line drawn east from the point.
 *
 * The standard ray test, written out because the app needs one and only one: a finger taps a country map
 * and twenty-odd vertices per region decide it. Edges are half-open (`>` on one end, `<=` on the other
 * through the sign test), so a point level with a vertex is counted once rather than twice.
 */
private fun List<GeoPoint>.holds(point: GeoPoint): Boolean {
    if (size < 3) return false
    var inside = false
    var previous = size - 1
    for (index in indices) {
        val a = this[index]
        val b = this[previous]
        // A horizontal edge cannot be crossed by a horizontal ray, so it is skipped - and skipping it is
        // also what keeps the division below safe.
        if (a.lat > point.lat != b.lat > point.lat) {
            val crossingLng = (b.lng - a.lng) * (point.lat - a.lat) / (b.lat - a.lat) + a.lng
            if (point.lng < crossingLng) inside = !inside
        }
        previous = index
    }
    return inside
}
