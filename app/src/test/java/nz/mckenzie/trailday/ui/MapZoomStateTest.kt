package nz.mckenzie.trailday.ui

import nz.mckenzie.trailday.ui.screens.MapZoomState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a tap on a preview map's zoom buttons asks for.
 *
 * Two things matter and neither is the number the map ends up at: **the step goes the way the button
 * says**, and **every tap is a request of its own** - because the map answers a request once and ignores a
 * count it has already served, which is what stops a tab change or a basemap change zooming again for a tap
 * that is already done. A count that only changed when the direction did would make the second tap in a row
 * do nothing at all.
 */
class MapZoomStateTest {

    @Test
    fun `the buttons ask for a level in and a level out`() {
        val zoom = MapZoomState()

        zoom.zoomIn()
        assertEquals("in is closer, which is positive", 1.0, zoom.step, 0.0)

        zoom.zoomOut()
        assertEquals("and out is further", -1.0, zoom.step, 0.0)
    }

    @Test
    fun `every tap is a request of its own, even the same one twice`() {
        val zoom = MapZoomState()
        zoom.zoomIn()

        zoom.zoomIn()
        zoom.zoomOut()

        assertEquals("three taps, three requests", 3, zoom.count)
    }

    @Test
    fun `nothing is asked for until a button is tapped`() {
        val zoom = MapZoomState()

        assertEquals("no request", 0, zoom.count)
        assertEquals("and no step to take", 0.0, zoom.step, 0.0)
    }
}
