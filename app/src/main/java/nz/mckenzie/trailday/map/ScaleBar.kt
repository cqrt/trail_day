package nz.mckenzie.trailday.map

import android.graphics.PointF
import nz.mckenzie.trailday.domain.geo.haversineMeters
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt

/**
 * How far across the ground the map covers, and how much of that a scale bar should say.
 *
 * **The map is asked, rather than a formula applied to it.** The obvious way to build a scale bar is
 * `156543 * cos(latitude) / 2^zoom`, and on this phone that is wrong by a factor of 2.6. It assumes the
 * 256-pixel tiles this app's sources are declared with, while a renderer's zoom is a level of its own -
 * and the renderer here draws at the screen's own density, so at zoom 6 over the lower North Island the
 * formula says 869 m to a pixel and the map's own projection says 331 (measured on the emulator, 420
 * dpi). None of that is written down anywhere the app can read it. So this asks the projection where two
 * points on the glass land on the ground and measures between them, which is what MapLibre's own scale
 * control does and which cannot come adrift from the renderer it is drawn on.
 *
 * **Along the foot of the map, not the middle.** A row of the screen is one latitude, and the ground a
 * pixel covers is the cosine of that latitude, so the row matters: the middle of a screen showing the
 * whole country is several degrees north of the bar drawn at its foot, and a bar measured from the wrong
 * row is out by a noticeable fraction at the zoom a walker reads the country at. The bar sits at the
 * foot, so the measurement is taken there.
 *
 * The drawing is the screen's, not this file's: [nz.mckenzie.trailday.ui.screens.MapScreen] puts the bar
 * above the credit strip, in the corner that strip already owns. Here is only the arithmetic.
 */
object ScaleBar {

    /**
     * The longest the bar is drawn, in dp.
     *
     * Short enough to sit in a corner of a phone screen beside the credit strip, long enough to read
     * a distance off. The bar is rarely at this length: the distance it shows is rounded down to a
     * number a walker thinks in, and that is what sets the length.
     */
    const val MAX_WIDTH_DP = 96f

    /**
     * The width the ground is measured across, in pixels.
     *
     * Wide enough that one pixel of error in the projection does not matter, narrow enough that the
     * curvature of the sphere across it is nothing - see [metresPerPixel].
     */
    private const val PROBE_PX = 256f

    /**
     * The distances a bar is allowed to show: half a metre, then one, two and five of every ten.
     *
     * A bar is read at a glance, and "1.3 km" is not a distance anybody glances at. The list runs up
     * to twenty thousand kilometres because the map's own camera can be pulled out to the whole
     * world, where the bar's share of the screen is a fifth of the planet.
     */
    private val NICE_METRES: List<Double> = buildList {
        add(0.5)
        var step = 1.0
        while (step <= 20_000_000.0) {
            add(step)
            add(step * 2.0)
            add(step * 5.0)
            step *= 10.0
        }
    }

    /**
     * Metres across one pixel of the map, at the row a bar is drawn on - or null while there is
     * nothing to measure.
     *
     * The two points are unprojected from the foot of the map and measured with the haversine rule,
     * which is the same pair of steps MapLibre's own scale control makes. Both are on one row, so
     * both are on one latitude: in Web Mercator a row of the screen is a parallel, and the answer
     * does not depend on which part of that row was probed.
     */
    fun metresPerPixel(map: MapLibreMap, widthPx: Int, heightPx: Int): Double? {
        if (widthPx <= 0 || heightPx <= 0) return null

        val foot = (heightPx - 1).toFloat()
        val centre = widthPx / 2f
        val half = PROBE_PX / 2f
        val left = map.projection.fromScreenLocation(PointF(centre - half, foot))
        val right = map.projection.fromScreenLocation(PointF(centre + half, foot))

        val metres = haversineMeters(left.latitude, left.longitude, right.latitude, right.longitude)
        return (metres / PROBE_PX).takeIf { it.isFinite() && it > 0.0 }
    }

    /**
     * The distance to show across [maxWidthPx] of the glass, and how wide that makes the bar.
     *
     * The largest of [NICE_METRES] that fits, so the bar is as long as it can be without overrunning
     * the room it has been given - which is what makes it readable at every zoom. Null when there is
     * no measurement to work from, or when the ground is too fine for even half a metre to fit: a bar
     * that says nothing rather than one that says something wrong.
     */
    fun span(metresAcross: Double, maxWidthPx: Float): ScaleBarSpan? {
        if (!metresAcross.isFinite() || metresAcross <= 0.0 || maxWidthPx <= 0f) return null

        val metres = NICE_METRES.lastOrNull { it <= metresAcross } ?: return null
        return ScaleBarSpan(metres, (maxWidthPx * (metres / metresAcross)).toFloat())
    }

    /** "500 m" / "2 km" - what the bar says. */
    fun label(metres: Double): String = when {
        metres < 1.0 -> "${(metres * 100.0).roundToInt()} cm"
        metres < 1000.0 -> "${metres.roundToInt()} m"
        else -> "${(metres / 1000.0).roundToInt()} km"
    }
}

/**
 * What a bar says, and how long it is drawn.
 *
 * [widthPx] is pixels of the map view, which is the same glass a Compose overlay is drawn on: the two
 * fill one box, so the length measured from the map is the length drawn on the screen with no
 * arithmetic between them.
 */
data class ScaleBarSpan(val metres: Double, val widthPx: Float)
