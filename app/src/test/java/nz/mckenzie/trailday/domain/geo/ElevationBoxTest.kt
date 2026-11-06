package nz.mckenzie.trailday.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The height a chart draws across.
 *
 * Two promises, and the second is the one that was wrong before this existed: a line with a hill in it
 * fills the box exactly as it always did, and a line with a ripple in it is drawn as a ripple rather
 * than stretched to look like a mountain.
 */
class ElevationBoxTest {

    /** A two-point line: [lowestM] at one end and [highestM] at the other, 1 km apart. */
    private fun line(lowestM: Double, highestM: Double): ElevationProfile =
        ElevationProfile.of(
            listOf(
                GeoPoint(lat = 0.0, lng = 0.0, altitudeM = lowestM),
                GeoPoint(lat = 0.009, lng = 0.0, altitudeM = highestM)
            )
        )!!

    @Test
    fun `a hill is drawn in its own box`() {
        val box = ElevationBox.of(line(500.0, 900.0))

        assertEquals(500.0, box.lowestM, 1e-9)
        assertEquals(900.0, box.highestM, 1e-9)
        assertEquals(400.0, box.spanM, 1e-9)
    }

    @Test
    fun `a hundred metres of ground is already a hill`() {
        val box = ElevationBox.of(line(500.0, 600.0))

        assertEquals("the box it always had", 500.0, box.lowestM, 1e-9)
        assertEquals(600.0, box.highestM, 1e-9)
    }

    @Test
    fun `a ripple is drawn small, in the middle of a hundred metres`() {
        val box = ElevationBox.of(line(500.0, 520.0))

        assertEquals("the middle of the measured range, less fifty", 460.0, box.lowestM, 1e-9)
        assertEquals("and fifty above it", 560.0, box.highestM, 1e-9)
        assertEquals(ElevationBox.MIN_SPAN_M, box.spanM, 1e-9)
    }

    @Test
    fun `a flat walk has air above and below rather than no height at all`() {
        val box = ElevationBox.of(line(50.0, 50.0))

        assertEquals(0.0, box.lowestM, 1e-9)
        assertEquals(100.0, box.highestM, 1e-9)
        assertEquals("and the line is drawn down the middle", 50.0, (box.lowestM + box.highestM) / 2, 1e-9)
    }

    @Test
    fun `the words beside the chart still say the measured range`() {
        // The box is a way of drawing; the card is the measurement. A 20 m ripple says 20 m however much
        // air the drawing puts round it.
        val profile = line(500.0, 520.0)

        assertEquals("≈500–520 m, up 20 m", ElevationPhrase.range(profile))
    }
}
