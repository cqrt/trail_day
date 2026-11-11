package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nz.mckenzie.trailday.domain.geo.ElevationAxis
import nz.mckenzie.trailday.domain.geo.ElevationProfile
import nz.mckenzie.trailday.domain.geo.ElevationSample
import nz.mckenzie.trailday.domain.geo.ElevationShape
import kotlin.math.roundToInt

/**
 * A line's height along its own length: the shape of the ground it was walked or driven on.
 *
 * The arithmetic is [ElevationProfile]'s, done once in the domain; this only puts it on the screen,
 * scaling the distance along the line to the width and the height between the box's lowest and highest
 * points to the height. The line drawn is [ElevationShape]'s, so a fix-to-fix sawtooth reads as the
 * ground under it. **The box is [ElevationAxis.heightAxis]'s**, which is the measured range widened to
 * a hundred and eighty metres when the line has less than that in it: a ripple is drawn as a ripple
 * rather than filling the height like a mountain, and a line flatter than the box sits across its middle
 * with air above and below.
 *
 * **The chart is labelled, and the labels are small.** There is not much room on a card that is 96 dp
 * tall, so the numbers are 8 sp: the heights down the inside of the left edge, over the fill and kept
 * legible by a white halo, and the distances along the foot in kilometres. The ticks are
 * [ElevationAxis]'s - round numbers, chosen so that as few as possible are needed - and the height ones
 * are drawn against the box, so the top and bottom of the drawing are not a measurement and the numbers
 * that are there are. The distance axis takes 16 dp from the bottom of the card.
 *
 * **A finger run along it names a place on the line**, reported in metres along the line - the same
 * scale the chart is drawn against - so a screen with a map above can put a dot there. See
 * [fingerAtM] and [onFinger]. This one only says *where along*; what that is on the ground is the
 * line's own arithmetic.
 */
