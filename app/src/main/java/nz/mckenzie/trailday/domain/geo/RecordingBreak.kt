package nz.mckenzie.trailday.domain.geo

/**
 * A stretch of a pass the walker was paused for.
 *
 * Pausing stops the fixes, so a pass that carries on always jumps from the last fix before
 * the pause to the first one after it. Every other jump of that kind is the fixes going
 * missing rather than the driving stopping - under trees, in a gully, with the phone in a
 * pocket - and the ground between two fixes the pass drove is ground it covered, so the
 * recorded line is drawn through it. A pause is different in the one way that matters: the
 * walker said they had stopped, so the recorded line is drawn in two pieces there.
 *
 * [toEpochMs] is null while the pass is still paused, and for a pass that was finished while
 * paused - which reads as a stop that runs to the end of the recording, as it should.
 */
data class RecordingBreak(
    val fromEpochMs: Long,
    /** When the pass carried on, or null for a break that never ended - see [fallsBetween]. */
    val toEpochMs: Long? = null
) {

    /**
     * Whether this break falls between two fixes: whether the walker was stopped at some
     * point on the way from one to the other.
     *
     * A break that never ended is taken to run to the end of the recording. A pass finished
     * while paused has nothing after the pause either way, and one that is picked up days
     * later still has a stop in it.
     */
    fun fallsBetween(fromEpochMs: Long, toEpochMs: Long): Boolean =
        this.fromEpochMs <= toEpochMs && (this.toEpochMs ?: Long.MAX_VALUE) >= fromEpochMs
}

/**
 * The recorded line as the pieces the breaks divide it into: one piece for a pass that was
 * never paused, which is the usual case.
 *
 * A piece of a single fix is not a line and is dropped, so a pause pressed before anything
 * was driven cannot make a dot appear where the line should start.
 */
fun List<GeoPoint>.splitAtBreaks(breaks: List<RecordingBreak>): List<List<GeoPoint>> {
    if (breaks.isEmpty() || size < 2) return listOf(this)

    val pieces = mutableListOf<List<GeoPoint>>()
    var start = 0
    for (index in 1 until size) {
        if (breaks.any { it.fallsBetween(this[index - 1].timeMs, this[index].timeMs) }) {
            pieces += subList(start, index)
            start = index
        }
    }
    pieces += subList(start, size)

    return pieces.filter { it.size >= 2 }.ifEmpty { listOf(this) }
}
