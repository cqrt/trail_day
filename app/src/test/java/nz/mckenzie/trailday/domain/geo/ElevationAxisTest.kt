package nz.mckenzie.trailday.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers along the axes of a height chart.
 *
 * The two things a label has to be: a round number, and far enough from its neighbour to read. Both are
 * held here rather than judged on a screen, because the same rule labels 12 km of walk and 900 m of it.
 */
class ElevationAxisTest {

    @Test
    fun `a hundred and eighty metres of box is labelled every fifty`() {
        // The Kiwi Burn box: 502 to 682, so the first round number inside it is 550.
        val ticks = ElevationAxis.ticks(low = 501.95, high = 681.95, target = 4)

        assertEquals(listOf(550.0, 600.0, 650.0), ticks)
    }

    @Test
    fun `twelve kilometres of walk is labelled every two`() {
        val ticks = ElevationAxis.ticks(low = 0.0, high = 12_244.0, target = 7)

        assertEquals(7, ticks.size)
        assertEquals(listOf(0.0, 2_000.0, 4_000.0, 6_000.0, 8_000.0, 10_000.0, 12_000.0), ticks)
    }

    @Test
    fun `a shorter walk is labelled in smaller round numbers`() {
        // 963 m: two hundred metre steps, which is what a card 300 dp wide can hold.
        val ticks = ElevationAxis.ticks(low = 0.0, high = 963.0, target = 7)

        assertEquals(listOf(0.0, 200.0, 400.0, 600.0, 800.0), ticks)
    }

    @Test
    fun `a mountain box is labelled in round hundreds`() {
        val ticks = ElevationAxis.ticks(low = 800.0, high = 2_400.0, target = 4)

        assertEquals(listOf(1_000.0, 1_500.0, 2_000.0), ticks)
    }

    @Test
    fun `no ticks rather than a division by nothing`() {
        assertTrue(ElevationAxis.ticks(low = 100.0, high = 100.0, target = 4).isEmpty())
        assertTrue(ElevationAxis.ticks(low = 0.0, high = 0.0, target = 4).isEmpty())
        assertTrue(ElevationAxis.ticks(low = 0.0, high = 100.0, target = 0).isEmpty())
    }

    @Test
    fun `every tick is inside the range it was asked about`() {
        val ticks = ElevationAxis.ticks(low = 517.3, high = 1_004.2, target = 4)

        assertTrue(ticks.isNotEmpty())
        ticks.forEach { tick ->
            assertTrue("$tick is inside 517.3..1004.2", tick >= 517.3 && tick <= 1_004.2)
        }
    }

    @Test
    fun `the box is raised to the next label when the peak is above the last one`() {
        // Kiwi Burn: 520 to 664 in a 180 m box, which is 502 to 682. The labels are 550, 600 and 650,
        // and the peak is above the last of them - so the box is raised to 700 and the peak has a number
        // over it.
        val axis = ElevationAxis.heightAxis(
            ElevationProfile.of(
                listOf(
                    GeoPoint(0.0, 0.0, altitudeM = 520.0),
                    GeoPoint(0.009, 0.0, altitudeM = 664.0)
                )
            )!!
        )

        assertEquals(listOf(550.0, 600.0, 650.0, 700.0), axis.ticks)
        assertEquals("the box's top is the label over the peak", 700.0, axis.box.highestM, 1e-9)
        assertEquals("and its foot is the floor's own", 502.0, axis.box.lowestM, 1e-9)
    }

    @Test
    fun `a peak already under a label leaves the box alone`() {
        // 500 to 640 in a 180 m box is 480 to 660, labelled 500, 550, 600 and 650: the peak is below the
        // top label, so there is nothing to cover and the box keeps the height it had.
        val axis = ElevationAxis.heightAxis(
            ElevationProfile.of(
                listOf(
                    GeoPoint(0.0, 0.0, altitudeM = 500.0),
                    GeoPoint(0.009, 0.0, altitudeM = 640.0)
                )
            )!!
        )

        assertEquals(listOf(500.0, 550.0, 600.0, 650.0), axis.ticks)
        assertEquals(480.0, axis.box.lowestM, 1e-9)
        assertEquals(660.0, axis.box.highestM, 1e-9)
    }

    @Test
    fun `a long climb is covered by the next round label`() {
        // 0 to 980 m: a 980 m box labelled 0 and 500, with the peak at 980 past the last of them, so the
        // box is raised to 1,000 and gets a label there.
        val axis = ElevationAxis.heightAxis(
            ElevationProfile.of(
                listOf(
                    GeoPoint(0.0, 0.0, altitudeM = 0.0),
                    GeoPoint(0.009, 0.0, altitudeM = 980.0)
                )
            )!!
        )

        assertEquals(listOf(0.0, 500.0, 1_000.0), axis.ticks)
        assertEquals(1_000.0, axis.box.highestM, 1e-9)
    }
}
