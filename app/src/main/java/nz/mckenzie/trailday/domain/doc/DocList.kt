package nz.mckenzie.trailday.domain.doc

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.haversineMeters
import nz.mckenzie.trailday.domain.tiles.LatLngBounds

/**
 * Working out what to show of a page of DOC's material: how far away each thing is, and which of them
 * match what the walker asked for.
 *
 * The rules are the app's own, and DOC's material is no different from the walker's places here: a
 * thing is nearer or further, of this kind or that, and a page shows a row only when it is both. All of
 * it is arithmetic over what was already read - nothing is asked of DOC to answer "near me", because
 * the answer is in the coordinates the search already brought back.
 */

/**
 * How far away something is: the nearest point of it to [point], or null when there is nothing to
 * measure.
 *
 * The nearest point rather than the middle one. A track that ends at the road end is close to the road
 * end, and a track whose middle is ten kilometres away but which starts at the car park is not ten
 * kilometres away. Every path counts, so where a side track goes does not make the track it hangs off
 * look distant.
 */
fun DocItem.distanceM(point: GeoPoint): Double? =
    paths.flatten().minOfOrNull { vertex -> haversineMeters(point.lat, point.lng, vertex.lat, vertex.lng) }

/** The kinds of thing these rows are, in DOC's own words, for a filter. */
fun List<DocItem>.kindsPresent(): List<String> = mapNotNull { it.kind }.distinct().sorted()

/** How they are walked - one way, a loop, a return - which is the tracks' own filter. */
fun List<DocItem>.typesPresent(): List<String> = mapNotNull { it.trackType }.distinct().sorted()

/**
 * Only what is within [radiusM] of [from], and everything when there is no fix to measure from.
 *
 * The service is asked about a **box** around the walker - a rectangle is what a spatial query takes -
 * and a circle is what was meant, so the corners of the box are trimmed here rather than shown as if
 * they were near.
 */
fun List<DocItem>.within(radiusM: Double, from: GeoPoint?): List<DocItem> {
    if (from == null) return this
    return filter { item -> (item.distanceM(from) ?: Double.MAX_VALUE) <= radiusM }
}

/** The order a page of DOC's material is shown in. */
enum class DocSort {

    /** Nearest first, which is what somebody standing somewhere wants. */
    NEAREST,

    /** By name, which is what somebody looking for a particular one wants. */
    NAME
}

/**
 * The rows to show, in the order to show them.
 *
 * A blank filter means "everything". Something that cannot be measured - a row with no coordinate, or a
 * search made with no fix to measure from - sorts **last** rather than first: putting it at the top of a
 * list of nearest would be answering the question with a shrug.
 */
fun List<DocItem>.showing(
    kinds: Set<String> = emptySet(),
    types: Set<String> = emptySet(),
    sort: DocSort = DocSort.NAME,
    from: GeoPoint? = null
): List<DocItem> {
    val filtered = filter { item ->
        (kinds.isEmpty() || item.kind in kinds) && (types.isEmpty() || item.trackType in types)
    }
    return when (sort) {
        DocSort.NEAREST -> filtered.sortedWith(
            compareBy(
                { item -> from?.let { fix -> item.distanceM(fix) ?: Double.MAX_VALUE } ?: 0.0 },
                { item -> item.name.lowercase() }
            )
        )

        DocSort.NAME -> filtered.sortedBy { it.name.lowercase() }
    }
}

/** Everything in these rows, as a box, which is what a download of them would cover. */
fun List<DocItem>.bounds(): LatLngBounds? {
    val points = flatMap { it.paths }.flatten()
    if (points.isEmpty()) return null
    return LatLngBounds(
        minLat = points.minOf { it.lat },
        minLng = points.minOf { it.lng },
        maxLat = points.maxOf { it.lat },
        maxLng = points.maxOf { it.lng }
    )
}
