package nz.mckenzie.trailday.ui

import androidx.compose.ui.graphics.Color
import nz.mckenzie.trailday.ui.theme.TrailDayPaper
import nz.mckenzie.trailday.ui.theme.onPaper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The paper the bar and the map's controls are drawn on.
 *
 * **20% see-through, 80% solid**, and the ink is not thinned with it: a label or a glyph keeps its own
 * colour at full strength, and only the surface behind it lets the ground through. That is the whole of
 * the rule, and it is one number - the tab bar and the four round controls read the same constant, so
 * they cannot drift a shade apart.
 */
class PaperTest {

    @Test
    fun `paper is four fifths solid`() {
        assertEquals(0.8f, TrailDayPaper.ALPHA, 0f)
    }

    @Test
    fun `thinning the paper leaves its colour alone`() {
        val surface = Color(0xFFFBFDF6)
        val paper = surface.onPaper()

        assertEquals("a fifth of the ground shows through", 0.8f, paper.alpha, 0.0001f)
        assertEquals("and the surface's own red is untouched", surface.red, paper.red, 0f)
        assertEquals("as is its green", surface.green, paper.green, 0f)
        assertEquals("and its blue", surface.blue, paper.blue, 0f)
    }
}
