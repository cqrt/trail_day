package nz.mckenzie.trailday.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import nz.mckenzie.trailday.domain.asset.AssetKind

/**
 * The five kinds of place, drawn: one shape each, in whatever colour the kind needs.
 *
 * **One geometry, two weights.** The list draws a glyph 20 dp wide beside a name, where a thin
 * outline is enough and anything heavier is a blob; the map draws the same glyph as a marker over
 * aerial imagery, where a thin outline disappears into the ground and a white edge is what makes it
 * findable. Those are the same shapes at different weights, not two sets of shapes - so they live
 * here together, and a hut cannot be a hut in the list and something else on the map.
 *
 * The colour is the caller's, because the two jobs colour them differently on purpose: beside a name
 * the colour says *which kind* it is, and on the map it is the kind's own colour too - see
 * [AssetColors.forKind]. Only the recorded line and the draft keep a colour that is nobody's kind.
 *
 * What each shape leans on to be told apart at twenty pixels is commented beside it, because that is
 * the thing reading the code cannot recover: two glyphs that are the same shape at this size is a
 * list - or a map - where the walker cannot tell a hut from a campsite.
 */
internal data class GlyphStyle(
    /** Stroke width, as a fraction of the box. */
    val weight: Float,
    /**
     * The white edge drawn behind the glyph, as a fraction of the box. Zero for a glyph on paper; a
     * marker is over imagery, where red on dark winter ground is otherwise a dark shape on dark
     * ground.
     */
    val haloFraction: Float = 0f,
    /** Whether a closed box - a hut's walls - is filled rather than outlined. */
    val filledBoxes: Boolean = false
) {
    companion object {

        /** Beside a name: a thin outline on paper, which is all a 20 dp glyph needs. */
        val LIST = GlyphStyle(weight = 0.13f)

        /**
         * Over imagery: fatter, and filled where a shape is a solid thing.
         *
         * How wide the white edge is is not decided here: it belongs to the map, in
         * [PlaceIcons.MARKER_OUTLINE_DP], and [MarkerIcons] asks for a glyph drawn at that width. What
         * this style decides is the shape - how thick the strokes are, and which of them are solid.
         */
        val MARKER = GlyphStyle(weight = 0.20f, filledBoxes = true)
    }
}

/**
 * Draws [kind]'s glyph to fill the canvas it is given.
 *
 * The halo is the same shapes stroked wider and drawn first, which is why a glyph is built inside the
 * square rather than on its edge: half of that wider stroke has to fall outside the shape, and a
 * shape drawn to the very edge would have its edge cut off.
 */
internal fun DrawScope.drawKindGlyph(kind: AssetKind, colorHex: String, style: GlyphStyle) {
    // The halo is a **rim**: every shape is grown by it, so the white edge is that wide and no wider.
    // Growing the *stroke* instead - the same path drawn fatter - looks identical on a long thin
    // shape and is wrong on a small fat one: a 20-pixel plate stroked 25 pixels wide has no hole left
    // in the middle, so the mark's marker came out as a white block with a colour bar in it.
    val rim = size.minDimension * style.haloFraction
    if (rim > 0f) {
        drawKindShapes(kind, parseHexColor(PlaceIcons.MARKER_OUTLINE), style, grow = rim)
    }
    drawKindShapes(kind, parseHexColor(colorHex), style, grow = 0f)
}

