package nz.mckenzie.trailday.domain.journey

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.reference.Followable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Carrying on from one section to the next without the walker touching the phone.
 *
 * The two things that matter are **not going backwards** and **not handing over at the junction you have
 * only just arrived at**: the first is what makes the walk go somewhere, the second is what stops it
 * oscillating between two sections for ever.
 *
 * **A walk published in pieces is one track**, so the piece being walked is left out by its line rather
 * than its name - the pieces share the name, and leaving out the name would leave out the way on.
 */
class SectionHandOverTest {

    /** A published line: the walker's own tracks and the dataset's look the same to the rule. */
    private class Published(
        override val name: String,
        override val paths: List<List<GeoPoint>>
    ) : Followable {
        override val sourceRef: String get() = "ref:$name"
    }

    /** A line running east, from [fromLng] to [toLng] at [lat]. */
    private fun line(lat: Double, fromLng: Double, toLng: Double, steps: Int = 10): List<GeoPoint> =
        (0..steps).map { step -> GeoPoint(lat = lat, lng = fromLng + (toLng - fromLng) * step / steps) }

    private val eastward = line(-45.0, 167.000, 167.010)

    @Test
    fun `the section that carries on is the next one`() {
        val second = Published("Second Section", listOf(line(-45.0, 167.010, 167.020)))

        val next = SectionHandOver.next(
            line = eastward,
            at = eastward.last(),
            travelledM = 800.0,
            furthestFromStartM = 800.0,
            furthestFromEndM = 0.0,
            candidates = listOf(second),
            walked = eastward
        )

        assertNotNull("the second section starts where the first ends", next)
        assertSame("and it is that publisher's track", second, next!!.track)
        assertEquals("and it starts at the junction", 167.010, next.line.first().lng, 0.0001)
        assertTrue("carrying on eastwards, so barely a turn", next.turnDegrees < 5.0)
    }

    @Test
    fun `a section that turns back is not the way on`() {
        // The spur runs back west from the junction: the walker is heading east.
        val backwards = Published("Spur", listOf(line(-45.0, 167.010, 167.002)))
        val onwards = Published("Onwards", listOf(line(-45.0, 167.010, 167.020)))

        val next = SectionHandOver.next(
            line = eastward,
            at = eastward.last(),
            travelledM = 800.0,
            furthestFromStartM = 800.0,
            furthestFromEndM = 0.0,
            candidates = listOf(backwards, onwards),
            walked = eastward
        )

        assertSame("the way on, not the way back", onwards, next?.track)
    }

    @Test
    fun `standing at the junction you have just reached does not hand over`() {
        val next = SectionHandOver.next(
            line = eastward,
            at = eastward.last(),
            travelledM = 800.0,
            furthestFromStartM = 0.0, // never got away from the start... but it is the end they are at
            furthestFromEndM = 0.0, // and they have only just arrived at the end
            candidates = listOf(Published("Second Section", listOf(line(-45.0, 167.010, 167.020)))),
            walked = eastward
        )

        assertNull("a section only just reached does not hand over", next)
    }

    @Test
    fun `the piece being walked is never the one handed over to`() {
        // The same ground, and it is the piece underfoot: a junction has two ends, and one of them is
        // where the walker came from.
        val next = SectionHandOver.next(
            line = eastward,
            at = eastward.last(),
            travelledM = 800.0,
            furthestFromStartM = 800.0,
            furthestFromEndM = 0.0,
            candidates = listOf(Published("First Section", listOf(eastward))),
            walked = eastward
        )

        assertNull(next)
    }

    @Test
    fun `the piece being walked is left out however it is stored`() {
        // The target was turned to start at the junction, so the published piece runs the other way.
        val next = SectionHandOver.next(
            line = eastward,
            at = eastward.last(),
            travelledM = 800.0,
            furthestFromStartM = 800.0,
            furthestFromEndM = 0.0,
            candidates = listOf(Published("First Section", listOf(eastward.reversed()))),
            walked = eastward
        )

        assertNull(next)
    }

    @Test
    fun `a walk in pieces carries on from one piece to the next`() {
        // One track, published in two pieces that meet: the walker walks the first and is handed the
        // second, which is a piece of the same track rather than a track of its own.
        val walkway = Published(
            "Kingswell Creek Walkway",
            listOf(line(-45.0, 167.000, 167.010), line(-45.0, 167.010, 167.020))
        )

        val next = SectionHandOver.next(
            line = eastward,
            at = eastward.last(),
            travelledM = 800.0,
            furthestFromStartM = 800.0,
            furthestFromEndM = 0.0,
            candidates = listOf(walkway),
            walked = eastward
        )

        assertNotNull("the next piece starts where this one ends", next)
        assertSame("and it is still the same walkway", walkway, next!!.track)
        assertTrue("the walker is nowhere near the end of this piece yet", next.line.last().lng > 167.015)
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
            candidates = listOf(Published("Far Away", listOf(line(-45.0, 167.0163, 167.0263)))),
            walked = eastward
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
            candidates = listOf(Published("Western Section", listOf(line(-45.0, 166.990, 167.000)))),
            walked = eastward
        )

        assertNotNull("the western section starts where this one does", next)
        assertEquals("and it is walked away from the junction", 166.990, next!!.line.last().lng, 0.0001)
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
            candidates = listOf(Published("Published Backwards", listOf(line(-45.0, 167.020, 167.010)))),
            walked = eastward
        )

        assertNotNull(next)
        assertEquals("the line begins at the junction", 167.010, next!!.line.first().lng, 0.0001)
        assertEquals("and ends at the far end of the section", 167.020, next.line.last().lng, 0.0001)
    }
}
