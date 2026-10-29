package nz.mckenzie.trailday.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A height read from LINZ's tiles, on a device, through the route the app actually uses.
 *
 * This is the one piece of the heights that a unit test cannot hold: decoding a PNG and reading a pixel
 * needs `Bitmap`. It is also where a journey went wrong. A journey made over Ben Lomond reported no climb
 * at all, while the arithmetic above it - the projection into a tile, the climb, storing the heights on
 * the points - was tested and passed, because that test only asked which way north was.
 *
 * The ground here is known: these three points run up the hillside above Queenstown from a little over
 * 300 m to about 800 m, so the heights have to rise with them. Asking only for "not null" would pass on
 * a slope that read 0 m, which is exactly what it used to do.
 */
@RunWith(AndroidJUnit4::class)
class TerrainHeightsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun summitAndShorelineReadAsTheHeightsTheyAre() = runBlocking {
        TileServerHolder.start(context)

        val heights = TerrainHeights().along(
            listOf(
                GeoPoint(lat = -45.02637, lng = 168.64686),
                GeoPoint(lat = -45.03300, lng = 168.65300),
                GeoPoint(lat = -45.03420, lng = 168.66140)
            )
        )

        assertTrue("heights for all three, and this read $heights", heights.all { it != null })
        val known = heights.filterNotNull()
        assertTrue("every height is a real one, and this read $heights", known.all { it in -50.0..3000.0 })
        assertTrue(
            "the hillside rises hundreds of metres along these points, and this read $heights",
            known.max() - known.min() > 300
        )
    }
}
