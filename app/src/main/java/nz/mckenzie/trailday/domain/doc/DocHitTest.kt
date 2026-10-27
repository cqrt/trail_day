package nz.mckenzie.trailday.domain.doc

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.nearestPathDistanceMeters

/**
 * What DOC publishes under the walker's finger.
 *
 * The map already answers "which of my places is this" for the walker's own work. This is the same question
 * asked of the reference layer beneath it - of the very lines and places DOC's material is drawn from - so a
 * tap that lands on one of DOC's tracks says which track it is, and a tap that lands on nothing says nothing.
 *
 * **Measured to the thing, not to its corner.** A line is as near as its nearest point on the line, which is
 * what the eye means by "on the track"; the distance to its nearest vertex would call a track through the
 * middle of a long straight stretch far away.
 */
object DocHitTest {

    /**
     * How near a tap has to be to count as on something, in metres, when the caller has no radius of its own.
     *
     * The map passes the radius its own zoom implies - generous at country scale, tight at walking scale - so
     * this default is only for callers without a map.
     */
    const val DEFAULT_TOLERANCE_M = 40.0

    /**
     * How close a place and a line have to be for the place to answer for both, in metres.
     *
     * A hut stands *on* the track that reaches it, and its marker is what the walker's finger is on. A line
     * that is a centimetre nearer would answer a different question from the one asked, so a place wins a
     * tie within this distance - and a tap well up a track is still a tap on the track, whatever hut is a
     * few metres off it.
     */
    private const val TIE_M = 5.0

    /**
     * The nearest of [items] whose line or place is within [radiusM] of the tapped point, or null when the tap
     * landed on nothing.
     *
     * The nearest rather than the first: two of DOC's tracks can meet at a junction, and the one the finger is
     * actually on is the one to say. A thing whose geometry will not measure is left out rather than counted
     * at zero.
     */
    fun nearest(
        items: List<DocItem>,
        lat: Double,
        lng: Double,
        radiusM: Double = DEFAULT_TOLERANCE_M
    ): DocItem? {
        val at = GeoPoint(lat = lat, lng = lng)
        val candidates = items.mapNotNull { item ->
            val distance = distanceTo(item, at) ?: return@mapNotNull null
            if (distance <= radiusM) item to distance else null
        }
        val nearest = candidates.minByOrNull { it.second } ?: return null
        if (!nearest.first.source.isLine) return nearest.first

        // A line won on distance. If a place is drawn at the same spot, that place is what the finger is
        // on - see [TIE_M].
        return candidates
            .filter { (item, distance) -> !item.source.isLine && distance <= nearest.second + TIE_M }
            .minByOrNull { it.second }
            ?.first
            ?: nearest.first
    }

    /**
     * How far [at] is from the nearest of [item]'s paths, in metres - or null when there is nothing to measure.
     *
     * The one distance rule for DOC's material: the card at the bottom of the map measures with it too, so
     * "4.71 km away" on the card and "the track under the finger" cannot disagree.
     */
    fun distanceTo(item: DocItem, at: GeoPoint): Double? =
        nearestPathDistanceMeters(item.paths, at)
}
