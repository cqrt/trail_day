package nz.mckenzie.trailday.domain.recording

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.geo.AverageSpeed
import nz.mckenzie.trailday.domain.geo.TravelMode

/**
 * What a trip was: **a walk or a ride**.
 *
 * Both are activities, and the tabs keep the word "activity", because a ride is not a second kind of
 * record - it is the same line, the same page and the same list, with the one fact that changes how it
 * looks and how its ground is estimated.
 *
 * **A trip's mode is the trip's own answer, not its place's kind.** A place is a line to travel along
 * and can be a bike track; a trip is a thing that happened, and a walk along a bike track is a walk.
 * The two are kept apart on purpose, and the only place they meet is [assetKind] - a trip saved as a
 * new place makes the place its mode says it is.
 *
 * The mapping to the map's own vocabulary - the place kind that decides colour and dash, and the
 * travel mode that decides how long the ground takes - lives here rather than in the map or the page,
 * so there is one answer to "what is a ride drawn as" and one to "how fast does a rider go".
 */
enum class ActivityMode(
    /** The word for one, as a sentence uses it: "a walk", "a ride". */
    val one: String,
    /** And for several: "2 walks", "3 rides". */
    val many: String
) {
    WALK(one = "walk", many = "walks"),

    RIDE(one = "ride", many = "rides");

    /**
     * The place a trip of this mode makes when it is recorded as a new line, and the kind whose colour
     * and dash the trip is drawn in wherever a place's kind decides that.
     */
    val assetKind: AssetKind
        get() = when (this) {
            WALK -> AssetKind.TRACK
            RIDE -> AssetKind.BIKE_TRACK
        }

    /** How this mode's ground is covered: the speed and climb the time estimate is made for. */
    val travelMode: TravelMode
        get() = when (this) {
            WALK -> TravelMode.WALKING
            RIDE -> TravelMode.RIDING
        }

    companion object {

        /**
         * The speed a recorded pass becomes a ride at: **6.0 km/h, on the number the page shows.**
         *
         * A brisk walk is about 5.5 and a bike on a track is 12 and up, so the gap between them is
         * where the line has to go; 6.0 is inside the gap and is a figure a person can check against
         * the average on the page. What it gets wrong is on the record: a walker pushing a bike up a
         * hill at 4 km/h is a walk, a 6.2 km/h walk is a ride, and a trail run at 10 km/h is a ride.
         * The trip's page changes it, which is the answer rather than a bug.
         */
        const val RIDE_FROM_KMH = 6.0

        /**
         * The mode of a place's kind.
         *
         * Only two kinds are lines, and one of them is ridden. The three point kinds are not trips and
         * never reach this; they answer [WALK] rather than throwing, because a stored value the app did
         * not expect must not stop a page opening.
         */
        fun of(kind: AssetKind): ActivityMode = if (kind == AssetKind.BIKE_TRACK) RIDE else WALK

        /**
         * The mode a stored value means.
         *
         * Anything unrecognised reads as a walk - the rule [AssetKind.fromStorage] already uses, for
         * the same reason: a value written by a later build must not stop the app reading its own rows.
         */
        fun ofStorage(value: String?): ActivityMode =
            entries.firstOrNull { it.name == value } ?: WALK

        /**
         * The mode a recorded pass is, from the numbers its page will print.
         *
         * No distance or no moving time is a walk: there is no average to compare, and guessing a ride
         * from nothing would put a bike on somebody's stroll. The comparison is on the shown figure, so
         * a page reading 5.9 is a walk and one reading 6.0 is a ride.
         */
        fun fromSpeed(distanceM: Double, movingMs: Long): ActivityMode {
            val shown = AverageSpeed.shownKmh(distanceM, movingMs) ?: return WALK
            return if (shown >= RIDE_FROM_KMH) RIDE else WALK
        }
    }
}
