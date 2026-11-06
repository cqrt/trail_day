package nz.mckenzie.trailday.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The shape a height chart draws, held against the ground it came from.
 *
 * These are the cases that decide whether a line reads as a walk: noise either side of a flat run
 * averages back to the run, a real climb survives, and the ends stay where the walker stood. The
 * thinning is held too, because a chart that dropped a spike would be a shape nobody could trust.
 */
class ElevationShapeTest {

    /** A line sampled every [stepM] metres, with a height for each sample. */
    private fun line(count: Int, stepM: Double, height: (Int) -> Double) =
        List(count) { index -> ElevationSample(alongM = index * stepM, heightM = height(index)) }

    @Test
    fun `noise either side of a flat run averages back to the run`() {
        // The sawtooth is the point: 95 m and 105 m alternate every 3 m, so the ground is 100 m and
        // the measured heights are never nearer than 5 m of it. Anything within a couple of metres
        // in the middle is the window doing its work.
        val noisy = line(401, 3.0) { index -> if (index % 2 == 0) 95.0 else 105.0 }

        val shape = ElevationShape.of(noisy)

        assertTrue("the middle is the ground: ${shape[200].heightM}", abs(shape[200].heightM - 100.0) <= 2.0)
    }

    @Test
    fun `a real climb survives the window`() {
        // A 100 m climb over 12 km, sampled every 30 m: a walking gradient. At 200 m the window
        // shifts the one-sided mean at the start by a quarter of a metre, and the interior is exact.
        val climbing = line(401, 30.0) { index -> 100.0 + index * 0.25 }

        val shape = ElevationShape.of(climbing)

        for (index in 1 until shape.size - 1) {
            val measured = climbing[index].heightM
            assertEquals(
                "sample $index, at ${climbing[index].alongM} m",
                measured,
                shape[index].heightM,
                3.0
            )
        }
        assertEquals(100.0, shape.first().heightM, 1e-9)
        assertEquals(200.0, shape.last().heightM, 1e-9)
    }

    @Test
    fun `the line starts and ends on the measured heights`() {
        val walked = listOf(
            ElevationSample(0.0, 553.6),
            ElevationSample(3.0, 570.0),
            ElevationSample(6.0, 568.0),
            ElevationSample(9.0, 663.6)
        )

        val shape = ElevationShape.of(walked)

        assertEquals(553.6, shape.first().heightM, 1e-9)
        assertEquals(663.6, shape.last().heightM, 1e-9)
    }

    @Test
    fun `a rise is never turned into a fall`() {
        val climbing = line(301, 3.0) { index -> 100.0 + index * 0.2 }

        val shape = ElevationShape.of(climbing)

        for (index in 1 until shape.size) {
            assertTrue(
                "rises at $index, never falls: ${shape[index - 1].heightM} to ${shape[index].heightM}",
                shape[index].heightM >= shape[index - 1].heightM
            )
        }
    }

    @Test
    fun `too little to average comes back untouched`() {
        val two = listOf(ElevationSample(0.0, 100.0), ElevationSample(3.0, 110.0))
        val one = listOf(ElevationSample(0.0, 100.0))

        assertSame(two, ElevationShape.of(two))
        assertSame(one, ElevationShape.of(one))
        assertSame(emptyList<ElevationSample>(), ElevationShape.of(emptyList()))
    }

    @Test
    fun `a window of nothing is not a window`() {
        val noisy = line(10, 3.0) { index -> 100.0 + index }

        assertSame(noisy, ElevationShape.of(noisy, windowM = 0.0))
        assertSame(noisy, ElevationShape.of(noisy, windowM = -40.0))
    }

    @Test
    fun `the metres along the line do not move`() {
        val noisy = line(100, 3.0) { index -> 100.0 + (index % 5) }

        val shape = ElevationShape.of(noisy)

        assertEquals(noisy.map { it.alongM }, shape.map { it.alongM })
    }

    @Test
    fun `a long line is thinned to about the most a chart can show`() {
        val long = line(2_000, 3.0) { index -> 100.0 + index * 0.01 }

        val shape = ElevationShape.of(long)

        assertTrue("thinned to ${shape.size} samples", shape.size <= ElevationShape.MAX_SAMPLES)
        assertEquals("the line still starts where the walk did", long.first(), shape.first())
        assertEquals("and ends at the walk's last fix", long.last(), shape.last())
    }

    @Test
    fun `a spike survives the thinning that leaves its flat metres behind`() {
        // One 800 m spike in a 6 km flat line. A 200 m window draws it as a hump of about 112 m; the
        // point is that the thinning kept that hump rather than the flat 100 m around it.
        val spiked = line(2_000, 3.0) { index -> if (index == 1_000) 900.0 else 100.0 }

        val shape = ElevationShape.of(spiked)

        assertTrue("thinned to ${shape.size} samples", shape.size <= ElevationShape.MAX_SAMPLES)
        assertTrue(
            "the spike is still the highest thing drawn: ${shape.maxOf { it.heightM }}",
            shape.maxOf { it.heightM } > 110.0
        )
    }
}
