package nz.mckenzie.trailday.domain.other

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.nearestPathDistanceMeters
import nz.mckenzie.trailday.domain.reference.ReferenceFilter
import nz.mckenzie.trailday.domain.reference.ReferenceSort

/**
 * The facts a page of somebody else's tracks can be narrowed by, in the app's own words.
 *
 * Three, and they are the three questions somebody standing at a trailhead asks: what kind of track is
 * this, who publishes it, and can the dog come.
 *
 * **What is deliberately not offered** is the rest of what the dataset carries. Horse and off-road access
 * are "Unknown" or "not allowed" on almost every row; a spatial accuracy is a figure about somebody's
 * survey rather than about the walk; and a condition of use is a sentence to read, not a value to tick. A
 * track that is temporarily closed says so on its own row instead, which is a fact rather than a way to
 * narrow. And there is no filter by council area: Near me and the desk's own view already answer *where*,
 * and a territorial boundary is not how a walker thinks.
 */
val OTHER_TRACK_FILTERS: List<ReferenceFilter> = listOf(
    ReferenceFilter("Walk", "What it is"),
    ReferenceFilter("DataSupplier", "Who it's from"),
    ReferenceFilter("DogAccess", "Dogs")
)

/**
 * How far away a track is: the nearest point of its line, in metres - or null when there is nothing to
 * measure.
 *
 * **The same rule the map's tap uses**, so that a row saying "200 m away" and a tap that opens the track
 * beside you cannot be two answers to one question. DOC's own rows measure to the nearest *vertex*, which
 * is a different rule and a wart of its own; it is not this layer's to copy.
 */
fun OtherTrack.distanceM(point: GeoPoint): Double? = nearestPathDistanceMeters(paths, point)

/**
 * The values these rows carry for a filter's field, each once, sorted, blanks left out.
 *
 * Read from the page the search actually brought back, so every chip a filter offers is a fact about
 * something on the screen: a chip that matches nothing is a filter nobody can use.
 */
fun List<OtherTrack>.valuesPresent(field: String): List<String> =
    flatMap { track -> track.filterValues(field) }.distinct().sorted()

/**
 * The rows to show, in the order to show them.
 *
 * A blank filter means "everything". Something that cannot be measured - a row whose line will not
 * measure, or a search made with no fix to measure from - sorts **last** rather than first: putting it at
 * the top of a list of nearest would be answering the question with a shrug.
 */
fun List<OtherTrack>.showing(
    filters: Map<String, Set<String>> = emptyMap(),
    sort: ReferenceSort = ReferenceSort.NAME,
    from: GeoPoint? = null
): List<OtherTrack> {
    val filtered = filter { track ->
        // Every filter must agree with the row, and a filter with nothing ticked agrees with everything. A
        // row that answers one filter in more than one way - a track two councils both published - matches
        // if *any* of its answers was ticked, which is what narrowing by a council means.
        filters.all { (field, chosen) ->
            chosen.isEmpty() || track.filterValues(field).any { it in chosen }
        }
    }
    return when (sort) {
        ReferenceSort.NEAREST -> filtered.sortedWith(
            compareBy(
                { track -> from?.let { fix -> track.distanceM(fix) ?: Double.MAX_VALUE } ?: 0.0 },
                { track -> track.name.lowercase() }
            )
        )

        ReferenceSort.NAME -> filtered.sortedBy { it.name.lowercase() }
    }
}

/**
 * Only what is within [radiusM] of [from], and everything when there is no fix to measure from.
 *
 * The service is asked about a **box** around the walker - a rectangle is what a spatial query takes - and
 * a circle is what was meant, so the corners of the box are trimmed here rather than shown as if they were
 * near.
 */
fun List<OtherTrack>.within(radiusM: Double, from: GeoPoint?): List<OtherTrack> {
    if (from == null) return this
    return filter { track -> (track.distanceM(from) ?: Double.MAX_VALUE) <= radiusM }
}

/**
 * What was held that matches [nameContains], by name, case-blind.
 *
 * Here rather than in the store because it is a filter over what was read, not a database query: the rows
 * are already on the phone, and asking the database to search them would be a second way to answer a
 * question the app already answers in one place.
 */
fun List<OtherTrack>.matching(nameContains: String): List<OtherTrack> {
    val needle = nameContains.trim()
    if (needle.isEmpty()) return this
    return filter { it.name.contains(needle, ignoreCase = true) }
}
