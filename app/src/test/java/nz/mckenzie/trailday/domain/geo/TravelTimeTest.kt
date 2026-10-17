package nz.mckenzie.trailday.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How long a stretch of ground takes.
 *
 * The arithmetic is Naismith's rule, and what is pinned here is that it is *that* rule for both ways of
 * moving - a flat speed plus an hour per 600 m climbed - and that the answer is coarse enough to read as
 * the estimate it is. Every screen that says "about an hour" gets its answer from here, so a change to the
 * rule is a change to all of them at once.
 */
class TravelTimeTest {

    private fun walking(distanceM: Double, climbM: Double = 0.0) =
        TravelTime.seconds(distanceM, climbM, TravelMode.WALKING)

    private fun riding(distanceM: Double, climbM: Double = 0.0) =
        TravelTime.seconds(distanceM, climbM, TravelMode.RIDING)

    @Test
    fun `five flat kilometres is an hour on foot`() {
        assertEquals(60 * 60L, walking(5_000.0))
    }

    @Test
    fun `an hour per six hundred metres on top of the flat - a five kilometre climb of six hundred is two hours`() {
        assertEquals(2 * 60 * 60L, walking(5_000.0, climbM = 600.0))
        assertEquals("and the same five kilometres with no climb is an hour", 60 * 60L, walking(5_000.0))
    }

    @Test
    fun `riding is faster on the flat and slower up, and the same rule for both`() {
        assertEquals("twelve flat kilometres is an hour on a bike", 60 * 60L, riding(12_000.0))
        assertEquals(
            "and four hundred and fifty metres up is another",
            2 * 60 * 60L,
            riding(12_000.0, climbM = 450.0)
        )
    }

    @Test
    fun `a descent does not make the walk quicker`() {
        // The rule counts climbing and not descending, deliberately: on this ground the way down is often
        // as slow as the way up, and a descent term would look precise about something nobody agrees on.
        assertEquals(walking(5_000.0), walking(5_000.0, climbM = -400.0))
    }

    @Test
    fun `past twenty minutes the answer is rounded up to five, and never down`() {
        // 5.1 km on the flat is 61.2 minutes: too precise for a rule of thumb, so it reads 65.
        assertEquals(65 * 60L, walking(5_100.0))
        // 19 minutes is left alone: under twenty, the minute is worth keeping.
        assertEquals(19 * 60L, walking(1_583.0))
    }

    @Test
    fun `a minute is the shortest estimate there is, and no ground is no time`() {
        assertEquals("one metre still takes a minute", 60L, walking(1.0))
        assertEquals(0L, walking(0.0))
        assertEquals(0L, riding(0.0))
    }

    @Test
    fun `the two modes say what they are, so a screen can print which one it meant`() {
        assertEquals("on foot", TravelMode.WALKING.phrase)
        assertEquals("riding", TravelMode.RIDING.phrase)
    }
}
