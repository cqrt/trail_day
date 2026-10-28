package nz.mckenzie.trailday.domain.other

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.haversineMeters
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

/**
 * How near two pieces' ends have to be to be one walkway, in metres, **when nothing else says they are.**
 *
 * **Measured rather than guessed, and it started at ten metres and was wrong.** Two walkways settled the
 * number between them:
 *
 *  - "Otatara Scenic Reserve Walkway" is four rows around Invercargill whose ends coincide *exactly* - 0.0 m
 *    - and a fifth "Otatara" in Napier, 900 km away, which must not join them. A name alone would merge
 *    those.
 *  - "Kingswell Creek Walkway" is **nine** rows along one creek in Invercargill, and only some of them
 *    touch: the gaps along it are **12, 21, 22 and 126 metres**, because a walkway is interrupted by roads,
 *    bridges and private land. At ten metres it stayed four rows, which is what a walker reported.
 *
 * Two hundred metres is the allowance for pieces whose publishers have nothing in common - a walkway that
 * crosses a boundary, published by the two councils either side of it. It is only safe because **a name
 * has to match as well**: two differently named walkways are never joined, however close they are.
 */
private const val JOIN_TOLERANCE_M = 200.0

/**
 * How near two pieces **in one district** have to be, in metres, when their names are the same.
 *
 * Measured, and it is the allowance sections actually need. Grouping the app's rows by **name and
 * district**, the smallest gap between pieces falls into four bands: **43** groups at 200 metres to a
 * kilometre, **16** at one to three kilometres, **12** at three to ten, and **6** beyond ten.
 *
 *  - "Lake Hayes Walkway" is **five rows**: three the council's, two the Trust's, their pieces 496 metres
 *    to 5,030 metres apart, and a walker reported it as still in sections.
 *  - "Bald Hill Longwood Forest" is **two**: Te Araroa's own, 2,750 metres apart.
 *  - The examples between three and ten kilometres are almost all the same story - "Ohau Lakeside Cycleway"
 *    5,843 m, "Ara Harakeke Walkway" 4,842 m, "Paihia - Opua Coastal Walkway" 3,377 m - because **a long
 *    trail is published in long sections**, and the section boundaries are kilometres apart.
 *
 * Ten kilometres is where the measurements stop agreeing: the six groups beyond it are pairs that read as
 * different places ("Short walk" 13,513 m apart in Waimakariri, "Motatapu Route" 20,263 m, "Round Hill
 * Route" 21,913 m), and joining those would put two walks under one row with no way to tell them apart.
 *
 * **The publisher is no longer part of this test**, which is the change that "Lake Hayes" needed: the
 * council's three pieces and the Trust's two are the same walkway, and requiring one publisher kept them in
 * two rows. A name that appears twice in one district is one walk.
 */
private const val SAME_DISTRICT_TOLERANCE_M = 10_000.0

/**
 * The walkways the dataset publishes in pieces, as one track each.
 *
 * **The dataset cuts a walkway up and the walker does not.** "Otatara Scenic Reserve Walkway" is four rows;
 * "Southern Walkway" is four around Mount Victoria; "Crazy Horse" is two that share a vertex. A list that
 * shows each piece is a list that says a walkway is four walkways, and each row's length is a quarter of
 * the walk.
 *
 * **A name is not enough, and the same example proves it**: there is an Otatara walkway in Invercargill and
 * another in Napier. Two rows join only when their names are the same *and* they are near each other - an
 * end of one within [JOIN_TOLERANCE_M] of an end of the other, or within [SAME_DISTRICT_TOLERANCE_M] when
 * they are in the same district. End to end, because a walkway cut into pieces is cut at points. Pieces are
 * then gathered into whole walkways, so four rows that meet in a loop become one track rather than two pairs,
 * and a reserve whose whole network carries one name becomes one row rather than thirty-five.
 *
 * **The name that counts is the publisher's, not the one the app shows.** A fifth of the dataset's rows have
 * a placeholder in the name field and are renamed after their locality, so twelve unnamed tracks in Mount
 * Victoria all read "Mount Victoria": joining on that would draw one enormous fake track across a suburb.
 * [publishedName] answers "did somebody actually name this", and a row nobody named never joins anything.
 *
 * The joined track keeps the **lowest service id** of its pieces, so which row stands for a walkway does
 * not depend on the order the service happened to answer in. Its paths stay apart - they meet, so they draw
 * as one line - and its publishers are all of them, because narrowing by a council means a track that
 * council had a hand in.
 */
