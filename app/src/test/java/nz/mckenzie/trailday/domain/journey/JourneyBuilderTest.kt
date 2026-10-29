package nz.mckenzie.trailday.domain.journey

import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Building a walk out of DOC's separate tracks.
 *
 * The shapes here are the ones the Kepler has: sections that touch end to end, a side trip that is not
 * the way through, a hole in DOC's line, and a first tap in the middle of a section rather than at its
 * end.
 */
class JourneyBuilderTest {

    private fun line(lat: Double, fromLng: Double, toLng: Double, steps: Int = 10): List<GeoPoint> =
        (0..steps).map { step ->
            GeoPoint(lat = lat, lng = fromLng + (toLng - fromLng) * step / steps)
        }

    /** Three sections end to end, and one spur off the middle of the walk. */
    private fun keplerLike(): List<JourneyTrack> = listOf(
        JourneyTrack("Kepler Track - One", listOf(line(-45.0, 167.000, 167.010))),
        JourneyTrack("Kepler Track - Two", listOf(line(-45.0, 167.010, 167.020))),
        JourneyTrack("Kepler Tk - Three", listOf(line(-45.0, 167.020, 167.030))),
        // The side trip to the summit: the same junction, ending nowhere useful.
        JourneyTrack("Kepler Tk - Hut To Summit", listOf(line(-45.0, 167.020, 167.020).map { it.copy(lat = -44.99) }))
    )

    private fun built(result: JourneyResult): JourneyPlan {
        assertTrue("expected a journey, was $result", result is JourneyResult.Built)
        return (result as JourneyResult.Built).plan
    }

    /** A point [metres] north of a place: a thumb's width, in the units the rule is written in. */
    private fun northOf(lat: Double, lng: Double, metres: Double) =
        GeoPoint(lat = lat + metres / 111_320.0, lng = lng)

    @Test
    fun `a tap beside the line lands on it, and one well off it does not`() {
        val tracks = listOf(JourneyTrack("Kepler Track - One", listOf(line(-45.0, 167.000, 167.010))))

        assertTrue(
            "on the line, of course",
            JourneyBuilder.isOnATrack(GeoPoint(lat = -45.0, lng = 167.005), tracks)
        )
        assertTrue(
            "200 m off is a thumb beside the line, and is taken as on it",
            JourneyBuilder.isOnATrack(northOf(-45.0, 167.005, 200.0), tracks)
        )
        assertFalse(
            "300 m off is a tap in the wrong place",
            JourneyBuilder.isOnATrack(northOf(-45.0, 167.005, 300.0), tracks)
        )
    }

    @Test
    fun `three sections end to end become one walk, named with the words they share`() {
        val plan = built(
            JourneyBuilder.build(
                picks = listOf(GeoPoint(-45.0, 167.0001), GeoPoint(-45.0, 167.0299)),
                tracks = keplerLike()
            )
        )

        // Three sections of 0.01 degrees, which at this latitude is about 787 m each.
        assertEquals("three sections", 3, plan.sections)
        assertTrue("about 2.3 km, was ${plan.lengthM}", plan.lengthM in 2_200.0..2_400.0)
        assertEquals("the words every section shares", "Kepler", plan.name)
        assertEquals("no holes to bridge", 0.0, plan.bridgeM, 0.001)
        assertTrue("a line with something in it", plan.line.size > 10)
    }

    @Test
    fun `the side trip is left out, because the way through is shorter without it`() {
        val withSummit = built(
            JourneyBuilder.build(
                picks = listOf(GeoPoint(-45.0, 167.0001), GeoPoint(-45.0, 167.0299)),
                tracks = keplerLike()
            )
        )

        // The spur runs north from the junction: no point of the walk may go north of -44.999.
        assertTrue(
            "the summit has been walked to and back: ${withSummit.lengthM} m",
            withSummit.lengthM < 2_400.0
        )
        assertTrue(
            "and the line stays on the walk",
            withSummit.line.all { it.lat > -45.001 && it.lat < -44.999 }
        )
    }

    @Test
    fun `a tap in the middle of a section starts the walk there`() {
        val whole = built(
            JourneyBuilder.build(
                picks = listOf(GeoPoint(-45.0, 167.000), GeoPoint(-45.0, 167.030)),
                tracks = keplerLike()
            )
        )
        val fromTheMiddle = built(
            JourneyBuilder.build(
                picks = listOf(GeoPoint(-45.0, 167.005), GeoPoint(-45.0, 167.030)),
                tracks = keplerLike()
            )
        )

        assertTrue(
            "cutting half a section off takes about 400 m off: ${whole.lengthM} against ${fromTheMiddle.lengthM}",
            whole.lengthM - fromTheMiddle.lengthM in 300.0..450.0
        )
        assertEquals("and it starts where the thumb was", -45.0, fromTheMiddle.line.first().lat, 0.0001)
        assertEquals(167.005, fromTheMiddle.line.first().lng, 0.0002)
    }

    @Test
    fun `a hole in the line is bridged, and said in the plan`() {
        // A, then a 500 m hole, then B and C touching: the walk must bridge the hole rather than jump
        // from A to C, and the bridge is drawn apart from the tracks either side of it.
        val holed = listOf(
            JourneyTrack("Gap Track - One", listOf(line(-45.0, 167.000, 167.010))),
            JourneyTrack("Gap Track - Two", listOf(line(-45.0, 167.0163, 167.0263))),
            JourneyTrack("Gap Track - Three", listOf(line(-45.0, 167.0263, 167.0363)))
        )

        val plan = built(
            JourneyBuilder.build(
                picks = listOf(GeoPoint(-45.0, 167.0001), GeoPoint(-45.0, 167.0362)),
                tracks = holed
            )
        )

        assertTrue("a bridge of about 500 m, was ${plan.bridgeM}", plan.bridgeM in 400.0..600.0)
        assertTrue("bridges are drawn apart", plan.parts.any { it.bridge })
        assertEquals("all three sections are walked", 3, plan.sections)
    }
    @Test
    fun `a tap on nothing is refused`() {
        val result = JourneyBuilder.build(
            picks = listOf(GeoPoint(-45.0, 167.0001), GeoPoint(-44.98, 167.02)),
            tracks = keplerLike()
        )

        assertEquals(JourneyResult.NotOnATrack, result)
    }

    @Test
    fun `two sections that do not lead to each other are refused`() {
        val apart = listOf(
            JourneyTrack("Away One", listOf(line(-45.0, 167.000, 167.010))),
            // Ten kilometres east: nothing joins them.
            JourneyTrack("Away Two", listOf(line(-45.0, 167.140, 167.150)))
        )

        val result = JourneyBuilder.build(
            picks = listOf(GeoPoint(-45.0, 167.0001), GeoPoint(-45.0, 167.1499)),
            tracks = apart
        )

        assertEquals(JourneyResult.NotConnected, result)
    }

    @Test
    fun `one tap is not a journey`() {
        assertEquals(
            JourneyResult.NotConnected,
            JourneyBuilder.build(listOf(GeoPoint(-45.0, 167.0)), keplerLike())
        )
    }

    @Test
    fun `the walk is walked in the order it was tapped`() {
        val plan = built(
            JourneyBuilder.build(
                picks = listOf(GeoPoint(-45.0, 167.0299), GeoPoint(-45.0, 167.0001)),
                tracks = keplerLike()
            )
        )

        assertTrue(
            "it starts at the eastern tap and ends at the western one",
            plan.line.first().lng > 167.028 && plan.line.last().lng < 167.002
        )
    }
}
