package nz.mckenzie.trailday.domain.geo

/**
 * Where a walker is against one line: how far off it, how far along it, and how long it is.
 *
 * **The arithmetic behind [FollowProgress], without the climbing.** Following a line asks four questions
 * - how far off, how far along, how far left, how much climb left - and the first three are the same walk
 * over the segments. The fourth is not: it reads the heights off the whole line, which is a pass over
 * every point.
 *
 * That matters because a line is measured twice for two different reasons. Once per fix for the line being
 * followed, where the climb is worth it. And once per nearby line when the recorder is looking for one the
 * walker has wandered onto - a few dozen lines, many times a second - where the climb is not asked for and
 * must not be paid for.
 *
 * So the walk over the segments lives here, and both callers share it. Two copies of it is how two screens
 * come to disagree about where the walker is on the same line.
 */

/** How far along one line the walker is, and how long that line is. */
data class PathPoint(
    /** How far the walker is from the line itself: zero when they are standing on it. */
    val offM: Double,
    /** How far along the line the nearest point is, measured from the line's start. */
    val alongM: Double,
    /** The whole length of the line. */
    val totalM: Double
) {

    /** How much of the line is left after that point. */
    val remainingM: Double get() = (totalM - alongM).coerceAtLeast(0.0)
}

/**
 * Where [point] is against [path], or null when there is no line to be against.
 *
 * The nearest point is taken over each segment and the distance measured to it, so a walker beside the
 * middle of a long straight stretch is measured against that stretch rather than against whichever vertex
 * happens to be nearest. A walker past the end of the line is at the end of it, 0 m left and as far off as
 * they are.
 */
fun nearestOnPath(path: List<GeoPoint>, point: GeoPoint): PathPoint? {
    if (path.size < 2) return null

    var travelled = 0.0
    var nearestOffM = Double.MAX_VALUE
    var nearestAlongM = 0.0

    for (index in 1 until path.size) {
        val start = path[index - 1]
        val end = path[index]
        val nearest = nearestPointOnSegment(point, start, end)
        val offM = haversineMeters(point.lat, point.lng, nearest.lat, nearest.lng)
        if (offM < nearestOffM) {
            nearestOffM = offM
            nearestAlongM = travelled + haversineMeters(start.lat, start.lng, nearest.lat, nearest.lng)
        }
        travelled += haversineMeters(start.lat, start.lng, end.lat, end.lng)
    }

    return PathPoint(offM = nearestOffM, alongM = nearestAlongM, totalM = travelled)
}

/**
 * The part of [path] from [alongM] onwards, in the direction the walker is going.
 *
 * **A line joined in the middle starts where the walker joined it.** The alternative - handing over the
 * whole piece and measuring from its start - reads "87 m of 197 m" the moment they step onto it, which
 * looks like they have already walked most of a track they have never been on. Cutting it at the join
 * reads "0 m of 110 m", which is what the end-of-track hand-over already does by turning the next piece
 * to start at the junction.
 *
 * [forward] is the direction along the path's own points: false turns the line round first, which is what
 * a walker joining a published line and setting off the other way down it needs. The join point is written
 * once, as the first vertex.
 */
fun pathFrom(path: List<GeoPoint>, alongM: Double, forward: Boolean): List<GeoPoint> {
    if (path.size < 2) return path

    if (!forward) {
        val reversed = path.reversed()
        val total = polylineLengthMeters(reversed)
        return pathFrom(reversed, total - alongM, forward = true)
    }

    val total = polylineLengthMeters(path)
    val target = alongM.coerceIn(0.0, total)

    val points = mutableListOf<GeoPoint>()
    var travelled = 0.0
    var joined = false

    for (index in 1 until path.size) {
        val start = path[index - 1]
        val end = path[index]
        val length = haversineMeters(start.lat, start.lng, end.lat, end.lng)
        val segmentEnd = travelled + length

        if (!joined && segmentEnd >= target - ALONG_EPSILON_M) {
            val fraction = if (length <= 0.0) 0.0 else ((target - travelled) / length).coerceIn(0.0, 1.0)
            points += interpolate(start, end, fraction)
            joined = true
        } else if (joined) {
            points += end
        }

        travelled = segmentEnd
    }

    if (!joined) points += path.last()
    return points
}

/**
 * A point [fraction] of the way from [start] to [end].
 *
 * The height comes with it when both ends have one, because a joined line's profile should not gain a step
 * where it was joined. A line published without heights stays without them rather than gaining a zero.
 */
private fun interpolate(start: GeoPoint, end: GeoPoint, fraction: Double): GeoPoint {
    val height = if (start.altitudeM != null && end.altitudeM != null) {
        start.altitudeM + (end.altitudeM - start.altitudeM) * fraction
    } else {
        null
    }
    return GeoPoint(
        lat = start.lat + (end.lat - start.lat) * fraction,
        lng = start.lng + (end.lng - start.lng) * fraction,
        altitudeM = height
    )
}

/** Slack for a join that lands exactly on a vertex, in metres: the same metre a published line is worth. */
private const val ALONG_EPSILON_M = 1e-6
