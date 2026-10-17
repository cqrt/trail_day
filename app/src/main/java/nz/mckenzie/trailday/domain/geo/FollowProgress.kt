package nz.mckenzie.trailday.domain.geo

/**
 * Where the walker is against the line they are following.
 *
 * The four things a person wants to know while following a track, and nothing else: **how far off it** they
 * are, **how far along** it they have got, **how much is left**, and **how long that will take**. All four
 * come from the same nearest point, so they cannot disagree with each other - a readout that said "2.4 km
 * along" and "3 km to go" about a 5 km line would be arithmetic with two ideas of where the walker is.
 */
data class FollowProgress(
    /** How far the walker is from the line itself: zero when they are standing on it. */
    val offM: Double,
    /** How far along the line the nearest point is, measured from the line's start. */
    val alongM: Double,
    /** How much of the line is left after that point. */
    val remainingM: Double,
    /**
     * How much climbing is left after that point, from the line's own heights.
     *
     * Zero when the line has no heights - a line brought in from a file that gave them has them, one
     * dropped on a map has not - because a climb nobody measured is not a climb to promise.
     */
    val remainingClimbM: Double
) {

    /** The line's whole length, as the walker's own position divides it. */
    val totalM: Double get() = alongM + remainingM

    /** How much of the line is behind them, from nothing to all of it. */
    val fraction: Double get() = if (totalM <= 0.0) 0.0 else (alongM / totalM).coerceIn(0.0, 1.0)

    /** How long what is left takes, by the one rule the whole app estimates time with. */
    fun toGoSeconds(mode: TravelMode): Long = TravelTime.seconds(remainingM, remainingClimbM, mode)

    companion object {

        /**
         * Where [position] is against [line], or null when there is no line to be against.
         *
         * The nearest point is taken over each segment and the distance measured to it, so a walker beside
         * the middle of a long straight stretch is measured against that stretch rather than against
         * whichever vertex happens to be nearest - which is what makes "how far off" honest on a track that
         * is mostly long legs.
         *
         * **A walker past the end of the line is at the end of it**, 0 m to go and as far off as they are:
         * the arithmetic clamps there rather than running off the end into a negative remainder.
         */
        fun of(line: List<GeoPoint>, position: GeoPoint): FollowProgress? {
            if (line.size < 2) return null

            var travelled = 0.0
            var nearestOffM = Double.MAX_VALUE
            var nearestAlongM = 0.0

            for (index in 1 until line.size) {
                val start = line[index - 1]
                val end = line[index]
                val nearest = nearestPointOnSegment(position, start, end)
                val offM = haversineMeters(position.lat, position.lng, nearest.lat, nearest.lng)
                if (offM < nearestOffM) {
                    nearestOffM = offM
                    nearestAlongM = travelled + haversineMeters(start.lat, start.lng, nearest.lat, nearest.lng)
                }
                travelled += haversineMeters(start.lat, start.lng, end.lat, end.lng)
            }

            return FollowProgress(
                offM = nearestOffM,
                alongM = nearestAlongM,
                remainingM = (travelled - nearestAlongM).coerceAtLeast(0.0),
                remainingClimbM = climbAfter(line, nearestAlongM)
            )
        }

        /**
         * The climbing left after [fromM], from the line's heights.
         *
         * Read off the height profile rather than the raw points, because the profile is already how the
         * app reads a line's heights (a missing height is a step rather than a break), and a second reading
         * of the same points is how two screens come to disagree about a hill. A rise that straddles
         * [fromM] is counted whole - the app knows how much the ground rose there, not where along the gap
         * it did - and a rise that ends exactly at the walker is behind them rather than ahead.
         */
        private fun climbAfter(line: List<GeoPoint>, fromM: Double): Double {
            val profile = ElevationProfile.of(line) ?: return 0.0
            var climb = 0.0
            var previous: Double? = null
            for (sample in profile.samples) {
                val last = previous
                if (last != null && sample.alongM > fromM) {
                    val rise = sample.heightM - last
                    if (rise > 0.0) climb += rise
                }
                previous = sample.heightM
            }
            return climb
        }
    }
}