@Composable
fun ElevationChart(
    profile: ElevationProfile,
    modifier: Modifier = Modifier,
    /**
     * Where the finger is, in metres along the line, or null when there is none.
     *
     * Held by the screen rather than here, because the dot on the map above is drawn from the same
     * number: one finger, one place, whichever of the two is being read.
     */
    fingerAtM: Double? = null,
    /**
     * A finger has arrived on the chart, moved, or left it.
     *
     * Null when it has left, which is the caller's cue to take the dot off the map.
     */
    onFinger: (Double?) -> Unit = {}
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val hairlineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val inkColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val ruleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    // The card the chart is drawn on, for the halo round a label and the disc under the finger's dot:
    // Card's own container, so the two are not a shade apart from the thing behind them.
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLow
    val samples = remember(profile) { ElevationShape.of(profile.samples) }
    // The box and the numbers down its edge, with one more number when the highest point would
    // otherwise sit above the last of them. See [ElevationAxis.heightAxis].
    val axis = remember(profile) { ElevationAxis.heightAxis(profile) }
    val box = axis.box
    val heightTicks = axis.ticks
    val density = LocalDensity.current
    val edgePx = with(density) { EDGE.toPx() }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = LABEL_SIZE, color = inkColor)
    val haloStyle = labelStyle.copy(color = cardColor)
    val currentOnFinger by rememberUpdatedState(onFinger)

    Canvas(
        modifier = modifier.pointerInput(profile) {
            // A **horizontal** drag, so the page still scrolls under a vertical one: the chart sits in a
            // scrolling column, and a graph that swallowed every drag would be a patch of the page that
            // could not be scrolled past. Compose cancels this gesture the moment the finger goes
            // further up or down than sideways, which is exactly the distinction wanted here.
            val firstAlongM = samples.first().alongM
            val lastAlongM = samples.last().alongM
            val spanM = (lastAlongM - firstAlongM).coerceAtLeast(1e-9)

            fun alongAt(x: Float): Double {
                // The same left edge and width the drawing uses, so the dot under the finger is under it.
                val usable = (size.width - edgePx * 2).coerceAtLeast(1f)
                val fraction = ((x - edgePx) / usable).coerceIn(0f, 1f)
                return firstAlongM + fraction * spanM
            }

            detectHorizontalDragGestures(
                onDragStart = { at -> currentOnFinger(alongAt(at.x)) },
                onDragEnd = { currentOnFinger(null) },
                onDragCancel = { currentOnFinger(null) }
            ) { change, _ ->
                currentOnFinger(alongAt(change.position.x))
                change.consume()
            }
        }
    ) {
        val first = samples.first()
        val last = samples.last()
        val alongSpan = (last.alongM - first.alongM).takeIf { it > 0.0 } ?: 1.0

        // The plot: the whole width less a little at each edge for a label, and the foot of the card
        // given to the distance axis.
        val left = edgePx
        val right = (size.width - edgePx).coerceAtLeast(left + 1f)
        val top = TOP.toPx()
        val bottom = (size.height - AXIS.toPx()).coerceAtLeast(top + 1f)
        val plotWidth = (right - left).coerceAtLeast(1f)
        val plotHeight = (bottom - top).coerceAtLeast(1f)

        // The box is never flat - a hundred and eighty metres at least - so this is one division and
        // not a special case for a line that climbs nothing.
        fun yFor(heightM: Double): Float =
            top + (1f - ((heightM - box.lowestM) / box.spanM).toFloat()) * plotHeight

        fun xFor(alongM: Double): Float =
            left + ((alongM - first.alongM) / alongSpan).toFloat() * plotWidth

        fun point(sample: ElevationSample): Offset = Offset(xFor(sample.alongM), yFor(sample.heightM))

        /** A label in the ink, with the card's colour behind it where it sits over the fill. */
        fun label(text: String, at: Offset, halo: Boolean) {
            val ink = textMeasurer.measure(text, labelStyle)
            if (halo) {
                val behind = textMeasurer.measure(text, haloStyle)
                val reach = 1.dp.toPx()
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        if (dx != 0 || dy != 0) {
                            drawText(
                                behind,
                                topLeft = at + Offset(dx * reach, dy * reach)
                            )
                        }
                    }
                }
            }
            drawText(ink, topLeft = at)
        }

        val outline = Path()
        val area = Path()
        val start = point(first)
        outline.moveTo(start.x, start.y)
        area.moveTo(start.x, bottom)
        area.lineTo(start.x, start.y)
        for (index in 1 until samples.size) {
            val at = point(samples[index])
            outline.lineTo(at.x, at.y)
            area.lineTo(at.x, at.y)
        }
        area.lineTo(point(last).x, bottom)
        area.close()

        drawPath(area, color = fillColor)

        // The height gridlines, under the line: a shape is read across to a number, not down to one.
        heightTicks.forEach { tick ->
            val y = yFor(tick)
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
        }

        drawPath(outline, color = lineColor, style = Stroke(width = 2.dp.toPx()))

        // The foot of the plot, and the distance ticks hanging off it.
        drawLine(ruleColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())

        val widthDp = size.width / density.density
        val distanceTarget = (widthDp / LABEL_EVERY_DP).roundToInt().coerceIn(3, 9)
        val distanceTicks = ElevationAxis.ticks(first.alongM, last.alongM, distanceTarget)
        distanceTicks.forEach { tick ->
            val x = xFor(tick)
            drawLine(ruleColor, Offset(x, bottom), Offset(x, bottom + 3.dp.toPx()), 1.dp.toPx())
        }

        // The heights, inside the left edge and over the fill. A label on the very top of the box - the one
        // that covers the highest point - hangs inside it rather than half outside the drawing.
        heightTicks.forEach { tick ->
            val text = "${tick.roundToInt()} m"
            val ink = textMeasurer.measure(text, labelStyle)
            val y = yFor(tick) - ink.size.height / 2f
            label(
                text = text,
                at = Offset(left + 3.dp.toPx(), y.coerceAtLeast(top + 1.dp.toPx())),
                halo = true
            )
        }

        // The distances, under the axis: the ends held inside the chart rather than hanging off it.
        distanceTicks.forEach { tick ->
            val text = String.format(java.util.Locale.US, "%.1f km", tick / 1000.0)
            val ink = textMeasurer.measure(text, labelStyle)
            val centred = xFor(tick) - ink.size.width / 2f
            label(
                text = text,
                at = Offset(
                    centred.coerceIn(left, (right - ink.size.width).coerceAtLeast(left)),
                    bottom + 6.dp.toPx()
                ),
                halo = false
            )
        }

        // The finger: a hairline down the plot and a dot on the ground beneath it, so the whole height is
        // one mark rather than a moving point with nothing under it. Held at the ends rather than drawn
        // outside the box the chart owns.
        val finger = fingerAtM
        if (finger != null) {
            val along = finger.coerceIn(first.alongM, last.alongM)
            val x = xFor(along)
            drawLine(
                color = hairlineColor,
                start = Offset(x, top),
                end = Offset(x, bottom),
                strokeWidth = 1.dp.toPx()
            )
            val y = yFor(heightAt(samples, along))
            // The card's own colour under the dot, so it reads against the fill even where the line is.
            drawCircle(color = cardColor, radius = 6.dp.toPx(), center = Offset(x, y))
            drawCircle(color = lineColor, radius = 4.5.dp.toPx(), center = Offset(x, y))
        }
    }
}

/**
 * The height the drawn line has at [alongM], between the two samples either side of it.
 *
 * The samples are [ElevationShape]'s, so the dot sits on the line that is on the screen rather than on a
 * measurement a few metres away from it.
 */
private fun heightAt(samples: List<ElevationSample>, alongM: Double): Double {
    if (alongM <= samples.first().alongM) return samples.first().heightM
    if (alongM >= samples.last().alongM) return samples.last().heightM

    var low = 0
    var high = samples.lastIndex
    while (high - low > 1) {
        val middle = (low + high) / 2
        if (samples[middle].alongM <= alongM) low = middle else high = middle
    }

    val before = samples[low]
    val after = samples[high]
    val span = after.alongM - before.alongM
    val fraction = if (span <= 0.0) 0.0 else (alongM - before.alongM) / span
    return before.heightM + (after.heightM - before.heightM) * fraction
}

/** The room left at each end of the chart, so a label at the inside edge is not at the card's edge. */
private val EDGE = 8.dp

/** The room above the plot, so a label at the top of the box is not cut in half. */
private val TOP = 6.dp

/** The foot of the card given to the distance axis: a rule, its ticks and their labels. */
private val AXIS = 16.dp

/** The size of every number on the chart, which is as large as a card this short will take. */
private val LABEL_SIZE = 8.sp
/** The width one distance label is given, in dp: the tick spacing follows from it. */
private const val LABEL_EVERY_DP = 45f
