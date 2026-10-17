package nz.mckenzie.trailday.domain.geo

/**
 * Whether the walker counts as off the line they are following: **the quiet word's two distances.**
 *
 * A warning that flickers is a warning people stop reading, and the distance to a line goes in and out by
 * itself wherever a track touches the edge of a clearing. So going off is measured at one distance (150 m,
 * far enough that it is not the line being coarse) and coming back at another (75 m, half of it). The gap
 * between the two is what makes one drift one word rather than a sentence that appears and disappears with
 * every fix.
 *
 * **This is a state, not a notification.** While the walker is off, the word is there; being back on the
 * line takes it away. That is what the walker wants to glance at - "am I on it?" - rather than a message
 * they might have missed while it was on screen for four seconds.
 */
class DriftWord(
    /** How far off the line counts as drifting. */
    private val offM: Double = DRIFT_OFF_M,
    /** How close again counts as back on it. */
    private val backOnM: Double = BACK_ON_M
) {

    private var off = false

    /**
     * Whether the walker is off the line, given how far from it they are now.
     *
     * The first call past [offM] turns it on and every call short of [backOnM] turns it off; between the
     * two, the answer is whatever it already was.
     */
    fun isOff(currentOffM: Double): Boolean {
        if (!off && currentOffM >= offM) {
            off = true
        } else if (off && currentOffM <= backOnM) {
            off = false
        }
        return off
    }

    /** A new line: whatever was true of the last one is not true of this one. */
    fun reset() {
        off = false
    }

    companion object {
        /** Far enough off that it is not the line being coarse: a couple of hundred metres, give or take. */
        const val DRIFT_OFF_M = 150.0

        /** Close enough to be back on it, and half of the distance that started the word. */
        const val BACK_ON_M = 75.0
    }
}
