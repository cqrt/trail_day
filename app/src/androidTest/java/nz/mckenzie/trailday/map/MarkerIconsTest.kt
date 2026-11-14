package nz.mckenzie.trailday.map

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.look.MarkerSizeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pictures the map draws a place with.
 *
 * A bitmap cannot be drawn on the JVM, so this is the one place the *drawing* is checked rather than
 * the naming: that every kind of place gives a picture with something in it, and that no two of them
 * are the same picture. `Bitmap.sameAs` compares pixels, so a hut and a campsite that came
 * out identical - the mistake two similar shapes are most likely to make - fails
 * here rather than being noticed on a hillside.
 */
@RunWith(AndroidJUnit4::class)
class MarkerIconsTest {

    /**
     * The edge these tests draw: well over the width the map uses, and stated once.
     *
     * The tests about shape, colour and drawing-on-nothing want an edge big enough to see, and the rim
     * test wants a width at which a block would show if the drawing ever went back to one. The width
     * the app actually draws is pinned by [everyMarkerWearsAThinWhiteEdge], and that is the one that
     * comes from [PlaceIcons.MARKER_OUTLINE_DP].
     */
    private val drawnEdgePx = 6f

    private fun marker(kind: AssetKind, colorHex: String = AssetColors.TRACK_KIND) =
        MarkerIcons.bitmap(kind, colorHex, sizePx = 64, outlinePx = drawnEdgePx)

    @Test
    fun everyKindOfPlaceIsDrawnAsSomething() {
        PlaceIcons.KINDS.forEach { kind ->
            val bitmap = marker(kind)
            val painted = (0 until bitmap.width).sumOf { x ->
                (0 until bitmap.height).count { y -> bitmap.getPixel(x, y) != 0 }
            }

            assertTrue("${kind.name} is drawn as nothing at all", painted > 0)
            assertTrue(
                "and is not a smudge: $painted pixels of ${bitmap.width * bitmap.height}",
                painted > bitmap.width
            )
        }
    }

    @Test
    fun theBikeWheelsAreHollow() {
        // The bike is not one of the place kinds, but it is the map's badge for a bike trail. At
        // marker size a heavy ring closed the hole and the bicycle read as two purple plums; the
        // wheel's centre has to be see-through for it to read as a wheel.
        val bitmap = MarkerIcons.bitmap(
            kind = AssetKind.BIKE_TRACK,
            colorHex = AssetColors.BIKE_TRACK_KIND,
            sizePx = 64,
            outlinePx = 2f
        )
        listOf(0.26, 0.74).forEach { wheel ->
            val x = (bitmap.width * wheel).toInt()
            val y = (bitmap.height * 0.72).toInt()
            assertEquals(
                "the wheel centred at $x,$y is filled in, so the bicycle reads as a blob",
                0,
                Color.alpha(bitmap.getPixel(x, y))
            )
        }
    }

    @Test
    fun noTwoKindsOfPlaceAreTheSamePicture() {
        val pictures = PlaceIcons.KINDS.map { it to marker(it) }

        pictures.forEachIndexed { at, (kind, bitmap) ->
            pictures.drop(at + 1).forEach { (other, otherBitmap) ->
                assertFalse(
                    "${kind.name} and ${other.name} are drawn the same, so the map cannot tell them apart",
                    bitmap.sameAs(otherBitmap)
                )
            }
        }
    }

    @Test
    fun noMarkerIsDrawnOnABackground() {
        // A marker is a picture over imagery, not a tile: whatever is behind it has to show through.
        // The corners are where a background shows first, and the first version of this drawing put
        // a white box around a sign - a 25-pixel white stroke on a 20-pixel plate.
        PlaceIcons.KINDS.forEach { kind ->
            val bitmap = marker(kind)
            val corners = listOf(0 to 0, bitmap.width - 1 to 0, 0 to bitmap.height - 1, bitmap.width - 1 to bitmap.height - 1)

            corners.forEach { (x, y) ->
                assertEquals(
                    "${kind.name} is drawn on a background: the pixel at $x,$y is not see-through",
                    0,
                    Color.alpha(bitmap.getPixel(x, y))
                )
            }
        }
    }

