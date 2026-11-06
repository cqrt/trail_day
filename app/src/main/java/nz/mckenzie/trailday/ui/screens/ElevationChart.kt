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
import androidx.compose.ui.unit.dp
import nz.mckenzie.trailday.domain.geo.ElevationProfile
import nz.mckenzie.trailday.domain.geo.ElevationSample
import nz.mckenzie.trailday.domain.geo.ElevationShape

/**
 * A line's height along its own length: the shape of the ground it was walked or driven on.
 *
 * The arithmetic is [ElevationProfile]'s, done once in the domain; this only puts it on the screen,
 * scaling the distance along the line to the width and the height between the lowest and highest
 * points to the height. The line drawn is [ElevationShape]'s, so a fix-to-fix sawtooth reads as the
 * ground under it; the box it is drawn in is still the measured lowest and highest, so the words
 * beside the chart describe the chart. A line that is flat - the highest point as low as the lowest -
 * is drawn down the middle rather than divided by nothing.
 *
 * It is a shape to read, not a survey: the height came off a device, so the chart is one colour and
 * no numbers live on it. The range is said in words beside it.
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
    val cardColor = MaterialTheme.colorScheme.surface
    val samples = remember(profile) { ElevationShape.of(profile.samples) }
    val padPx = with(LocalDensity.current) { PAD.toPx() }
    val currentOnFinger by rememberUpdatedState(onFinger)

    Canvas(
        modifier = modifier.pointerInput(profile) {
            // A **horizontal** drag, so the page still scrolls under a vertical one: the chart sits in a
            // scrolling column, and a graph that swallowed every drag would be a patch of the page that
            // could not be scrolled past. Compose cancels this gesture the moment the finger goes
            // further up or down than sideways, which is exactly the distinction wanted here.
            val pad = 4.dp.toPx()
            val firstAlongM = samples.first().alongM
            val lastAlongM = samples.last().alongM
            val spanM = (lastAlongM - firstAlongM).coerceAtLeast(1e-9)

            fun alongAt(x: Float): Double {
                val usable = (size.width - pad * 2).coerceAtLeast(1f)
                val fraction = ((x - pad) / usable).coerceIn(0f, 1f)
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
        val heightSpan = profile.rangeM.takeIf { it > 0.0 }
        val pad = 4.dp.toPx()
        val usableWidth = (size.width - pad * 2).coerceAtLeast(1f)
        val usableHeight = (size.height - pad * 2).coerceAtLeast(1f)

        fun yFor(heightM: Double): Float = if (heightSpan == null) {
            size.height / 2f
        } else {
            pad + (1f - ((heightM - profile.lowestM) / heightSpan).toFloat()) * usableHeight
        }

        fun point(sample: ElevationSample): Offset {
            val x = pad + ((sample.alongM - first.alongM) / alongSpan).toFloat() * usableWidth
            return Offset(x, yFor(sample.heightM))
        }

        val outline = Path()
        val area = Path()
        val start = point(first)
        outline.moveTo(start.x, start.y)
        area.moveTo(start.x, size.height)
        area.lineTo(start.x, start.y)
        for (index in 1 until samples.size) {
            val at = point(samples[index])
            outline.lineTo(at.x, at.y)
            area.lineTo(at.x, at.y)
        }
        area.lineTo(point(last).x, size.height)
        area.close()

        drawPath(area, color = fillColor)
        drawPath(outline, color = lineColor, style = Stroke(width = 2.dp.toPx()))

        // The finger: a hairline down the chart and a dot on the ground beneath it, so the whole height
        // is one mark rather than a moving point with nothing under it. Held at the ends rather than
        // drawn outside the box the chart owns.
        val finger = fingerAtM
        if (finger != null) {
            val along = finger.coerceIn(first.alongM, last.alongM)
            val x = pad + ((along - first.alongM) / alongSpan).toFloat() * usableWidth
            drawLine(
                color = hairlineColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
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

/** The room left at each end of the chart, so the line does not touch the card's edge. */
private val PAD = 4.dp
