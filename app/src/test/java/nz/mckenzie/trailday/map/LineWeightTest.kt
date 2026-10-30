package nz.mckenzie.trailday.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How heavy the lines of the work are, and what follows from the one number that sets them.
 *
 * The widths used to be five numbers set by hand, and these tests are the reason they are not: an edge that
 * is not wider than its line hides it, a trail that is not narrower than a track turns the ground into a
 * rival, and a dash that shrinks with a thinning line stops being a dash. None of that is visible in a
 * style document read by eye, and all of it is a walker's complaint once it is on a phone.
 */
class LineWeightTest {

    @Test
    fun `a track is three style pixels, which is what the map was thinned to`() {
        assertEquals(3f, LineWeight.TRACK, 1e-6f)
    }

    @Test
    fun `an edge is wider than the line it holds, and not by a line's own width`() {
        assertTrue(
            "an edge no wider than its line is hidden by it: ${LineWeight.EDGE}",
            LineWeight.EDGE > LineWeight.TRACK
        )
        // Under two times the line: what shows of an edge is half the difference either side, and an edge
        // that doubled the white would read as a white line with a coloured thread in it.
        assertTrue(
            "an edge of ${LineWeight.EDGE} against a line of ${LineWeight.TRACK} is too much white",
            LineWeight.EDGE < LineWeight.TRACK * 2f
        )
    }

    @Test
    fun `the trail is the narrowest line and the followed line the widest`() {
        assertTrue(
            "the trail must stay under a track: ${LineWeight.TRAIL}",
            LineWeight.TRAIL < LineWeight.TRACK
        )
        assertTrue(
            "a followed line has to stand out from the work it is among: ${LineWeight.FOLLOWED}",
            LineWeight.FOLLOWED > LineWeight.TRACK
        )
        assertTrue(
            "and the trail's own edge must be wider than the trail: ${LineWeight.TRAIL_EDGE}",
            LineWeight.TRAIL_EDGE > LineWeight.TRAIL
        )
    }

    @Test
    fun `a bike track's dash is ten pixels of line and eight of gap, whatever the line's width`() {
        // A dash array is in multiples of its own line's width, so the numbers have to be raised as the
        // line thins. This is that arithmetic, at the width the map draws and at one it does not.
        listOf(LineWeight.TRACK, 5f, 8f).forEach { width ->
            val dash = LineWeight.dashArray(width)

            assertEquals("a ${width}-wide line's dash", LineWeight.DASH_PX, dash[0] * width, 1e-4f)
            assertEquals("a ${width}-wide line's gap", LineWeight.GAP_PX, dash[1] * width, 1e-4f)
        }
    }

    @Test
    fun `both maps are handed the same dash`() {
        val fromTheStyle = AssetLineStyles.forKind(
            nz.mckenzie.trailday.domain.asset.AssetKind.BIKE_TRACK
        )!!

        assertEquals(
            "the desk and the phone must dash a bike track the same way",
            LineWeight.dashArray().toList(),
            fromTheStyle.toList()
        )
    }
}
