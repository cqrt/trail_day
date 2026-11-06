package nz.mckenzie.trailday.domain.geo

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * The shape a height chart draws, worked out from the heights the line was taken with.
 *
 * A recorded walk is a fix every few metres and a phone's height is noisy, so the measured heights
 * drawn straight are a sawtooth with the ground buried in it. This is the ground: each sample is
 * replaced by the mean of the heights around it - a window [WINDOW_M] wide, half of it either side -
 * so fix-to-fix jitter averages out and a climb reads as a climb.
 *
 * **It is a way of drawing the samples, not a second reading of them.** Nothing here changes what the
 * profile says was the lowest, the highest, the climb or the descent, and nothing here reaches the
 * points the walk was recorded with or the file written back out. [of] is called by whatever is
 * about to draw, and by nothing that measures.
 *
 * The first and last samples keep their measured heights, so the line starts and ends where the walk
 * did. [alongM][ElevationSample.alongM] never moves, so the chart's own distance scale is unchanged.
 */
object ElevationShape {

    /**
     * The width of the ground each sample is averaged over: half of it either side.
     *
     * Thirty metres, chosen by eye on a real walk rather than from a noise model. The Kiwi Burn
     * track, 3,456 fixes over 12 km: at 200 m its small turns were gone and the line read as a smooth
     * ridge that the ground is not, and at 0 the fix-to-fix jitter was the line. Thirty keeps the
     * turns and takes the jitter - four or five fixes either side at a fix every few metres.
     *
     * The cost is anything much shorter than the window: a ten-metre knoll is muted, where 200 m
     * flattened a fifty-metre one. That is the trade the line is drawn on.
     */
    const val WINDOW_M = 30.0

    /**
     * The most samples a chart is handed.
     *
     * A chart is a few hundred pixels wide and the desk draws on a 100-unit grid, so a long ride's
     * thousands of samples cost document size and show nothing. Past this, [thin] keeps an extreme
     * from each bucket rather than every sample.
     */
    const val MAX_SAMPLES = 480

    /**
     * [samples] as the ground under them, or untouched when there is nothing to average.
     *
     * Fewer than three samples has no middle to average, and a window of nothing is not a window.
     * The returned list's metres are the input's own, so a caller can hand it straight to a chart.
     */
    fun of(samples: List<ElevationSample>, windowM: Double = WINDOW_M): List<ElevationSample> {
        if (windowM <= 0.0 || samples.size < 3) return samples

        val half = windowM / 2.0
        val smoothed = ArrayList<ElevationSample>(samples.size)
        var lo = 0
        var hi = 0
        var sum = 0.0
        for (i in samples.indices) {
            val centre = samples[i].alongM
            while (hi < samples.size && samples[hi].alongM <= centre + half) {
                sum += samples[hi].heightM
                hi++
            }
            while (samples[lo].alongM < centre - half) {
                sum -= samples[lo].heightM
                lo++
            }
            smoothed += ElevationSample(centre, sum / (hi - lo))
        }

        smoothed[0] = samples.first()
        smoothed[smoothed.lastIndex] = samples.last()
        return thin(smoothed)
    }

    /**
     * [samples] at [MAX_SAMPLES] or fewer, keeping the shape rather than every point of it.
     *
     * Each bucket keeps the sample that departs furthest from where the bucket began, because a spike
     * is what a chart is read for and the flat metres around it are not. The first and last samples
     * are kept on top of that, so the line still starts and ends where the walk did - the first
     * bucket's own choice would otherwise begin the line a few metres in. A line already short enough
     * is returned as it is.
     */
    private fun thin(samples: List<ElevationSample>): List<ElevationSample> {
        if (samples.size <= MAX_SAMPLES) return samples

        val step = ceil(samples.size.toDouble() / MAX_SAMPLES).toInt()
        val kept = ArrayList<ElevationSample>(MAX_SAMPLES + 2)
        var start = 0
        while (start < samples.size) {
            val end = min(start + step, samples.size)
            val from = samples[start].heightM
            var best = samples[start]
            var departure = 0.0
            for (i in start until end) {
                val away = abs(samples[i].heightM - from)
                if (away > departure) {
                    departure = away
                    best = samples[i]
                }
            }
            kept += best
            start = end
        }

        if (kept.first() != samples.first()) kept.add(0, samples.first())
        if (kept.last() != samples.last()) kept += samples.last()
        return kept
    }
}