    @Test
    fun theWhiteEdgeIsARimAndNotABlock() {
        // The white edge is as wide as the edge drawn here says and no wider, which means every white
        // pixel has a coloured pixel within that distance of it. A white *block* - the same path drawn
        // fatter - has white pixels with the nearest colour further away than that, and that is the
        // difference between a marker that reads over imagery and a white square with a red shape in it.
        PlaceIcons.KINDS.forEach { kind ->
            val bitmap = marker(kind)
            val reach = (drawnEdgePx * 2).toInt() + 2

            var painted = 0
            var stranded = 0
            for (x in 0 until bitmap.width) {
                for (y in 0 until bitmap.height) {
                    if (!bitmap.isWhiteAt(x, y)) continue
                    painted++
                    if (!bitmap.hasColourNear(x, y, reach)) stranded++
                }
            }

            assertTrue("${kind.name} was drawn as nothing at all", painted > 0)
            assertEquals(
                "${kind.name} has $stranded white pixels further than ${reach}px from any colour, " +
                    "so its edge is a block rather than a rim",
                0,
                stranded
            )
        }
    }

    @Test
    fun theColourAskedForIsTheColourDrawn() {
        val green = marker(AssetKind.HUT, AssetColors.TRACK_KIND)
        val red = marker(AssetKind.HUT, AssetColors.RECORDED)

        assertFalse("a marker's colour is not a decoration", green.sameAs(red))
    }

    @Test
    fun everyMarkerWearsAThinWhiteEdge() {
        // "Thin" is the walker's word for it and the number in the code is a width in
        // density-independent pixels, so this draws the edge the map draws - at the marker's own size -
        // and counts what it costs. The edge has to be there, because a red marker with no white round
        // it was hard to read over winter imagery; and it has to be thin, because at two dp the markers
        // read as white shapes with a colour inside them. The edge twice as wide is drawn beside it so
        // that "thin" is a comparison as well as a number.
        val size = 64
        PlaceIcons.KINDS.forEach { kind ->
            val thin = MarkerIcons.bitmap(kind, AssetColors.RECORDED, size, edgePx(size))
            val wide = MarkerIcons.bitmap(kind, AssetColors.RECORDED, size, edgePx(size) * 2f)

            val white = thin.whitePixels()
            val whiter = wide.whitePixels()

            assertTrue("${kind.name} is drawn with no white edge on it at all", white > 0)
            assertTrue(
                "${kind.name}'s edge takes $white pixels of ${size * size}, which is not a thin edge",
                white * 100 < size * size * mostEdgePercent
            )
            assertTrue(
                "${kind.name} is drawn with as much white as an edge twice as wide ($white of $whiter)",
                white < whiter
            )
        }
    }

    /**
     * How much of a marker's picture its edge may take, as a percentage.
     *
     * Measured on the phone's own marker, one dp against two: at one the worst kind is the dot in a
     * ring at 24.8% of the picture and the leanest is the sign at 14.2%; at two the *least* white kind,
     * the sign, was 29.2% and the ring 39.1%. A ceiling between the two - nearer the two - fails if the
     * edge is ever made fat again, and leaves the thin one room to be redrawn slightly differently.
     */
    private val mostEdgePercent = 27

    @Test
    fun theFinishFlagIsDrawnInTheRecordedGreen() {
        // The flag is the one picture on the map whose colour is not the walker's to choose, so it has
        // to be the recorded green and nothing else: a ride's purple line runs up to it, and the two
        // marks read as one trip because the flag is the colour of a trip's evidence.
        val bitmap = EndIcons.bitmap(sizePx = 64, outlinePx = 2f)

        // Inside the pennant: a third of the way along it and level with its point, which is ink
        // whatever the halo and the antialiasing do at the edges.
        val pixel = bitmap.getPixel((bitmap.width * 0.40f).toInt(), (bitmap.height * 0.24f).toInt())

        val wanted = Color.parseColor(AssetColors.RECORDED)
        assertEquals("the flag's ink is not the recorded green", Color.alpha(pixel), 255)
        assertEquals("red of ${Integer.toHexString(pixel)}", Color.red(wanted), Color.red(pixel))
        assertEquals("green of ${Integer.toHexString(pixel)}", Color.green(wanted), Color.green(pixel))
        assertEquals("blue of ${Integer.toHexString(pixel)}", Color.blue(wanted), Color.blue(pixel))
    }

