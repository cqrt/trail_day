package nz.mckenzie.trailday.domain.geo

/**
 * The heights a height chart draws across: the line's own range, widened when that range is small.
 *
 * **A chart that always fills its box makes a hill out of a ripple.** Drawn against the measured lowest
 * and highest, a 20 m rise across a beach fills the same 96 dp as a 600 m climb, and the words beside it
 * are the only thing that says which one you are looking at. So the box is never narrower than
 * [MIN_SPAN_M]: it is centred on the middle of the measured range, and a line with less ground than that
 * in it is drawn small, in the middle, with air above and below.
 *
 * **The measurement does not move.** The lowest, the highest, the climb and the descent are still the
 * measured ones, [ElevationPhrase] says them, and the time estimate is worked out from them - this is a
 * way of drawing, like [ElevationShape], and nothing reads it back. A line with [MIN_SPAN_M] or more of
 * ground in it gets exactly the box it always had.
 *
 * One hundred metres is a small hill: below it the ground is undulation rather than a climb, so it is
 * drawn as undulation. It was chosen against the existing examples - a 100 m range draws unchanged - so
 * a walk whose hills used to fill the box still fills it.
 */
data class ElevationBox(val lowestM: Double, val highestM: Double) {

    /** The height drawn across, which is never zero and never less than [MIN_SPAN_M]. */
    val spanM: Double get() = highestM - lowestM

    companion object {

        /** The least ground a chart shows, in metres of height. */
        const val MIN_SPAN_M = 100.0

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
