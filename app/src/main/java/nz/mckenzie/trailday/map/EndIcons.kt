package nz.mckenzie.trailday.map

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * The flag a trip finishes under: one picture, in the recorded green, with a white halo.
 *
 * A sibling of [MarkerIcons] and [PlaceIcons] with **no kind**, because this is not a place. A marker
 * says what a thing *is* - a hut, a campsite, a bike track - and the walker chooses each one's colour in
 * the Look. The finish of a trip says where a day ended, so its colour is the recorded green whatever
 * the day was: a ride's purple line runs up to a green flag, and that is right. It cannot follow the
 * look and cannot follow the mode, which is why there is one picture and no more.
 *
 * Drawn through [CanvasDrawScope] the way [MarkerIcons] is, at the device's own density, because a
 * MapLibre image is drawn at its own pixels: there is no icon-size in the style to keep in step with
 * the screen, and a flag that was scaled by the map would smear its pole.
 */
internal object EndIcons {

    /** The name the map's finish layer asks for. */
    const val IMAGE_NAME = "trailday-end-flag"

    /**
     * The side of the square the flag is drawn in, in density-independent pixels.
     *
     * A place marker's own size, because the two are read at the same distance over the same imagery.
     * The ink is smaller than the box on purpose: the halo has to fall inside it.
     */
    const val SIZE_DP = 22f

    // The flag on the app's 24-unit grid, as fractions of the box. The pole stops short of the bottom
    // edge - 0.92 rather than 1.0 - so the halo's half-width and the round cap have room inside the
    // picture, and so the foot, not the middle, is the thing that lands on the coordinate.
    private const val POLE_X = 0.26f
    private const val POLE_TOP = 0.10f
    private const val POLE_FOOT = 0.92f
    private const val PENNANT_TIP_X = 0.80f
    private const val PENNANT_TIP_Y = 0.24f
    private const val PENNANT_FOOT_Y = 0.40f

    /**
     * The pole's stroke, as a fraction of the box.
     *
     * Wide enough to survive being drawn at 22 dp - about four pixels on a 420 dpi phone - and no
     * wider: with the halo, 0.07 is what still leaves the foot inside the square.
     */
    private const val POLE_WEIGHT = 0.07f

    /**
     * The flag, [sizePx] across in a transparent square.
     *
     * @param sizePx the width and height of the picture, in pixels of the device's own density.
     * @param outlinePx the width of the white halo, in pixels of the same density. The map asks for
     *   [PlaceIcons.MARKER_OUTLINE_DP], the same white every marker wears.
     */
    fun bitmap(sizePx: Int, outlinePx: Float): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        // The halo arrives in pixels and the drawing wants pixels, but it is clamped as a fraction of
        // the box: an outline wider than the flag is meant to be is a white square with a green
        // splinter in it, and a style that asked for one should not be able to draw that.
        val rim = outlinePx.coerceAtLeast(0f).coerceAtMost(size * MOST_HALO_FRACTION)

        CanvasDrawScope().draw(
            // No scaling: the bitmap is already the size the flag is drawn at, on this device.
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap.asImageBitmap()),
            size = Size(size.toFloat(), size.toFloat())
        ) {
            drawFlag(parseHexColor(PlaceIcons.MARKER_OUTLINE), grow = rim)
            drawFlag(parseHexColor(AssetColors.RECORDED), grow = 0f)
        }

        return bitmap
    }

    /** The widest the halo may be, as a fraction of the box - a fifth, and the flag is still a flag. */
    private const val MOST_HALO_FRACTION = 0.2f

    /**
     * The pole and the pennant, grown by [grow] all round.
     *
     * The halo is a **rim** rather than a second, fatter flag: the pole is stroked that much wider and
     * the pennant is stroked over its own edge, so half of each falls outside the shape - the same
     * treatment [drawKindGlyph] gives a marker, and for the same reason. A pennant simply drawn bigger
     * would move the flag's tip, and the tip is the shape.
     */
    private fun DrawScope.drawFlag(color: Color, grow: Float) {
        val x = { fraction: Float -> size.width * fraction }
        val y = { fraction: Float -> size.height * fraction }
        val stroke = size.minDimension * POLE_WEIGHT
        drawLine(
            color = color,
            start = Offset(x(POLE_X), y(POLE_FOOT)),
            end = Offset(x(POLE_X), y(POLE_TOP)),
            strokeWidth = stroke + grow * 2f,
            cap = StrokeCap.Round
        )

        val pennant = Path().apply {
            moveTo(x(POLE_X), y(POLE_TOP))
            lineTo(x(PENNANT_TIP_X), y(PENNANT_TIP_Y))
            lineTo(x(POLE_X), y(PENNANT_FOOT_Y))
            close()
        }
        // Filled, so the pennant is a solid flag rather than an outline: a hollow one at this size
        // reads as a loop in the line. The halo pass strokes the same path, which is what widens it
        // without redrawing it at a different size.
        drawPath(pennant, color, style = Fill)
        if (grow > 0f) {
            drawPath(
                pennant,
                color,
                style = Stroke(width = grow * 2f, join = StrokeJoin.Round, cap = StrokeCap.Round)
            )
        }
    }
}
