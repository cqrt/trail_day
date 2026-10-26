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
 * The bike points that belong to tracks, and the rules that pair them.
 *
 * **A name first, then the line itself.** DOC uses the same name for different tracks in different
 * places - two live examples share a name with a track nine kilometres away - so a name match has to
 * be confirmed by the point being near the line. But many bike points use a different name for a
 * track they sit on: a cycle trail and the tramping track it shares, or a trailhead name and the
 * track's own. Those are found by the second rule: **a bike point directly on top of a track belongs
 * to it**, whatever either is called. The live services had 122 of 124 name-unmatched points sitting
 * within ten metres of a track, so this is not a guess; it is how DOC publishes them.
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

    /** The tracks that can be ridden, by reference: the ones with at least one bike point. */
    val linkedTrackRefs: Set<String> get() = byTrackRef.keys

    val isEmpty: Boolean get() = pairs.isEmpty()

    companion object {

        /**
         * How near a bike point has to be to a track of the **same name** to belong to it, in metres.
         *
         * A hundred metres is generous for DOC's points, which sat on their lines in the live sample,
         * and far short of the same-name tracks kilometres away that must not be linked.
         */
        const val MAX_DISTANCE_M = 100.0

        /**
         * How near a bike point has to be to **any** track, whatever the names, to be on top of it.
         *
         * The live services put 122 of 124 name-unmatched bike points within ten metres of a track.
         * Twenty-five metres leaves room for the point DOC chose at a trailhead without reaching the
         * next track along.
         */
        const val ON_TOP_M = 25.0

        val EMPTY = DocBikeLinks(emptyList())

        /**
         * Pairs [bikes] with [tracks]: by name and proximity first, then by the point sitting on a line.
         *
         * The caller passes DOC's material as it was read; anything from another source is ignored.
         */
        fun of(
            tracks: List<DocItem>,
            bikes: List<DocItem>,
            maxDistanceM: Double = MAX_DISTANCE_M,
            onTopM: Double = ON_TOP_M
        ): DocBikeLinks {
            val docTracks = tracks.filter { it.source == DocSource.TRACKS }
            val tracksByKey = docTracks.groupBy { nameKey(it.name) }
            // A cheap box round each track keeps the no-name search from measuring every line on the
            // phone for every bike point. Only boxes near a point are measured exactly.
            val boxes = docTracks.mapNotNull { track -> boxOf(track)?.let { track to it } }

            val pairs = bikes
                .asSequence()
                .filter { it.source == DocSource.BIKES }
                .mapNotNull { bike ->
                    val point = bike.point ?: return@mapNotNull null
                    // The name narrows the candidates; the point still has to be near the line.
                    val named = tracksByKey[nameKey(bike.name)]
                        .orEmpty()
                        .mapNotNull { track ->
                            val distance = DocHitTest.distanceTo(track, point)
                                ?: return@mapNotNull null
                            if (distance <= maxDistanceM) track to distance else null
                        }
                        .minByOrNull { (_, distance) -> distance }

                    // A point sitting on a line belongs to that line, whatever DOC called the bike
                    // trail: a cycle trail on the tramping track it shares, or a trailhead name on
                    // the track's own. It wins when it is nearer than the name-matched line - a point
                    // at the edge of one track can name a track a few metres further away - and a
                    // name match wins a tie, because that is the stronger evidence.
                    val onTop = boxes
                        .asSequence()
                        .filter { (_, box) -> box.isNear(point) }
                        .mapNotNull { (track, _) ->
                            val distance = DocHitTest.distanceTo(track, point)
                                ?: return@mapNotNull null
                            if (distance <= onTopM) track to distance else null
                        }
                        .minByOrNull { (_, distance) -> distance }

                    val chosen = when {
                        named == null -> onTop
                        onTop == null -> named
                        onTop.second < named.second -> onTop
                        else -> named
                    } ?: return@mapNotNull null
                    DocBikeLink(track = chosen.first, bike = bike, distanceM = chosen.second)
                }
                .toList()

            return if (pairs.isEmpty()) EMPTY else DocBikeLinks(pairs)
        }

        /** Whether the track and the bike point are called the same thing, once DOC's spacing is gone. */
        fun sameName(track: DocItem, bike: DocItem): Boolean =
            nameKey(track.name) == nameKey(bike.name)

        private fun boxOf(track: DocItem): TrackBox? {
            val points = track.paths.flatten()
            if (points.isEmpty()) return null
            return TrackBox(
                minLat = points.minOf { it.lat },
                minLng = points.minOf { it.lng },
                maxLat = points.maxOf { it.lat },
                maxLng = points.maxOf { it.lng }
            )
        }

        private data class TrackBox(
            val minLat: Double,
            val minLng: Double,
            val maxLat: Double,
            val maxLng: Double
        ) {
            fun isNear(point: nz.mckenzie.trailday.domain.geo.GeoPoint): Boolean {
                // A tenth of a degree would be far too generous; this is about a hundred metres,
                // enough to include the line before measuring it exactly.
                val margin = 0.001
                return point.lat in (minLat - margin)..(maxLat + margin) &&
                    point.lng in (minLng - margin)..(maxLng + margin)
            }
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
