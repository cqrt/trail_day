package nz.mckenzie.trailday.domain.geo

import kotlin.math.floor

/**
 * How fast a trip was going, and how much of it was actually going anywhere.
 *
 * **One arithmetic, two readers.** A trip's page prints these numbers, and the recorder decides from
 * them whether the pass was a walk or a ride when it is saved - so a page reading "6.0 km/h" and a tile
 * saying "ride" have to be the same answer about the same trip. Two copies of "distance over moving
 * time" would be two answers, and the one that fell behind would be whichever the walker looked at less.
 *
 * **Moving time, not elapsed time.** A walker who stopped for twenty minutes did not spend them walking,
 * and the pauses are on the recording rather than guessed at, so they are taken off. A walk with no
 * pauses stored is all moving time, which is what most walks are.
 *
 * **One decimal, because that is what is printed.** The rule that says 6.0 km/h is a ride is compared
 * against the number the page shows, so 5.96 cannot slip under it as 6.0 on one screen and a walk on
 * the other.
 */
object AverageSpeed {

    /**
     * How long a pass spent moving: **the end less the start, less the pauses**.
     *
     * A pause that was never closed - a pass finished while paused, or one picked up days later - runs
     * to the recording's own end, because the walker said they had stopped and nothing says they
     * carried on. A pause with no end *and* no end to the recording has no length to claim, and the
     * answer is 0: no end at all is a file with no times in it, which says nothing about how long
     * anything was.
     *
     * A pass whose pauses add up to more than its length answers 0 rather than a negative number: a
     * stored pause can lie outside the fixes (the walker paused, the phone was put away, the pass was
     * finished from the notification), and no walker believes they moved for minus four minutes.
     */
    fun movingMs(
        startedAtEpochMs: Long,
        endedAtEpochMs: Long?,
        breaks: List<RecordingBreak>
    ): Long {
        val end = endedAtEpochMs ?: return 0L
        val totalMs = (end - startedAtEpochMs).coerceAtLeast(0L)
        val pausedMs = breaks.sumOf { pause ->
            ((pause.toEpochMs ?: end) - pause.fromEpochMs).coerceAtLeast(0L)
        }
        return (totalMs - pausedMs).coerceAtLeast(0L)
    }

    /**
     * The raw average: kilometres an hour, or null when there is nothing to divide by.
     *
     * Null rather than zero, because "no speed" and "standing still" are different things: a file with
     * no times says nothing about how fast the walk was, where a walk with a distance and no moving time
     * is a walk that went nowhere. The page prints a dash for both.
     */
    fun kmh(distanceM: Double, movingMs: Long): Double? {
        if (distanceM <= 0.0 || movingMs <= 0L) return null
        return distanceM / 1_000.0 / (movingMs / 3_600_000.0)
    }

    /**
     * The average as the page prints it: [kmh] rounded half-up to one decimal.
     *
     * Half-up rather than a tie to the nearest even number, because a person reading "5.9" and "6.0"
     * expects the boundary to be exactly 5.95. The rounding is done on the number that will be printed,
     * and the walk-or-ride rule is compared against the same number, so the two cannot disagree.
     */
    fun shownKmh(distanceM: Double, movingMs: Long): Double? {
        val raw = kmh(distanceM, movingMs) ?: return null
        return floor(raw * 10.0 + 0.5) / 10.0
    }
}
