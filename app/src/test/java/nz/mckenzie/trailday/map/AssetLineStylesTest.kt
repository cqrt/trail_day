package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.look.LineWidthChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which kind of line is drawn how.
 *
 * A dash pattern is in multiples of the line width, so these are shapes rather than measurements: a
 * solid line and a dash with a gap. What matters, and what is asserted here, is that the two are
 * recognisably different - two lines drawn the same way would make the map say less than it claims to -
 * and that the shape survives the walker changing the width.
 */
class AssetLineStylesTest {

    @Test
    fun `a track is drawn solid`() {
        widthChoices.forEach { width ->
            assertNull(AssetLineStyles.forKind(AssetKind.TRACK, LineWeight(width.stylePx)))
        }
    }

    @Test
    fun `a bike track is dashed, with a dash longer than its gap, at every width`() {
        widthChoices.forEach { width ->
            val bikeTrack = AssetLineStyles.forKind(AssetKind.BIKE_TRACK, LineWeight(width.stylePx))!!

            assertTrue(
                "a bike track's dash should read as a dash: ${bikeTrack.toList()}",
                bikeTrack[0] > bikeTrack[1]
            )
        }
    }

    @Test
    fun `the two lines are told apart by their dashes, and a place has no dash at all`() {
        val weight = LineWeight(LineWidthChoice.NORMAL.stylePx)
        val lines = AssetKind.entries.filter { it.shape == AssetShape.LINE }
            .map { AssetLineStyles.forKind(it, weight)?.toList() }

        assertEquals(
            "two lines sharing a dash would be indistinguishable: $lines",
            2,
            lines.toSet().size
        )
        AssetKind.entries.filter { it.shape == AssetShape.POINT }.forEach { place ->
            assertNull("$place is a marker, not a line", AssetLineStyles.forKind(place, weight))
        }
    }

    @Test
    fun `there are two kinds of line, and both are drawn`() {
        assertEquals(
            2,
            AssetKind.entries.filter { it.shape == AssetShape.LINE }.size
        )
    }

    private companion object {
        val widthChoices = LineWidthChoice.entries
    }
}