fun List<OtherTrack>.joinedByName(toleranceM: Double = JOIN_TOLERANCE_M): List<OtherTrack> {
    if (size < 2) return this

    // Grouped by published name first: only rows somebody named the same can be the same walkway, and a
    // list is thousands of rows, which is not worth comparing every pair of.
    val groups = LinkedHashMap<String, MutableList<Int>>()
    forEachIndexed { index, track ->
        val named = track.publishedName()?.lowercase() ?: return@forEachIndexed
        groups.getOrPut(named) { mutableListOf() } += index
    }

    val replaced = arrayOfNulls<OtherTrack>(size)
    val dropped = BooleanArray(size)
    for (indices in groups.values) {
        if (indices.size < 2) continue
        for (component in componentsOf(indices, toleranceM)) {
            if (component.size < 2) continue
            replaced[component.first()] = joinedOf(component)
            component.drop(1).forEach { dropped[it] = true }
        }
    }
    if (replaced.none { it != null }) return this

    return mapIndexedNotNull { index, track ->
        when {
            dropped[index] -> null
            else -> replaced[index] ?: track
        }
    }
}

/** The walkways among [indices]: those whose ends meet, gathered into one component each. */
private fun List<OtherTrack>.componentsOf(indices: List<Int>, toleranceM: Double): List<List<Int>> {
    val remaining = indices.toMutableList()
    val components = mutableListOf<List<Int>>()
    while (remaining.isNotEmpty()) {
        val component = mutableListOf(remaining.removeAt(0))
        var grew = true
        while (grew) {
            grew = false
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (component.any { this[it].meets(this[candidate], toleranceM) }) {
                    component += candidate
                    iterator.remove()
                    grew = true
                }
            }
        }
        components += component.sorted()
    }
    return components
}

/**
 * Whether these two rows are pieces of one walkway.
 *
 * Two allowances, and the wide one is only open to rows that agree about **where** they are: see
 * [JOIN_TOLERANCE_M] and [SAME_DISTRICT_TOLERANCE_M].
 */
private fun OtherTrack.meets(other: OtherTrack, toleranceM: Double): Boolean {
    val gap = endsGap(other) ?: return false
    if (gap <= toleranceM) return true
    return gap <= SAME_DISTRICT_TOLERANCE_M && sameDistrict(other)
}

/** The shortest distance between an end of this track and an end of [other], or null without two lines. */
private fun OtherTrack.endsGap(other: OtherTrack): Double? {
    val mine = ends()
    val theirs = other.ends()
    if (mine.isEmpty() || theirs.isEmpty()) return null
    return mine.minOf { a -> theirs.minOf { b -> haversineMeters(a.lat, a.lng, b.lat, b.lng) } }
}

/**
 * Whether both rows are in the same district - the dataset's own word for where a track is.
 *
 * A row with no district matches nothing: without one of the dataset's own words for the place, only the
 * near allowance is on offer.
 */
private fun OtherTrack.sameDistrict(other: OtherTrack): Boolean {
    val district = localAuthority?.trim().orEmpty()
    return district.isNotEmpty() && district.equals(other.localAuthority?.trim(), ignoreCase = true)
}

/** The vertices at the two ends of a track's paths. A path of one point is not a line. */
private fun OtherTrack.ends(): List<GeoPoint> = paths
    .filter { it.size >= 2 }
    .flatMap { listOf(it.first(), it.last()) }

/** [component]'s pieces as one track: see [joinedByName]. */
private fun List<OtherTrack>.joinedOf(component: List<Int>): OtherTrack {
    val pieces = component.map { this[it] }.sortedBy { it.globalId }
    val main = pieces.first()
    // The publishers of every piece, each once, so the filter still finds the walkway by any council that
    // has a hand in it. The field is comma-separated where a row was merged upstream, so this round-trips.
    val publishers = pieces.flatMap { it.suppliers }.distinct()
    return main.copy(
        paths = pieces.flatMap { it.paths },
        facts = if (publishers.isEmpty()) {
            main.facts
        } else {
            main.facts + ("DataSupplier" to publishers.joinToString(", "))
        }
    )
}
