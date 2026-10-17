package nz.mckenzie.trailday.domain.nearby

import nz.mckenzie.trailday.domain.geo.TravelMode
import nz.mckenzie.trailday.domain.geo.TravelTime

/**
 * One thing the map can be nearest to: a place of the walker's, or something DOC publishes.
 *
 * Only what the card shows: **what it is, what it is called, and how far away it is**. It carries no
 * geometry and no DOC fields - a card that listed a hut's facilities would be a page, and this is the
 * answer to a glance.
 */
data class NearbyThing(
    val name: String,
    /** What it is, in the app's words: "hut", "track", "bike location", "waypoint". */
    val what: String,
    /** How far away it is, in metres, on the ground - to the line itself for a line. */
    val distanceM: Double,
    /** The place DOC puts it in, when DOC is who published it; null for the walker's own. */
    val place: String? = null,
    /** The walker's own place, so the card can open it; null for something DOC publishes. */
    val assetId: Long? = null
) {

    /**
     * How long getting there takes on foot.
     *
     * The straight line the card knows and no climb it does not: a hut over the next spur is further than
     * this says, and the card is a glance rather than a route.
     */
    fun onFootSeconds(): Long = TravelTime.seconds(distanceM, climbM = 0.0, mode = TravelMode.WALKING)
}

/**
 * What is around the walker: the nearest thing, and everything within a few kilometres of them.
 *
 * The map's answer to "am I where I think I am" - a hut's name and distance where the walker was expecting
 * one is the quickest confirmation there is that the map, the phone and the hill agree.
 *
 * **Nothing in range is nothing to show**, not a card saying so: the card is for telling the walker what is
 * around them, and in the backcountry the absence of it is the truth they can see on the map already.
 */
data class Nearby(
    /** The nearest of them all. */
    val nearest: NearbyThing,
    /** Everything within the radius, nearest first - including [nearest]. */
    val within: List<NearbyThing>,
    val radiusM: Double
) {

    /**
     * How many of each thing are within the radius, as words: "6 tracks", "2 huts", "1 campsite", the
     * commonest first.
     *
     * Counted from [within], which includes the nearest one, so the card never says "nearest hut" and then
     * "0 huts within 5 km" about the same hut. Singular and plural are decided here rather than by whoever
     * prints it, so "1 campsite" cannot come out as "1 campsites".
     */
    val counted: List<String>
        get() = within
            .groupingBy { it.what }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { (what, count) -> "$count ${if (count == 1) what else plural(what)}" }

    companion object {

        /** How far around the walker the card counts: five kilometres, the mock's own answer. */
        const val RADIUS_M = 5_000.0

        /**
         * What is within [radiusM] of the walker, or null when nothing is.
         *
         * A thing exactly on the radius counts: the radius is a promise about what is inside it, and a hut
         * at five kilometres is a hut five kilometres away rather than a hut that is not there.
         */
        fun of(things: List<NearbyThing>, radiusM: Double = RADIUS_M): Nearby? {
            val within = things.filter { it.distanceM <= radiusM }.sortedBy { it.distanceM }
            val nearest = within.firstOrNull() ?: return null
            return Nearby(nearest = nearest, within = within, radiusM = radiusM)
        }

        /** "hut" becomes "huts"; every word this app calls a thing pluralises that way. */
        private fun plural(what: String): String = what + "s"
    }
}
