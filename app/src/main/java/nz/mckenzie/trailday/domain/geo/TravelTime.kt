package nz.mckenzie.trailday.domain.geo

import kotlin.math.ceil
import kotlin.math.max

/**
 * How the ground is being covered: on foot, or on a bike.
 *
 * The two are not a preference - a walker's hour and a rider's hour are different distances - so every
 * estimate of time carries the one it was made for, and says which in words a row can print.
 */
enum class TravelMode(
    /** What a sentence calls it: "on foot", "riding". */
    val phrase: String,
    /** How fast the flat is covered, in metres a second. */
    val flatMps: Double,
    /** How fast height is gained, in metres a second. */
    val climbMps: Double
) {
    /** 5 km/h on the flat and 600 m an hour up: Naismith's rule, which New Zealand trampers plan with. */
    WALKING(phrase = "on foot", flatMps = 5_000.0 / 3600.0, climbMps = 600.0 / 3600.0),

    /** 12 km/h on the flat and 450 m an hour up: a mountain bike on a track, not a road bike on tarmac. */
    RIDING(phrase = "riding", flatMps = 12_000.0 / 3600.0, climbMps = 450.0 / 3600.0)
}

/**
 * How long a stretch of ground takes - **one piece of arithmetic for the whole app**.
 *
 * Three screens ask this question and they must not each have their own answer: the card that says what is
 * nearest, a place's own page, and the target the recorder follows. Two copies of "how fast does a walker
 * walk" would drift, and the walker would be told forty minutes by one screen and an hour by the other
 * about the same hill.
 *
 * **The rule is Naismith's**: a flat speed plus an hour for every 600 m climbed. It is the estimate used on
 * New Zealand tracks, it needs nothing but a map and a pair of legs, and it errs generous - a party that
 * arrives early is pleased, and one that planned on the best case is late in the dark.
 *
 * **Descent is not counted.** Naismith does not, and on the ground this app is for, going down is often as
 * slow as going up: a descent term would make the figure look precise about something nobody agrees on.
 *
 * **The answer is coarse on purpose.** Up to twenty minutes it is whole minutes; past that it is rounded
 * up to five, because "2h 45m" is what a person plans with and "2h 43m" claims a precision the rule does
 * not have. It rounds *up*, never down, so an estimate is not a promise.
 */
object TravelTime {

    /**
     * How long [distanceM] of ground with [climbM] of ascent takes, in seconds, or zero for no distance.
     *
     * A negative climb - a descent - is left out rather than speeding the walk up, for the reason above.
     */
    fun seconds(distanceM: Double, climbM: Double, mode: TravelMode): Long {
        if (distanceM <= 0.0) return 0L
        val flat = distanceM / mode.flatMps
        val up = max(climbM, 0.0) / mode.climbMps
        return minutesOf((flat + up) / 60.0) * 60L
    }

    /** Whole minutes, coarse past twenty, and never under a minute while there is ground to cover. */
    private fun minutesOf(minutes: Double): Long {
        val whole = ceil(minutes)
        val rounded = if (whole >= COARSE_ABOVE_MINUTES) ceil(whole / COARSE_TO_MINUTES) * COARSE_TO_MINUTES else whole
        return rounded.toLong().coerceAtLeast(1L)
    }

    private const val COARSE_ABOVE_MINUTES = 20.0
    private const val COARSE_TO_MINUTES = 5.0
}
