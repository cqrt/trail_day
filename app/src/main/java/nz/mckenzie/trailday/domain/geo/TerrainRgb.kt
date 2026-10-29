package nz.mckenzie.trailday.domain.geo

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh

/**
 * LINZ's height tiles, as numbers.
 *
 * DOC publishes no heights at all, so a walk built out of DOC's rows has none. LINZ publishes the
 * country's height as **terrain-rgb** tiles, the same tiles the drawn map is shaded from, and one pixel
 * of one of them is a height in metres.
 *
 * The sum is fixed by Mapbox's Terrain-RGB, which LINZ follows: the three colour channels are one number
 * in tenths of a metre, counted from ten thousand metres below the sea.
 */
object TerrainRgb {

    /** The zoom the heights are read at: the deepest LINZ publishes, about four metres a pixel. */
    const val ZOOM = 15

    /** The height in metres a pixel stands for. */
    fun metres(red: Int, green: Int, blue: Int): Double =
        -10_000.0 + (((red and 0xFF) shl 16) or ((green and 0xFF) shl 8) or (blue and 0xFF)) * 0.1

    /**
     * Which tile a place falls in, at [zoom], and where in it.
     *
     * Web Mercator, which is what every XYZ tile service is: `x` counts across the world, `y` counts down
     * from the top, and a tile is 256 pixels each way.
     */
    fun pixelFor(point: GeoPoint, zoom: Int = ZOOM): TerrainPixel {
        val scale = (1 shl zoom).toDouble()
        val across = (point.lng + 180.0) / 360.0 * scale
        val latRad = point.lat * PI / 180.0
        // Mercator's y in radians, which is what asinh(tan) is; then the same 0..1 the x axis uses.
        val northSouth = atan(sinh(latRad))
        val down = (1.0 - northSouth / PI) / 2.0 * scale
        val tileX = floor(across).toInt()
        val tileY = floor(down).toInt()
        val px = ((across - tileX) * 256.0).toInt().coerceIn(0, 255)
        val py = ((down - tileY) * 256.0).toInt().coerceIn(0, 255)
        return TerrainPixel(x = tileX, y = tileY, px = px, py = py)
    }

    /**
     * The climb along a line, from a height for each point.
     *
     * Only the rises count, which is what Naismith's rule asks for and what the app's own [ElevationProfile]
     * already does with heights that arrived in a file: the two must not disagree about the same line. A
     * point with no height breaks the run rather than counting as zero, because a missing height is not a
     * height of nothing.
     */
    fun climbM(heights: List<Double?>): Double {
        var climb = 0.0
        for (index in 1 until heights.size) {
            val here = heights[index] ?: continue
            val before = heights[index - 1] ?: continue
            val rise = here - before
            if (rise > 0.0) climb += rise
        }
        return climb
    }
}

/** Where a place falls in the heights: which tile, and which pixel of it. */
data class TerrainPixel(val x: Int, val y: Int, val px: Int, val py: Int)
