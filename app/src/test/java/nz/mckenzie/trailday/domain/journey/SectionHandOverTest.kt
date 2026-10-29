package nz.mckenzie.trailday.domain.journey

import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Carrying on from one of DOC's sections to the next without the walker touching the phone.
 *
 * The two things that matter are **not going backwards** and **not handing over at the junction you have
 * only just arrived at**: the first is what makes the walk go somewhere, the second is what stops it
 * oscillating between two sections for ever.
 */
class SectionHandOverTest {

    /** A line running east, from [fromLng] to [toLng] at [lat]. */
    private fun line(lat: Double, fromLng: Double, toLng: Double, steps: Int = 10): List<GeoPoint> =
        (0..steps).map { step -> GeoPoint(lat = lat, lng = fromLng + (toLng - fromLng) * step / steps) }

    private val eastward = line(-45.0, 167.000, 167.010)

    @Test
    fun `the section that carries on is the next one`() {
        val next = SectionHandOver.next(
            line = eastward,
            at = eastward.last(),
            travelledM = 800.0,
            furthestFromStartM = 800.0,
            furthestFromEndM = 0.0,
            candidates = listOf(JourneyTrack("Second Section", listOf(line(-45.0, 167.010, 167.020)))),
            notThis = "First Section"
        )

        assertNotNull("the second section starts where the first ends", next)
        assertEquals("Second Section", next!!.name)
        assertEquals("and it starts at the junction", 167.010, next.line.first().lng, 0.0001)
        assertTrue("carrying on eastwards, so barely a turn", next.turnDegrees < 5.0)
    }

    @Test
    fun `a section that turns back is not the way on`() {
        // The spur runs back west from the junction: the walker is heading east.
        val backwards = JourneyTrack("Spur", listOf(line(-45.0, 167.010, 167.002)))

        val next = SectionHandOver.next(
            line = eastward,
            at = eastward.last(),
            travelledM = 800.0,
            furthestFromStartM = 800.0,
            furthestFromEndM = 0.0,
            candidates = listOf(backwards, JourneyTrack("Onwards", listOf(line(-45.0, 167.010, 167.020)))),
            notThis = "First Section"
        )

        assertEquals("the way on, not the way back", "Onwards", next?.name)
    }

    @Test
    fun `standing at the junction you have just reached does not hand over`() {
        val next = SectionHandOver.next(
            line = eastward,
            at = eastward.last(),
            travelledM = 800.0,
            furthestFromStartM = 0.0, // never got away from the start... but it is the end they are at
            furthestFromEndM = 0.0, // and they have only just arrived at the end
            candidates = listOf(JourneyTrack("Second Section", listOf(line(-45.0, 167.010, 167.020)))),
            notThis = "First Section"
        )

        assertNull("a section only just reached does not hand over", next)
    }

    @Test
    fun `the section being followed is never the one handed over to`() {
        val next = SectionHandOver.next(
            line = eastward,
            at = eastward.last(),
            travelledM = 800.0,
            furthestFromStartM = 800.0,
            furthestFromEndM = 0.0,
            candidates = listOf(JourneyTrack("First Section", listOf(eastward))),
            notThis = "First Section"
        )

        assertNull(next)
    }

    @Test
    fun `a section that does not reach the junction is not the way on`() {
        val next = SectionHandOver.next(
            line = eastward,
            at = eastward.last(),
            travelledM = 800.0,
            furthestFromStartM = 800.0,
            furthestFromEndM = 0.0,
            // Five hundred metres east: nothing to join at this junction.
            candidates = listOf(JourneyTrack("Far Away", listOf(line(-45.0, 167.0163, 167.0263)))),
            notThis = "First Section"
        )

        assertNull(next)
    }

    @Test
    fun `it can hand over at the start end when the walker is walking that way`() {
        // Walking west, back along the line: the section to the west carries on.
        val next = SectionHandOver.next(
            line = eastward,
            at = eastward.first(),
            travelledM = 40.0,
            furthestFromStartM = 0.0,
            furthestFromEndM = 800.0,
            candidates = listOf(JourneyTrack("Western Section", listOf(line(-45.0, 166.990, 167.000)))),
            notThis = "First Section"
        )

        assertNotNull("the western section starts where this one does", next)
        assertEquals("Western Section", next!!.name)
        assertEquals("and it is walked away from the junction", 166.990, next.line.last().lng, 0.0001)
    }

    @Test
    fun `a section stored the other way round is turned to start at the junction`() {
        // The candidate is published east to west, so its *last* point is the junction.
        val next = SectionHandOver.next(
            line = eastward,
            at = eastward.last(),
            travelledM = 800.0,
            furthestFromStartM = 800.0,
            furthestFromEndM = 0.0,
            candidates = listOf(JourneyTrack("Published Backwards", listOf(line(-45.0, 167.020, 167.010)))),
            notThis = "First Section"
        )

        assertNotNull(next)
        assertEquals("the line begins at the junction", 167.010, next!!.line.first().lng, 0.0001)
        assertEquals("and ends at the far end of the section", 167.020, next.line.last().lng, 0.0001)
    }
}
