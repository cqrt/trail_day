package nz.mckenzie.trailday.offline

import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ground to keep around a track.
 *
 * A walker's unit is the track they are about to walk, so the box is worked out from the line rather than
 * drawn by hand - and the two things worth holding still are that the margin is on every side, and that
 * longitude is widened where latitude is not, because a degree of longitude is shorter down here.
 */
class AroundTrackTest {

    /** A short line near Bluff: two vertices a few hundred metres apart. */
    private val line = AssetGeometry(
        paths = listOf(
            listOf(GeoPoint(lat = -46.6000, lng = 168.3000), GeoPoint(lat = -46.6100, lng = 168.3100))
        )
    )

    @Test
    fun `the box is the line's own, with a margin on every side`() {
        val ground = groundAround(name = "Glory Tk", geometry = line, widthM = 2_000.0)!!

        assertEquals("the name travels, so the area is named after the walk", "Glory Tk", ground.name)
        assertTrue("west of the line", ground.bounds.minLng < 168.3000)
        assertTrue("east of it", ground.bounds.maxLng > 168.3100)
        assertTrue("south of it", ground.bounds.minLat < -46.6100)
        assertTrue("north of it", ground.bounds.maxLat > -46.6000)
    }

    @Test
    fun `two kilometres either side is what two kilometres means`() {
        val ground = groundAround(name = "Glory Tk", geometry = line, widthM = 2_000.0)!!

        // A degree of latitude is 111,132 m, so 2 km either side is about 0.018 degrees each way.
        assertEquals(0.036, ground.bounds.maxLat - ground.bounds.minLat - 0.01, 0.005)
        assertEquals(-46.6000 + 0.018, ground.bounds.maxLat, 0.002)

        // And further in longitude, because a degree of longitude is shorter this far south.
        assertTrue(
            "the margin is wider east-west than north-south",
            (ground.bounds.maxLng - ground.bounds.minLng - 0.01) >
                (ground.bounds.maxLat - ground.bounds.minLat - 0.01)
        )
    }

    @Test
    fun `a wider margin is a bigger box, and the middle does not move`() {
        val near = groundAround("Glory Tk", line, widthM = 1_000.0)!!
        val far = groundAround("Glory Tk", line, widthM = 4_000.0)!!

        assertTrue(far.bounds.minLat < near.bounds.minLat)
        assertTrue(far.bounds.maxLat > near.bounds.maxLat)
        assertEquals(
            "the middle of the line stays the middle of the box",
            (near.bounds.minLat + near.bounds.maxLat) / 2.0,
            (far.bounds.minLat + far.bounds.maxLat) / 2.0,
            1e-9
        )
    }

    @Test
    fun `a track with nothing on it has no ground to keep`() {
        assertNull(groundAround("Nothing", AssetGeometry.NONE))
        assertNull(groundAround("Nothing", AssetGeometry(paths = listOf(emptyList()))))
    }

    @Test
    fun `a one-point track still asks for ground rather than for a point`() {
        // A walked track can be a single vertex, and an area with no size is not a question anybody can
        // answer: the app's own minimum span is the floor.
        val single = AssetGeometry(paths = listOf(listOf(GeoPoint(lat = -46.6, lng = 168.3))))

        val ground = groundAround("One point", single)!!

        assertTrue("a box rather than a point", ground.bounds.maxLat > ground.bounds.minLat)
        assertTrue(ground.bounds.maxLng > ground.bounds.minLng)
    }
}
