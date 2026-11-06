package nz.mckenzie.trailday.map

/**
 * The level a framed box lands on.
 *
 * Three things ask, and only three: the box's own fit, which is the answer on every map but one; a cap
 * on how close a fit may come, which is one thing's own page at 15; and a level the walker was already
 * reading at, which is the recorder coming back to its tab.
 *
 * A level the walker chose outranks both, because the two are answers to different questions: the box
 * is where, the level is how close. That is also why a kept level is not capped - a cap exists to stop
 * a box being framed too tight, and a level the walker pinched to is not a box being framed at all.
 */
internal fun fitZoom(fitted: Double, cap: Double? = null, kept: Double? = null): Double =
    kept ?: cap?.let { minOf(fitted, it) } ?: fitted
