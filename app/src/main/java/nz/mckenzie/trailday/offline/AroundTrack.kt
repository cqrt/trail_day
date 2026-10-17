package nz.mckenzie.trailday.offline

import kotlin.math.cos
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.METRES_PER_DEG_LAT
import nz.mckenzie.trailday.domain.geo.METRES_PER_DEG_LNG_AT_EQUATOR
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.domain.tiles.withMinimumSpan

/**
 * The ground to keep for a track: its own box, grown by a width either side, and what to call the area.
 *
 * A walker's unit is **the track I am about to walk**, not a rectangle they draw on a map - so this is the
 * offline picker's own machinery with a different source of corners, which is what the plan asked for
 * rather than a second downloader.
 */
data class AroundTrack(val name: String, val bounds: LatLngBounds)

/**
 * How much ground either side of a track is kept.
 *
 * **Two kilometres**: enough to be wrong in - a GPS fix wanders, and a side trip to a hut is off the line -
 * and small enough that one track's area does not swallow the next valley. It is the same kind of choice as
 * the app's other margins, and it is the one number to change if a walker says otherwise.
 */
const val GROUND_WIDTH_M = 2_000.0

/**
 * The box around [geometry]'s own line, [widthM] either side, or null when there is no line to go around.
 *
 * **Longitude degrees are shorter than latitude ones away from the equator**, so the width in longitude is
 * divided by the cosine of the box's own middle - the same arithmetic, from the same constants, that the
 * box asked of DOC uses ([METRES_PER_DEG_LAT]). A tiny track is given the app's minimum span, so a
 * one-vertex line still asks for ground rather than for a point.
 */
fun groundAround(
    name: String,
    geometry: AssetGeometry,
    widthM: Double = GROUND_WIDTH_M
): AroundTrack? {
    val points = geometry.paths.flatten()
    if (points.isEmpty()) return null

    val minLat = points.minOf { it.lat }
    val maxLat = points.maxOf { it.lat }
    val minLng = points.minOf { it.lng }
    val maxLng = points.maxOf { it.lng }

    val latitudeM = widthM / METRES_PER_DEG_LAT
    val middle = Math.toRadians((minLat + maxLat) / 2.0)
    val longitudeM = widthM / (METRES_PER_DEG_LNG_AT_EQUATOR * maxOf(cos(middle), MIN_COS))

    return AroundTrack(
        name = name,
        bounds = LatLngBounds(
            minLat = minLat - latitudeM,
            minLng = minLng - longitudeM,
            maxLat = maxLat + latitudeM,
            maxLng = maxLng + longitudeM
        ).withMinimumSpan()
    )
}

/** The floor under the cosine, so a track at the pole still has a box rather than an infinite one. */
private const val MIN_COS = 0.01
