package nz.mckenzie.trailday.domain.journey

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.METRES_PER_DEG_LAT
import nz.mckenzie.trailday.domain.geo.METRES_PER_DEG_LNG_AT_EQUATOR
import nz.mckenzie.trailday.domain.reference.Followable
import kotlin.math.cos

/**
 * The published lines worth measuring against where the walker is standing.
 *
 * **A box, because measuring everything is not affordable.** The national dataset is 1,089 rows in
 * Canterbury alone and DOC publishes a few hundred more; walking every path of every one of them on every
 * fix, at one fix a second, is a phone doing arithmetic instead of recording. A box apiece turns that into
 * a handful of comparisons, and the precise measuring is then done on the two or three lines that pass.
 *
 * **A box per path rather than per track**, which is the half that is easy to get wrong. A track that runs
 * five kilometres has a five kilometre box, and a track passing anywhere inside that box would be measured
 * on every fix for the rest of the walk; a path's own box is tight around the ground it actually covers.
 *
 * A box is a filter and never an answer: a line whose box contains the walker can still be a kilometre
 * away, and it is the measuring that decides. What the box buys is not having to measure the rest.
 */

/** One path's ground, as the corners of the box it fits in. */
data class BoxedLine(
    val track: Followable,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
)

/** Every path of every line, boxed. Null-height paths are dropped: a line needs two points. */
fun List<Followable>.boxed(): List<BoxedLine> = flatMap { track ->
    track.paths.mapNotNull { path ->
        if (path.size < 2) return@mapNotNull null
        var south = path.first().lat
        var north = south
        var west = path.first().lng
        var east = west
        for (point in path) {
            if (point.lat < south) south = point.lat
            if (point.lat > north) north = point.lat
            if (point.lng < west) west = point.lng
            if (point.lng > east) east = point.lng
        }
        BoxedLine(track, south, west, north, east)
    }
}

/**
 * The lines with a path whose box comes within [radiusM] of [at], each named once.
 *
 * A long line's box can hold the walker while the line itself is far away - that is what a box is - and
 * the caller measures what this returns before believing any of it.
 */
fun List<BoxedLine>.near(at: GeoPoint, radiusM: Double): List<Followable> {
    val latSlack = radiusM / METRES_PER_DEG_LAT
    val lngSlack = radiusM / (METRES_PER_DEG_LNG_AT_EQUATOR * cos(Math.toRadians(at.lat)).coerceAtLeast(1e-6))
    return filter { box ->
        at.lat >= box.south - latSlack && at.lat <= box.north + latSlack &&
            at.lng >= box.west - lngSlack && at.lng <= box.east + lngSlack
    }
        .map { it.track }
        .distinctBy { it.sourceRef }
}

/**
 * How far from the walker a line has to have ground before it is worth measuring, in metres.
 *
 * **Three times the distance that counts as standing on it.** The box is a square around the ground the
 * line covers, and a line running diagonally through the walker's own box can have its nearest point
 * further away than the corner suggests - so the box is drawn generously and the measuring is what decides.
 * What it costs is a few more lines measured; what it saves is a hand-over that never fires because the
 * line was a metre outside the box.
 */
const val LINES_NEARBY_M = 150.0
