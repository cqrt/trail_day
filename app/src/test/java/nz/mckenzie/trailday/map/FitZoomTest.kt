package nz.mckenzie.trailday.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which level a framed box lands on.
 *
 * The fit itself comes from the map's own projection and needs a device to answer. The choice between
 * the box's level, a cap, and a level the walker chose does not, and it is the one rule here: the
 * recorder keeps the walker's zoom, one thing's own page keeps its cap, and every other map keeps the
 * fit.
 */
class FitZoomTest {

    @Test
    fun `a box nobody has an opinion about lands on its own level`() {
        assertEquals(11.4, fitZoom(fitted = 11.4), 0.0001)
    }

    @Test
    fun `a fit deeper than the cap stops at the cap`() {
        // One thing's own page asks for 15, because past that the screen is bare background.
        assertEquals(15.0, fitZoom(fitted = 17.0, cap = 15.0), 0.0001)
    }

    @Test
    fun `a fit already wider than the cap keeps the fit`() {
        assertEquals(12.0, fitZoom(fitted = 12.0, cap = 15.0), 0.0001)
    }

    @Test
    fun `the recorder's level outranks the box and its cap`() {
        // The recorder's case: a box around one phone, which says nothing about scale, and a level the
        // walker pinched to. A level chosen by hand is not capped by a rule about boxes.
        assertEquals(17.5, fitZoom(fitted = 11.4, cap = 15.0, kept = 17.5), 0.0001)
        assertEquals(9.0, fitZoom(fitted = 11.4, cap = 15.0, kept = 9.0), 0.0001)
    }
}
