package nz.mckenzie.trailday.domain.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Heading for a point rather than following a line: which way it is, how far, and how long on foot.
 *
 * A hut, a campsite, a spot of the walker's own - the other half of what the recorder is asked while a walk
 * is being made. The questions are different from a line's: there is no "how far along" to a point, and no
 * off-distance, only **which way** and **how far**.
 *
 * The bearing is the **initial great-circle bearing**, as a compass knows it - the direction to set off in,
 * which is what a person walking towards something needs. On the distances this app is used over it barely
 * differs from the final bearing, and the difference is not worth a second number to read.
 */
data class FollowPoint(
    /** Which way, in degrees clockwise from north: 0 is north, 90 is east. */
    val bearingDegrees: Double,
    /** How far away it is, in metres, on the ground. */
    val distanceM: Double
) {

    /**
     * Which way, in the app's own words: "north", "north-east", "east" and so on.
     *
     * Eight points rather than sixteen: a walker pointing at a hut does not need to be told
     * north-north-east, and the extra words are harder to read at a glance than the truth is worth.
     */
    val compass: String
        get() {
            // Half a sector is added before dividing, so north is 337.5 to 22.5 degrees rather than 0 to
            // 45: a bearing of 30 degrees is more north-east than north, and should say so.
            val sector = ((((bearingDegrees % 360.0) + 360.0) % 360.0 + 22.5) / 45.0).toInt() % 8
            return COMPASS[sector]
        }

    /** How long getting there takes on foot, over the straight line and with no climb known. */
    fun toGoSeconds(mode: TravelMode = TravelMode.WALKING): Long =
        TravelTime.seconds(distanceM, climbM = 0.0, mode)

    companion object {

        /**
         * Where [to] is from [from], or null when the two are the same place and there is no bearing to give.
         *
         * A walker standing on the spot is told nothing rather than told "0 m north", which would be a
         * direction made up out of rounding.
         */
        fun of(from: GeoPoint, to: GeoPoint): FollowPoint? {
            val distance = haversineMeters(from.lat, from.lng, to.lat, to.lng)
            if (distance < 1.0) return null

            val fromLat = Math.toRadians(from.lat)
            val toLat = Math.toRadians(to.lat)
            val dLng = Math.toRadians(to.lng - from.lng)
            val y = sin(dLng) * cos(toLat)
            val x = cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(dLng)
            val bearing = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0

            return FollowPoint(bearingDegrees = bearing, distanceM = distance)
        }

        /** The eight points of the compass, clockwise from north. */
        private val COMPASS = listOf(
            "north",
            "north-east",
            "east",
            "south-east",
            "south",
            "south-west",
            "west",
            "north-west"
        )
    }
}
