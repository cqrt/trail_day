package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.look.LineWidthChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How heavy a line of the work is, and what follows from the one number that sets it.
 *
 * A weight is now the walker's answer rather than a constant - three widths, in
 * [LineWeight][nz.mckenzie.trailday.domain.look.LineWidthChoice] - so these tests hold the *ratios*
 * rather than one number: whatever the walker picks, an edge is wider than the line it holds, the trail
 * stays the narrowest line, and a dash is still ten pixels of line and eight of gap.
 *
 * None of that is visible in a style document read by eye, and all of it is a walker's complaint once it
 * is on a phone: an edge that is not wider than its line hides it, and a trail as heavy as a track turns
 * the ground into a rival.
 */
class LineWeightTest {

    /** The three widths the app offers, as weights. */
    private val weights = LineWidthChoice.entries.map { LineWeight(it.stylePx) }

    @Test
    fun `normal is the three style pixels the map was thinned to`() {
        assertEquals(3f, LineWidthChoice.NORMAL.stylePx, 1e-6f)
        assertEquals(3f, LineWeight(LineWidthChoice.NORMAL.stylePx).stylePx, 1e-6f)
    }

    @Test
    fun `every line is worked out from the one width, so all three choices hold`() {
        weights.forEach { weight ->
            assertTrue(
                "an edge no wider than its line is hidden by it: ${weight.edge}",
                weight.edge > weight.stylePx
            )
            // Under two times the line: what shows of an edge is half the difference either side, and an
            // edge that doubled the white would read as a white line with a coloured thread in it.
            assertTrue(
                "an edge of ${weight.edge} against a line of ${weight.stylePx} is too much white",
                weight.edge < weight.stylePx * 2f
            )
            assertTrue("the trail must stay under a track: ${weight.trail}", weight.trail < weight.stylePx)
            assertTrue(
                "a followed line has to stand out from the work it is among: ${weight.followed}",
                weight.followed > weight.stylePx
            )
            assertTrue(
                "and the trail's own edge must be wider than the trail: ${weight.trailEdge}",
                weight.trailEdge > weight.trail
            )
        }
    }

    @Test
    fun `a thicker track is a thicker edge and a thicker trail with it`() {
        // The point of deriving the four others: a walker who thickens their tracks must not end up with
        // the same white edge under a heavier line, or the line loses the edge that lifts it off imagery.
        val thin = LineWeight(LineWidthChoice.THIN.stylePx)
        val thick = LineWeight(LineWidthChoice.THICK.stylePx)

        assertTrue(thick.edge > thin.edge)
        assertTrue(thick.trail > thin.trail)
        assertTrue(thick.followed > thin.followed)
    }

    @Test
    fun `a bike track's dash is ten pixels of line and eight of gap, whatever the line's width`() {
        // A dash array is in multiples of its own line's width, so the numbers have to be raised as the
        // line thins. This is that arithmetic, at each of the three widths.
        weights.forEach { weight ->
            val dash = weight.dashArray

            assertEquals("a ${weight.stylePx}-wide line's dash", LineWeight.DASH_PX, dash[0] * weight.stylePx, 1e-4f)
            assertEquals("a ${weight.stylePx}-wide line's gap", LineWeight.GAP_PX, dash[1] * weight.stylePx, 1e-4f)
        }
    }

    @Test
    fun `both maps are handed the same dash, at the same width`() {
        // The desk is built from the same functions the phone's map is, so a bike track dashes the same way
        // on both - and at the width the walker chose, because both ask the look.
        weights.forEach { weight ->
            val fromTheStyle = AssetLineStyles.forKind(AssetKind.BIKE_TRACK, weight)!!

            assertEquals(
                "the desk and the phone must dash a bike track the same way",
                weight.dashArray.toList(),
                fromTheStyle.toList()
            )
        }
    }
}
