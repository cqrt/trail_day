package nz.mckenzie.trailday.domain.geo

/**
 * The heights a height chart draws across: the line's own range, widened when that range is small.
 *
 * **A chart that always fills its box makes a hill out of a ripple.** Drawn against the measured lowest
 * and highest, a 20 m rise across a beach fills the same 96 dp as a 600 m climb, and the words beside it
 * are the only thing that says which one you are looking at. So the box is never narrower than
 * [MIN_SPAN_M]: it is centred on the middle of the measured range, and a line with less ground than that
 * in it is drawn in the middle, with air above and below.
 *
 * **The measurement does not move.** The lowest, the highest, the climb and the descent are still the
 * measured ones, [ElevationPhrase] says them, and the time estimate is worked out from them - this is a
 * way of drawing, like [ElevationShape], and nothing reads it back.
 *
 * **Fifty metres is the number.** Below it the ground is undulation rather than a climb, and a ripple
 * should not take the height of a mountain; above it a hill has earned the whole chart, and a line with
 * fifty metres or more in it is drawn exactly as it was before the floor existed.
 */
data class ElevationBox(val lowestM: Double, val highestM: Double) {

    /** The height drawn across, which is never zero and never less than [MIN_SPAN_M]. */
    val spanM: Double get() = highestM - lowestM

    companion object {

        /** The least ground a chart shows, in metres of height. */
        const val MIN_SPAN_M = 50.0

        fun of(profile: ElevationProfile): ElevationBox {
            if (profile.rangeM >= MIN_SPAN_M) {
                return ElevationBox(lowestM = profile.lowestM, highestM = profile.highestM)
            }
            val middle = (profile.lowestM + profile.highestM) / 2.0
            val half = MIN_SPAN_M / 2.0
            return ElevationBox(lowestM = middle - half, highestM = middle + half)
        }
    }
}