    @Test
    fun theFinishFlagIsDrawnOnNothing() {
        // The flag is over imagery like every marker, so what is behind it has to show through. The top
        // left corner is where a flag would be if the halo were ever drawn as a box again.
        val bitmap = EndIcons.bitmap(sizePx = 64, outlinePx = 2f)

        assertEquals("the top left corner is not see-through", 0, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(
            "the top right corner is not see-through",
            0,
            Color.alpha(bitmap.getPixel(bitmap.width - 1, 0))
        )
    }

    @Test
    fun theFinishFlagWearsAWhiteHalo() {
        // The flag is over imagery like every marker, so it wears the same white edge - and the edge
        // is a rim rather than a white box: a halo wide enough to be the background would be a flag
        // that had become a white square with a green notch in it. The bug this holds was real: the
        // halo was passed as a fraction of the box where the drawing wanted pixels, and drew nothing.
        val bitmap = EndIcons.bitmap(sizePx = 64, outlinePx = 4f)

        val white = bitmap.whitePixels()
        assertTrue("the flag is drawn with no white edge at all, so it is lost in dark bush", white > 0)
        assertTrue(
            "the white takes $white pixels of ${64 * 64}, which is a background rather than a halo",
            white < 64 * 64 / 3
        )
    }

    @Test
    fun theFinishFlagStandsOnItsOwnFoot() {
        // The style anchors the picture by its bottom edge, so the ink has to reach down to near that
        // edge: a flag drawn in the top of its box would float above the last fix rather than stand on
        // it. The foot is the pole, which is the only ink in the bottom quarter.
        val bitmap = EndIcons.bitmap(sizePx = 64, outlinePx = 2f)

        val lowest = (0 until bitmap.height).last { y ->
            (0 until bitmap.width).any { x -> Color.alpha(bitmap.getPixel(x, y)) > 0 }
        }
        assertTrue(
            "the flag's ink stops at ${lowest + 1} of ${bitmap.height}, above the bottom quarter",
            lowest >= bitmap.height * 3 / 4
        )

        val footX = (0 until bitmap.width).filter { x -> Color.alpha(bitmap.getPixel(x, lowest)) > 0 }
        assertTrue("the foot is drawn somewhere", footX.isNotEmpty())
        val centre = (footX.first() + footX.last()) / 2
        assertTrue(
            "the pole's foot is at $centre of ${bitmap.width}, which is not under the pole",
            kotlin.math.abs(centre - bitmap.width * 0.26f) <= bitmap.width * 0.08f
        )
    }

    /** The edge the map draws, in pixels of a picture [size] across. */
    private fun edgePx(size: Int): Float =
        PlaceIcons.MARKER_OUTLINE_DP * size / MarkerSizeChoice.NORMAL.dp

    private fun Bitmap.whitePixels(): Int = (0 until width).sumOf { x ->
        (0 until height).count { y -> isWhiteAt(x, y) }
    }

    private fun Bitmap.isWhiteAt(x: Int, y: Int): Boolean {
        val pixel = getPixel(x, y)
        return Color.alpha(pixel) > 0 &&
            Color.red(pixel) > 240 && Color.green(pixel) > 240 && Color.blue(pixel) > 240
    }

    /** Whether any pixel in the square of side [reach] x 2 around ([x], [y]) is a colour. */
    private fun Bitmap.hasColourNear(x: Int, y: Int, reach: Int): Boolean {
        for (dx in -reach..reach) {
            for (dy in -reach..reach) {
                val px = x + dx
                val py = y + dy
                if (px !in 0 until width || py !in 0 until height) continue
                val pixel = getPixel(px, py)
                if (Color.alpha(pixel) == 0) continue
                if (Color.red(pixel) < 220 || Color.green(pixel) < 220 || Color.blue(pixel) < 220) return true
            }
        }
        return false
    }
}
