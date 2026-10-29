package nz.mckenzie.trailday.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LINZ's height tiles, as numbers.
 *
 * The sum is Mapbox Terrain-RGB, which LINZ follows: three colour channels are one number in tenths of a
 * metre, from ten thousand metres below the sea. It is arithmetic nobody can check by looking at a map,
 * so it is checked here.
 */
class TerrainRgbTest {

    @Test
    fun `black is the bottom of the scale and one step is a decimetre`() {
        assertEquals(-10_000.0, TerrainRgb.metres(0, 0, 0), 0.0001)
        assertEquals(-9_999.9, TerrainRgb.metres(0, 0, 1), 0.0001)
        assertEquals(-9_999.9 + 25.6, TerrainRgb.metres(0, 1, 1), 0.0001)
        // 65536 steps of a decimetre is 6,553.6 m above the bottom of the scale.
        assertEquals(-3_446.4, TerrainRgb.metres(1, 0, 0), 0.0001)
    }

    @Test
    fun `a mountain reads as a mountain`() {
        // 1,800 m is 11,800 m above the bottom of the scale: 118,000 steps, which is 0x01CCF0.
        assertEquals(1_800.0, TerrainRgb.metres(0x01, 0xCC, 0xF0), 0.0001)
    }

    @Test
    fun `only the climbing counts`() {
        assertEquals(130.0, TerrainRgb.climbM(listOf(100.0, 150.0, 120.0, 200.0)), 0.0001)
    }

    @Test
    fun `a point with no height breaks the run rather than counting as nothing`() {
        assertEquals(0.0, TerrainRgb.climbM(listOf(100.0, null, 200.0)), 0.0001)
        assertEquals(50.0, TerrainRgb.climbM(listOf(100.0, null, 200.0, 250.0)), 0.0001)
        assertEquals(0.0, TerrainRgb.climbM(listOf(null, null)), 0.0001)
    }

    @Test
    fun `a place falls in a tile and a pixel of it`() {
        val pixel = TerrainRgb.pixelFor(GeoPoint(lat = -45.0, lng = 167.0), TerrainRgb.ZOOM)

        assertTrue("a pixel, not a tile coordinate", pixel.px in 0..255 && pixel.py in 0..255)
        assertTrue("a tile in the world", pixel.x in 0 until (1 shl TerrainRgb.ZOOM))

        val furtherEast = TerrainRgb.pixelFor(GeoPoint(lat = -45.0, lng = 167.5), TerrainRgb.ZOOM)
        assertTrue("east is a larger x", furtherEast.x > pixel.x)

        val furtherNorth = TerrainRgb.pixelFor(GeoPoint(lat = -44.5, lng = 167.0), TerrainRgb.ZOOM)
        assertTrue("north is a smaller y", furtherNorth.y < pixel.y)
    }
}