private fun DrawScope.drawKindShapes(
    kind: AssetKind,
    color: Color,
    style: GlyphStyle,
    /** How far outside itself every shape is drawn: the halo's rim, and nothing at all in colour. */
    grow: Float
) {
    val stroke = size.minDimension * style.weight
    // What this pass actually draws with. Equal to [stroke] for the colour pass, that much plus the
    // rim twice over for the halo, which is a rim half inside and half outside the shape it grows.
    val drawnStroke = stroke + grow * 2f
    val width = size.width
    val height = size.height
    val x = { fraction: Float -> width * fraction }
    val y = { fraction: Float -> height * fraction }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, widthOf: Float = stroke) {
        drawLine(
            color = color,
            start = Offset(x(x1), y(y1)),
            end = Offset(x(x2), y(y2)),
            strokeWidth = widthOf + grow * 2f,
            cap = StrokeCap.Round
        )
    }

    /** A box, filled or outlined according to the style - and larger all round by the rim. */
    fun box(left: Float, top: Float, boxWidth: Float, boxHeight: Float) {
        drawRect(
            color = color,
            topLeft = Offset(x(left) - grow, y(top) - grow),
            size = Size(width * boxWidth + grow * 2f, height * boxHeight + grow * 2f),
            style = if (style.filledBoxes) Fill else Stroke(width = drawnStroke)
        )
    }

    when (kind) {
        // A track wanders, so it is one line with a bend in it - which is also what tells it from a
        // bike track's wheels at twenty pixels.
        AssetKind.TRACK -> {
            val path = Path().apply {
                moveTo(x(0.14f), y(0.86f))
                cubicTo(x(0.32f), y(0.30f), x(0.68f), y(0.70f), x(0.86f), y(0.14f))
            }
            drawPath(path, color, style = Stroke(width = drawnStroke, cap = StrokeCap.Round))
        }

        // A bike track: two wheels joined by a frame, so the kind that is ridden is told from the
        // kind that is walked even where both are a line on the map.
        AssetKind.BIKE_TRACK -> {
            listOf(0.28f, 0.72f).forEach { wheel ->
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.15f + grow,
                    center = Offset(x(wheel), y(0.72f)),
                    style = Stroke(width = drawnStroke)
                )
            }
            line(0.28f, 0.72f, 0.50f, 0.40f)
            line(0.50f, 0.40f, 0.72f, 0.72f)
            line(0.50f, 0.40f, 0.36f, 0.30f)
        }

        // A roof over walls: what a hut looks like from the air, and from the ground.
        AssetKind.HUT -> {
            val roof = Path().apply {
                moveTo(x(0.10f), y(0.46f))
                lineTo(x(0.50f), y(0.12f))
                lineTo(x(0.90f), y(0.46f))
            }
            drawPath(roof, color, style = Stroke(width = drawnStroke, cap = StrokeCap.Round))
            box(left = 0.26f, top = 0.50f, boxWidth = 0.48f, boxHeight = 0.38f)
        }

        // A tent: a ridge and the two sides that fall from it. It is a plain triangle beside the hut's
        // roof over walls, which is all the two need to be told apart at twenty pixels.
        AssetKind.CAMPSITE -> {
            val tent = Path().apply {
                moveTo(x(0.18f), y(0.78f))
                lineTo(x(0.50f), y(0.24f))
                lineTo(x(0.82f), y(0.78f))
                close()
            }
            drawPath(tent, color, style = Stroke(width = drawnStroke, cap = StrokeCap.Round))
        }

        // A dot in a ring: something is here, and nothing beyond that. The plainest of the five on
        // purpose, because a waypoint is the catch-all and what it has to say is "something is here".
        AssetKind.WAYPOINT -> {
            drawCircle(
                color = color,
                radius = size.minDimension * 0.36f,
                style = Stroke(width = drawnStroke)
            )
            // The dot is filled, so the rim around it is a bigger circle rather than a fatter line.
            drawCircle(color = color, radius = size.minDimension * 0.10f + grow)
        }
    }
}

/**
 * "#RRGGBB" to a Compose colour, used to keep map and UI colours identical.
 *
 * Here rather than in the drawing code of a screen because both need it and one of them is this
 * package: a glyph is coloured from [AssetColors]'s strings on the phone and from the map's own
 * kind strings on the map, and those two have to come out as the same colour.
 */
internal fun parseHexColor(hex: String): Color = runCatching {
    Color(hex.removePrefix("#").toLong(16) or 0xFF000000L)
}.getOrDefault(Color.Gray)
