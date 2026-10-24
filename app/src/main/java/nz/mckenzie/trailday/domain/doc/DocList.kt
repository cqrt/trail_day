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

/**
 * DOC's rows with the same thing said twice removed.
 *
 * **DOC publishes some of its huts more than once.** Measured against the live service, 500 of its 1,429
 * huts are a second row for a hut already in the list: the same name, the same place, the same region and
 * the same coordinate to five decimal places, under a different number a few hundred away. A walker
 * browsing the huts list sees the same hut twice, the map draws two markers on one spot, and a download
 * spends its time on the copies.
 *
 * **The rule is a place, not a name.** DOC really does publish a Crow Hut in the Ruahine Forest Park,
 * another in Arthur's Pass and a third in Kahurangi, and a Hurunui Hut in two different regions; merging on
 * the name alone would take those away. What is duplicated is the same *row*, so it takes the same name,
 * the same place, the same region **and the same point** - and the pairs DOC publishes today are all at zero
 * metres, so the allowance below cannot reach a hut that is genuinely another.
 *
 * **The lowest DOC number of a repeated group is the one kept**, so a page, a download and a cache row agree
 * about which row a thing is however the answer happened to be ordered.
 */
fun List<DocItem>.withoutRepeats(): List<DocItem> {
    // Grouped by name first: only rows that share one can be the same thing, and a national list is a few
    // thousand rows, which is not worth comparing every pair of.
    val byName = mutableMapOf<String, MutableList<Int>>()
    forEachIndexed { index, item ->
        byName.getOrPut(item.name.trim().lowercase()) { mutableListOf() } += index
    }

    val dropped = mutableSetOf<Int>()
    byName.values.forEach { indices ->
        indices.forEach { index ->
            val item = this[index]
            val repeated = indices.any { other ->
                other != index && this[other].objectId < item.objectId && this[other].isTheSameThingAs(item)
            }
            if (repeated) dropped += index
        }
    }

    return filterIndexed { index, _ -> index !in dropped }
}

/**
 * Whether two of DOC's rows are the same thing said twice: the same service, the same name, the same place
 * and region, and the same point on the ground.
 */
private fun DocItem.isTheSameThingAs(other: DocItem): Boolean {
    if (source != other.source) return false
    if (!name.trim().equals(other.name.trim(), ignoreCase = true)) return false
    if (place?.trim() != other.place?.trim()) return false
    if (region?.trim() != other.region?.trim()) return false

    val here = paths.firstOrNull()?.firstOrNull() ?: return false
    val there = other.paths.firstOrNull()?.firstOrNull() ?: return false
    return haversineMeters(here.lat, here.lng, there.lat, there.lng) <= SAME_THING_M
}

/**
 * How far apart two rows may be and still be one thing.
 *
 * Twenty metres, against pairs that DOC publishes at zero: room for a re-survey to move one copy a little
 * without bringing the duplicate back, and far short of the nearest two genuinely different huts.
 */
private const val SAME_THING_M = 20.0

/** How they are walked - one way, a loop, a return - which is the tracks' own filter. */
fun List<DocItem>.typesPresent(): List<String> = mapNotNull { it.trackType }.distinct().sorted()

/**
 * The values these rows carry for a filter's field, each once, sorted, blanks left out.
 *
 * Read from the page the search actually brought back, so every chip a filter offers is a fact about
 * something on the screen: a chip that matches nothing is a filter nobody can use.
 */
fun List<DocItem>.valuesPresent(field: String): List<String> =
    mapNotNull { item -> item.filterValue(field) }.distinct().sorted()

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
    filters: Map<String, Set<String>> = emptyMap(),
    sort: DocSort = DocSort.NAME,
    from: GeoPoint? = null
): List<DocItem> {
    val filtered = filter { item ->
        // Every filter must agree with the row, and a filter with nothing ticked agrees with everything.
        filters.all { (field, chosen) -> chosen.isEmpty() || item.filterValue(field) in chosen }
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
