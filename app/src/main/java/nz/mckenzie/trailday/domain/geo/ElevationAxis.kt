package nz.mckenzie.trailday.domain.geo

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * The numbers an axis is labelled with: as few of them as a reader needs, and only round ones.
 *
 * **A chart with a label at every fix is a chart with no labels at all**, so the ticks are chosen from
 * the 1, 2 and 5 series - 50 m, or 2 km - and the step is the smallest that keeps the count under
 * [target]. That is what makes a 12 km walk read in twos and a 900 m one in hundreds of metres, without
 * either screen keeping its own idea of a round number.
 *
 * A caller with no height to draw - a flat line, or a span of nothing - gets no ticks rather than a
 * division by zero.
 */
object ElevationAxis {

    /**
     * How far apart the ticks in [low]..[high] are, or zero when there is nothing to label.
     *
     * One of the 1, 2 and 5 series times a power of ten: the smallest step that keeps the count at or
     * under [target]. The chart asks for this separately from [ticks] because it needs the *next* one
     * along - a label above the highest point - as well as the ones inside the range.
     */
    fun step(low: Double, high: Double, target: Int): Double {
        if (!(high > low) || target <= 0) return 0.0

        val rough = (high - low) / target
        val magnitude = 10.0.pow(floor(log10(rough)))
        val normal = rough / magnitude
        return when {
            normal <= 1.0 -> 1.0
            normal <= 2.0 -> 2.0
            normal <= 5.0 -> 5.0
            else -> 10.0
        } * magnitude
    }

    /**
     * The round numbers in [low]..[high], no closer together than [target] of them along the axis.
     *
     * The first is the first multiple of the step inside the range, and the last is the last one inside
     * it, so every tick is a round number in the units the caller draws in - metres of height, or metres
     * along the line.
     */
    fun ticks(low: Double, high: Double, target: Int): List<Double> {
        val step = step(low, high, target)
        if (step <= 0.0) return emptyList()

        val ticks = ArrayList<Double>()
        var value = ceil(low / step) * step
        while (value <= high + 1e-9) {
            ticks += value
            value += step
        }
        return ticks
    }

    /**
     * The box a chart draws in, and the height labels along its left edge.
     *
     * **A peak with no number over it is a peak nobody can read.** The box is [ElevationBox]'s - the
     * measured range, widened to its floor and centred - and the labels are the round numbers inside it.
     * When the highest point of the line is past the last of those, the box is raised to the next one, so
     * the tallest ground on the walk always has a label at or above it. The box only ever grows here, so
     * the floor and the centring are otherwise exactly as they were, and the label that covers the peak
     * sits on the box's own top edge.
     */
    fun heightAxis(profile: ElevationProfile, target: Int = HEIGHT_TICKS): HeightAxis {
        val box = ElevationBox.of(profile)
        val step = step(box.lowestM, box.highestM, target)
        val ticks = ticks(box.lowestM, box.highestM, target)

        val topM = if (step > 0.0 && ticks.isNotEmpty() && profile.highestM > ticks.last()) {
            ticks.last() + step
        } else {
            box.highestM
        }

        return HeightAxis(
            box = box.copy(highestM = topM),
            ticks = if (topM > box.highestM) ticks + topM else ticks
        )
    }

    /** How much height a chart's own range is labelled with: as few as will place a point. */
    const val HEIGHT_TICKS = 4
}

/** The box a chart draws in, and the height labels down its left edge. */
data class HeightAxis(val box: ElevationBox, val ticks: List<Double>)
