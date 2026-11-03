package nz.mckenzie.trailday.domain.reference

/**
 * What the recorder's chooser offers from one publisher's lines.
 *
 * **A rule about the question rather than about the publisher.** The sheet lists the walker's own places,
 * DOC's lines and the tracks councils and trusts publish under three headings, and a walker reading any of
 * them is asking the same thing: what can I follow from here. The two published lists narrow it the same
 * way, so the search field means one thing rather than two - a name typed into it finds DOC's track and
 * somebody else's alike, or the field would be answered by only half the list under it.
 *
 * Nearby when nothing is typed, and by name when something is - **by name wherever the line is**, so a
 * walker who knows what a track is called finds it without a fix and without knowing which town it is
 * near. A row whose distance cannot be measured - no fix, or a line that will not measure - sorts last
 * rather than first: putting it at the top of a list of nearest is answering the question with a shrug.
 */
fun <T> List<T>.chooserChoices(
    query: String,
    distanceOf: (T) -> Double?,
    nameOf: (T) -> String
): List<ChooserChoice<T>> {
    val words = query.trim()
    return mapNotNull { row ->
        val distance = distanceOf(row)
        val named = words.isEmpty() || nameOf(row).contains(words, ignoreCase = true)
        val near = distance != null && distance <= CHOOSER_NEARBY_M
        if (named && (words.isNotEmpty() || near)) ChooserChoice(row, distance) else null
    }
        .sortedBy { it.distanceM ?: Double.MAX_VALUE }
        .take(CHOOSER_ROWS)
}

/**
 * How far around the phone the chooser looks when nothing is typed, in metres.
 *
 * Ten kilometres, and it has been ten since DOC's list was the only one: a walk a walker can start from
 * where they are standing, rather than every line in the district. Nothing is lost by a short reach,
 * because the search reaches the whole of what the phone holds.
 */
const val CHOOSER_NEARBY_M = 10_000.0

/**
 * How many of one publisher's lines the chooser offers at once.
 *
 * Five, so the sheet stays a choice rather than a list: three groups of five is already more rows than the
 * window shows before it scrolls, and the search is there for anything further away.
 */
const val CHOOSER_ROWS = 5

/** One line the chooser offers, and how far away it is - null when there is no fix to measure from. */
data class ChooserChoice<T>(val item: T, val distanceM: Double?)
