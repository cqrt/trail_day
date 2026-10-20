package nz.mckenzie.trailday.domain.doc

import java.util.Locale

/**
 * One of DOC's bike trails, paired with the track it belongs to.
 *
 * DOC publishes its bike material as a point, not a line: a place where riding is permitted, with a
 * difficulty and a time. Most of those points sit on a track of the same name. The pair is the useful
 * thing - a track that can be ridden - so the point is read as a note about the track rather than as a
 * second destination on the map.
 */
data class DocBikeLink(
    val track: DocItem,
    val bike: DocItem,
    /** How far the bike point sits from the track's line, in metres. */
    val distanceM: Double
)

/**
 * The bike points that belong to tracks, and the rule that pairs them.
 *
 * **A name alone is not enough.** DOC uses the same name for different tracks in different places -
 * two live examples share a name with a track nine kilometres away. The name narrows the candidates;
 * the point has to be near the line to be a link. The distance is deliberately short: every real pair
 * checked against DOC's live services sat within a few metres of its line, while the same-name wrong
 * ones were hundreds of metres to kilometres away.
 *
 * **Derived, never stored.** The two services are downloaded separately and can change independently,
 * and a cache may hold one without the other. Reading the pair from what is on the phone each time
 * means a fresh download updates the answer and an incomplete cache never invents one.
 */
class DocBikeLinks private constructor(val pairs: List<DocBikeLink>) {

    /** The references of the bike points that are drawn as a track's badge rather than as a place. */
    val linkedBikeRefs: Set<String> = pairs.mapTo(mutableSetOf()) { it.bike.sourceRef }

    private val byTrackRef: Map<String, List<DocBikeLink>> =
        pairs.groupBy { it.track.sourceRef }

    /** The bike point that best belongs to [track], or null when DOC's bike data has none for it. */
    fun bikeFor(track: DocItem): DocItem? =
        byTrackRef[track.sourceRef]?.minByOrNull { it.distanceM }?.bike

    /** Every bike point paired with [track], nearest to the line first. */
    fun bikesFor(track: DocItem): List<DocItem> =
        byTrackRef[track.sourceRef].orEmpty().sortedBy { it.distanceM }.map { it.bike }

    /** The tracks that can be ridden, by reference: the ones with at least one bike point. */
    val linkedTrackRefs: Set<String> get() = byTrackRef.keys

    val isEmpty: Boolean get() = pairs.isEmpty()

    companion object {

        /**
         * How near a bike point has to be to a track's line to belong to it, in metres.
         *
         * A hundred metres is generous for DOC's points, which sat on their lines in the live sample,
         * and far short of the same-name tracks kilometres away that must not be linked.
         */
        const val MAX_DISTANCE_M = 100.0

        val EMPTY = DocBikeLinks(emptyList())

        /**
         * Pairs [bikes] with [tracks] by normalised name and proximity.
         *
         * The caller passes DOC's material as it was read; anything from another source is ignored.
         */
        fun of(
            tracks: List<DocItem>,
            bikes: List<DocItem>,
            maxDistanceM: Double = MAX_DISTANCE_M
        ): DocBikeLinks {
            val tracksByKey = tracks
                .filter { it.source == DocSource.TRACKS }
                .groupBy { nameKey(it.name) }

            val pairs = bikes
                .asSequence()
                .filter { it.source == DocSource.BIKES }
                .mapNotNull { bike ->
                    val point = bike.point ?: return@mapNotNull null
                    val near = tracksByKey[nameKey(bike.name)]
                        .orEmpty()
                        .mapNotNull { track ->
                            val distance = DocHitTest.distanceTo(track, point)
                                ?: return@mapNotNull null
                            if (distance <= maxDistanceM) track to distance else null
                        }
                        .minByOrNull { (_, distance) -> distance }
                        ?: return@mapNotNull null
                    DocBikeLink(track = near.first, bike = bike, distanceM = near.second)
                }
                .toList()

            return if (pairs.isEmpty()) EMPTY else DocBikeLinks(pairs)
        }

        /**
         * The words [name] is matched by: case, spacing, punctuation and non-breaking spaces gone.
         *
         * DOC's own names carry trailing spaces and typographic punctuation; a name is the same name
         * whatever a text field did to its whitespace. Letters keep their accents and macrons, because
         * those are part of the name.
         */
        internal fun nameKey(name: String): String =
            name.lowercase(Locale.ROOT)
                .replace(NOT_A_NAME, " ")
                .trim()
                .replace(SPACES, " ")

        private val NOT_A_NAME = Regex("[^\\p{L}\\p{N}]+")
        private val SPACES = Regex(" +")
    }
}
