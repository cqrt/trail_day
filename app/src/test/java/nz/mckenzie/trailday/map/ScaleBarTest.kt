package nz.mckenzie.trailday.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the scale bar says, and how long it draws itself.
 *
 * The measurement underneath - how far a pixel covers - comes from the map's own projection and needs a
 * device to answer. The arithmetic that turns that measurement into a bar does not, and it is here.
 */
class ScaleBarTest {

    @Test
    fun `the longest distance that fits the room is the one shown`() {
        // Two kilometres across two hundred pixels: 2 km is the whole room, and 5 km would need 500.
        val span = ScaleBar.span(metresAcross = 2000.0, maxWidthPx = 200f)

        assertEquals(2000.0, span!!.metres, 0.0001)
        assertEquals(200f, span.widthPx, 0.01f)
    }

    @Test
    fun `a distance that does not fill the room is drawn to scale`() {
        // 900 m across 200 px: the largest round distance that fits is 500 m, which is 111 px of it.
        val span = ScaleBar.span(metresAcross = 900.0, maxWidthPx = 200f)

        assertEquals(500.0, span!!.metres, 0.0001)
        assertEquals((200f * 500.0 / 900.0).toFloat(), span.widthPx, 0.01f)
    }

    @Test
    fun `the bar is never wider than the room it was given, and never claims more ground than it has`() {
        var across = 0.6
        while (across < 40_000_000.0) {
            val span = ScaleBar.span(across, 240f)!!

            assertTrue("across=$across width=${span.widthPx}", span.widthPx <= 240.01f)
            assertTrue("across=$across metres=${span.metres}", span.metres <= across)
            across *= 1.7
        }
    }

    @Test
    fun `nothing is drawn when there is nothing to measure`() {
        assertNull(ScaleBar.span(metresAcross = 0.0, maxWidthPx = 240f))
        assertNull(ScaleBar.span(metresAcross = -5.0, maxWidthPx = 240f))
        assertNull(ScaleBar.span(metresAcross = Double.NaN, maxWidthPx = 240f))
        assertNull(ScaleBar.span(metresAcross = 1000.0, maxWidthPx = 0f))
    }

    @Test
    fun `the finest ground the bar will show is half a metre`() {
        assertNull(ScaleBar.span(metresAcross = 0.4, maxWidthPx = 240f))
        assertEquals(0.5, ScaleBar.span(metresAcross = 0.6, maxWidthPx = 240f)!!.metres, 0.0001)
    }

    @Test
    fun `the label is written in the unit a walker reads a distance in`() {
        assertEquals("500 m", ScaleBar.label(500.0))
        assertEquals("1 km", ScaleBar.label(1000.0))
        assertEquals("2 km", ScaleBar.label(2000.0))
        assertEquals("20 km", ScaleBar.label(20_000.0))
    }

    @Test
    fun `a bar finer than a metre is written in centimetres rather than rounded away to nothing`() {
        assertEquals("50 cm", ScaleBar.label(0.5))
    }
}
