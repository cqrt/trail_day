package nz.mckenzie.trailday.domain.journey

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.haversineMeters
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos

/**
 * Carrying on to the next of DOC's sections, without the walker touching the phone.
 *
 * A walk published in pieces is followed one piece at a time: at the end of a section the recorder knows
 * nothing about the section that begins there, so the walker has to open the app and choose it. This is
 * the rule that hands over instead - **the next section that starts where this one ends, and carries on
 * the way the walker was walking**.
 *
 * It cannot say how long the whole walk is: the chain is discovered as it is walked, not known before the
 * start. That is what the journey builder is for.
 */
object SectionHandOver {

    /** How near the end of a section counts as being at its end. */
    const val REACH_M = 60.0

    /**
     * How far along the section a walker must have walked before it can hand over at either end.
     *
     * Without it the hand-over would fire the moment a section becomes the target, since a walker
     * standing at a junction is within reach of the end they arrived at - and hand straight back to the
     * section they came from, for ever.
     */
    const val WALKED_M = 100.0

    /** Past this a section is not "carrying on": a hand-over that turns back is the walker's own choice. */
    const val MAX_TURN_DEGREES = 90.0

    /**
     * The section to follow next, or null when there is nothing that carries on from here.
     *
     * [travelledM] is how far along [line] the walker is, and [furthestFromStartM] and
     * [furthestFromEndM] are the furthest they have been from each end since this section became the
     * target - which is what says they walked *along* it rather than arriving at the junction.
     *
     * [notThis] is the section they are on, by name: a junction has two ends, and handing back to the one
     * just walked would be a walk that goes nowhere.
     */
    fun next(
        line: List<GeoPoint>,
        at: GeoPoint,
        travelledM: Double,
        furthestFromStartM: Double,
        furthestFromEndM: Double,
        candidates: List<JourneyTrack>,
        notThis: String?
    ): HandOver? {
        if (line.size < 2) return null
        val total = lengthOf(line)
        if (total <= 0.0 || travelledM <= 0.0) return null

        val toStart = haversineMeters(at.lat, at.lng, line.first().lat, line.first().lng)
        val toEnd = haversineMeters(at.lat, at.lng, line.last().lat, line.last().lng)

        val junction: GeoPoint
        val outward: Double
        when {
            toEnd <= REACH_M && furthestFromStartM >= WALKED_M -> {
                junction = line.last()
                // The way the walker is travelling *out of* this section: from inside, through the end.
                outward = bearing(inside(line, atEnd = true), junction)
            }
            toStart <= REACH_M && furthestFromEndM >= WALKED_M -> {
                junction = line.first()
                outward = bearing(inside(line, atEnd = false), junction)
            }
            else -> return null
        }

        var best: HandOver? = null
        var bestTurn = Double.MAX_VALUE
        for (track in candidates) {
            if (notThis != null && track.name == notThis) continue
            for (path in track.paths) {
                if (path.size < 2) continue
                for (endsAtLast in listOf(true, false)) {
                    val end = if (endsAtLast) path.last() else path.first()
                    val away = haversineMeters(junction.lat, junction.lng, end.lat, end.lng)
                    if (away > REACH_M) continue
                    // Which way this section leaves the junction: from the junction, into the section.
                    val direction = bearing(end, inside(path, atEnd = endsAtLast))
                    val turn = angleBetween(outward, direction)
                    if (turn > MAX_TURN_DEGREES) continue
                    val oriented = if (endsAtLast) path.reversed() else path
                    // The straightest continuation wins; a tie goes to the longer section, because two
                    // sections leaving a junction at the same angle are one line with a join in it.
                    if (turn < bestTurn - 1e-9 || (abs(turn - bestTurn) <= 1e-9 && (best == null || oriented.size > best!!.line.size))) {
                        bestTurn = turn
                        best = HandOver(name = track.name, line = oriented, turnDegrees = turn)
                    }
                }
            }
        }
        return best
    }

    /** What the recorder moves onto: the section's name, and its line starting at the junction. */
    data class HandOver(val name: String, val line: List<GeoPoint>, val turnDegrees: Double)

    /**
     * A point [DIRECTION_SAMPLE_M] inside a path from the end asked about.
     *
     * Read a little way in rather than at the junction, so a line that begins with a wiggle does not decide
     * which way the whole section goes.
     */
    private fun inside(path: List<GeoPoint>, atEnd: Boolean): GeoPoint {
        var index = if (atEnd) path.lastIndex else 0
        var walked = 0.0
        while (walked < DIRECTION_SAMPLE_M) {
            val next = if (atEnd) index - 1 else index + 1
            if (next !in path.indices) break
            walked += haversineMeters(path[index].lat, path[index].lng, path[next].lat, path[next].lng)
            index = next
        }
        return path[index]
    }

    /** The compass bearing from one place to another, clockwise from north. */
    private fun bearing(from: GeoPoint, to: GeoPoint): Double {
        val midLat = Math.toRadians((from.lat + to.lat) / 2.0)
        val north = (to.lat - from.lat) * METRES_PER_DEGREE
        val east = (to.lng - from.lng) * METRES_PER_DEGREE * cos(midLat)
        return Math.toDegrees(atan2(east, north))
    }

    /** The angle between two bearings, in degrees, as a magnitude: 0 is straight on. */
    private fun angleBetween(a: Double, b: Double): Double {
        val difference = abs((a - b + 540.0) % 360.0 - 180.0)
        return difference
    }

    private fun lengthOf(points: List<GeoPoint>): Double =
        (1 until points.size).sumOf { index ->
            haversineMeters(points[index - 1].lat, points[index - 1].lng, points[index].lat, points[index].lng)
        }

    /** How far along a line the direction is read from: far enough past a junction's own wiggle. */
    private const val DIRECTION_SAMPLE_M = 40.0

    private const val METRES_PER_DEGREE = 111_320.0
}
