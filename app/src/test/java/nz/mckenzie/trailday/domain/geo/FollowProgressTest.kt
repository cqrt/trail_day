package nz.mckenzie.trailday.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the walker is against the line they are following.
 *
 * Four numbers come from one nearest point, and what is held still here is that they agree with each other:
 * the distance off, the distance along, what is left, and the climbing left after that point. The awkward
 * cases are the ends - a walker past the line has nothing left, not a negative amount - and a line with
 * heights, where the climb already behind them must not be counted again.
 */
class FollowProgressTest {

    /** A straight line due north: 0.1 degrees of latitude, which is about 11.1 km. */
    private val flatLine = listOf(
        GeoPoint(lat = -46.6000, lng = 168.3000),
        GeoPoint(lat = -46.5000, lng = 168.3000)
    )

    /** The same line, climbing 500 m from its start to its end. */
    private val climbingLine = listOf(
        GeoPoint(lat = -46.6000, lng = 168.3000, altitudeM = 100.0),
        GeoPoint(lat = -46.5500, lng = 168.3000, altitudeM = 350.0),
        GeoPoint(lat = -46.5000, lng = 168.3000, altitudeM = 600.0)
    )

    @Test
    fun `standing on the middle of the line is half way along it and not off it`() {
        val progress = FollowProgress.of(flatLine, GeoPoint(lat = -46.5500, lng = 168.3000))!!

        assertEquals("on the line", 0.0, progress.offM, 0.5)
        assertEquals("half way along an 11.1 km line", 5_559.8, progress.alongM, 5.0)
        assertEquals("with the other half left", 5_559.8, progress.remainingM, 5.0)
        assertEquals("and the two add up to the whole line", 11_119.5, progress.totalM, 5.0)
        assertEquals("half of it behind them", 0.5, progress.fraction, 0.001)
    }

    @Test
    fun `beside the line, the distance off it is measured to the line and not to a vertex`() {
        // About 77 m east of the line's middle: the middle of a long leg, where a vertex is kilometres away.
        val beside = GeoPoint(lat = -46.5500, lng = 168.3011)

        val progress = FollowProgress.of(flatLine, beside)!!

        assertEquals("the perpendicular distance, not the distance to the nearest end", 84.0, progress.offM, 8.0)
        assertEquals("and still half way along", 5_559.8, progress.alongM, 30.0)
    }

    @Test
    fun `past the end of the line there is nothing left, and before it there is all of it`() {
        val past = FollowProgress.of(flatLine, GeoPoint(lat = -46.4900, lng = 168.3000))!!
        val before = FollowProgress.of(flatLine, GeoPoint(lat = -46.6100, lng = 168.3000))!!

        assertEquals("nothing left", 0.0, past.remainingM, 0.5)
        assertEquals("all of it behind them", 1.0, past.fraction, 0.0001)
        assertEquals("and the walker is off the end, which is a distance", 1_111.0, past.offM, 5.0)

        assertEquals("nothing behind them", 0.0, before.alongM, 0.5)
        assertEquals("and all of it ahead", 11_119.5, before.remainingM, 5.0)
    }

    @Test
    fun `the climbing left is the climbing after the walker, not the climbing of the whole line`() {
        val atStart = FollowProgress.of(climbingLine, GeoPoint(-46.6000, 168.3000))!!
        val halfway = FollowProgress.of(climbingLine, GeoPoint(-46.5500, 168.3000))!!
        val atEnd = FollowProgress.of(climbingLine, GeoPoint(-46.5000, 168.3000))!!

        assertEquals("the whole 500 m is ahead at the start", 500.0, atStart.remainingClimbM, 0.5)
        assertEquals("the 250 m to the top is ahead half way", 250.0, halfway.remainingClimbM, 0.5)
        assertEquals("and none of it is left at the end", 0.0, atEnd.remainingClimbM, 0.5)
    }

    @Test
    fun `a line with no heights says no climbing rather than zero metres of it`() {
        val progress = FollowProgress.of(flatLine, GeoPoint(-46.5500, 168.3000))!!

        assertEquals("nothing measured, nothing promised", 0.0, progress.remainingClimbM, 0.0)
    }

    @Test
    fun `the time left is the one rule for time, over what is left`() {
        val halfway = FollowProgress.of(flatLine, GeoPoint(-46.5500, 168.3000))!!
        val climbingHalfway = FollowProgress.of(climbingLine, GeoPoint(-46.5500, 168.3000))!!

        // 5.556 km on the flat at 5 km/h is 66.7 minutes, rounded up to the five-minute grid: 70.
        assertEquals(70 * 60L, halfway.toGoSeconds(TravelMode.WALKING))
        // The same distance with 250 m to climb adds 25 minutes: 91.7, rounded up to 95.
        assertEquals(95 * 60L, climbingHalfway.toGoSeconds(TravelMode.WALKING))
    }

    @Test
    fun `nothing to be against is no progress at all`() {
        assertNull(FollowProgress.of(emptyList(), GeoPoint(-46.55, 168.3)))
        assertNull(FollowProgress.of(listOf(GeoPoint(-46.6, 168.3)), GeoPoint(-46.55, 168.3)))
    }

    @Test
    fun `the fraction of the line is between nothing and all of it, whatever the arithmetic says`() {
        val progress = FollowProgress.of(flatLine, GeoPoint(-46.5500, 168.3000))!!

        assertTrue(progress.fraction in 0.0..1.0)
    }
}
