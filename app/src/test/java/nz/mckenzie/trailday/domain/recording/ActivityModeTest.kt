package nz.mckenzie.trailday.domain.recording

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.geo.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one rule that decides what a recorded pass was.
 *
 * A trip is a walk or a ride, decided from the average its own page will print: **6.0 km/h and up is a
 * ride**. The cases below are the band either side of that number and the arithmetic that feeds it,
 * because the two ways this goes wrong are a walk called a ride and a ride called a walk - and both are
 * somebody reading their own trip back and finding it described as something they did not do.
 */
class ActivityModeTest {

    /**
     * The mode a pass covering [kmh] comes out as, over an hour of moving time.
     *
     * An hour because it makes the distance the speed with two more zeroes, so the number in the test
     * is the number on the page rather than something to work out.
     */
    private fun modeAt(kmh: Double): ActivityMode =
        ActivityMode.fromSpeed(distanceM = kmh * 1_000.0, movingMs = 3_600_000L)

    @Test
    fun `six kilometres an hour and up is a ride`() {
        assertEquals("standing still is a walk", ActivityMode.WALK, modeAt(0.0))
        assertEquals("a slow climb is a walk", ActivityMode.WALK, modeAt(2.9))
        assertEquals("three is still a walk", ActivityMode.WALK, modeAt(3.0))
        assertEquals("a brisk walk reads 5.9, so it is a walk", ActivityMode.WALK, modeAt(5.9))
        assertEquals("exactly six is a ride", ActivityMode.RIDE, modeAt(6.0))
        assertEquals("6.04 shows as 6.0, so it is a ride", ActivityMode.RIDE, modeAt(6.04))
        assertEquals("a bike on a track", ActivityMode.RIDE, modeAt(20.0))
        assertEquals("a fast descent", ActivityMode.RIDE, modeAt(60.0))
    }

    @Test
    fun `no distance or no moving time is a walk`() {
        assertEquals(
            "there is no average to compare",
            ActivityMode.WALK,
            ActivityMode.fromSpeed(distanceM = 0.0, movingMs = 3_600_000L)
        )
        assertEquals(
            "a distance with no time under it says nothing about speed",
            ActivityMode.WALK,
            ActivityMode.fromSpeed(distanceM = 12_000.0, movingMs = 0L)
        )
    }

    @Test
    fun `the comparison is on the shown figure, so 5 point 95 is a ride`() {
        // 5.95 prints as 6.0, and the rule is about what the page says: a trip whose page reads 6.0
        // while its tile says walk is the disagreement this rule exists to prevent.
        assertEquals(
            ActivityMode.RIDE,
            ActivityMode.fromSpeed(distanceM = 5_950.0, movingMs = 3_600_000L)
        )
    }

    @Test
    fun `a stored value reads back, and anything unknown is a walk`() {
        assertEquals(ActivityMode.RIDE, ActivityMode.ofStorage("RIDE"))
        assertEquals(ActivityMode.WALK, ActivityMode.ofStorage("WALK"))
        assertEquals("a value from a later build must not stop a page opening", ActivityMode.WALK, ActivityMode.ofStorage(null))
        assertEquals(ActivityMode.WALK, ActivityMode.ofStorage("SKI"))
        assertEquals(ActivityMode.WALK, ActivityMode.ofStorage(""))
    }

    @Test
    fun `a ride is a bike track and riding, and a walk is a track walked`() {
        assertEquals(AssetKind.BIKE_TRACK, ActivityMode.RIDE.assetKind)
        assertEquals(AssetKind.TRACK, ActivityMode.WALK.assetKind)
        assertEquals(TravelMode.RIDING, ActivityMode.RIDE.travelMode)
        assertEquals(TravelMode.WALKING, ActivityMode.WALK.travelMode)
    }

    @Test
    fun `a place's kind says which mode it is for`() {
        assertEquals(ActivityMode.RIDE, ActivityMode.of(AssetKind.BIKE_TRACK))
        assertEquals(ActivityMode.WALK, ActivityMode.of(AssetKind.TRACK))
        assertEquals("a spot is not a trip, and a page must still open", ActivityMode.WALK, ActivityMode.of(AssetKind.WAYPOINT))
    }

    @Test
    fun `the words a sentence uses for one and for several`() {
        assertEquals("walk", ActivityMode.WALK.one)
        assertEquals("walks", ActivityMode.WALK.many)
        assertEquals("ride", ActivityMode.RIDE.one)
        assertEquals("rides", ActivityMode.RIDE.many)
    }
}
