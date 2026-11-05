package nz.mckenzie.trailday.domain.reference

import nz.mckenzie.trailday.domain.look.LookTarget
import nz.mckenzie.trailday.domain.other.OTHER_TRACK_REF_PREFIX
import nz.mckenzie.trailday.domain.other.OtherTracksCache

/**
 * One published line a trip followed, and who published it when that is known.
 *
 * A walk is measured against one line at a time, but it is remembered against however many it walked. The
 * publisher is nullable for the reason [followedPublishers] says: it lives in the downloaded row, and a
 * cache cleared on a monthly rebuild can take it away without taking away the walk.
 */
data class FollowedRef(val ref: String, val publisher: String? = null) {

    /** Whether this is one of DOC's lines, which is what the reference's prefix says. */
    val isDoc: Boolean get() = !ref.startsWith(OTHER_TRACK_REF_PREFIX)
}

/**
 * What a saved trip's references say about the line, or the lines, it followed.
 *
 * **A reference and nothing else is what a trip keeps**, so the reference is the beginning of what the
 * wording can be decided from. A walk that followed one track reads as it always has. A walk that followed
 * two - one line at the end of a section, another when the ground forked - names **both publishers**:
 * reading only the last one would be a line under the name of a walk that is quietly wrong about its first
 * half.
 *
 * The reading is by prefix, because the prefix is what the publishers already use to keep their lines
 * apart. DOC is named by its own word; a track from the dataset is **named by whoever publishes it** when
 * the publisher is known, and falls back to "another publisher" when it is not - which is honest, because
 * the cache holding that name can be cleared without costing anything but the download.
 */
fun followedLineWords(followed: List<FollowedRef>): String? {
    if (followed.isEmpty()) return null

    if (followed.all { it.isDoc }) {
        return if (followed.size == 1) "Following a DOC track" else "Following DOC tracks"
    }

    // Named once each, in the order they were walked: a walk that went DOC, council, DOC read its two
    // publishers and not its three lines.
    val publishers = LinkedHashSet<String>()
    for (item in followed) {
        publishers += if (item.isDoc) DOC_WORD else item.publisher ?: UNKNOWN_PUBLISHER
    }

    if (publishers.size == 1 && UNKNOWN_PUBLISHER in publishers) {
        return if (followed.size == 1) {
            "Following another publisher's track"
        } else {
            "Following other publishers' tracks"
        }
    }

    return if (followed.size == 1) {
        "Following a track from ${publishers.first()}"
    } else {
        "Following tracks from ${namedInWords(publishers.toList())}"
    }
}

/**
 * Which row of the look screen paints a line being followed, read from its reference for the same reason
 * [followedLineWords] reads who published it that way: the prefix is the whole of the difference.
 *
 * DOC's tracks are drawn among the walker's own - that is what every map does with DOC's material - and a
 * track from the national dataset wears the Other tracks colour, which is the row the walker sets to keep
 * somebody else's line apart from DOC's.
 */
fun followedLookTarget(ref: String): LookTarget =
    if (ref.startsWith(OTHER_TRACK_REF_PREFIX)) LookTarget.OTHER_TRACKS else LookTarget.TRACKS

/**
 * The service's own key for the dataset's row a reference points at, or null when it points at somebody
 * else's material. The prefix is the whole of the reading, as everywhere else.
 */
fun otherTrackGlobalId(ref: String?): String? =
    ref?.takeIf { it.startsWith(OTHER_TRACK_REF_PREFIX) }?.removePrefix(OTHER_TRACK_REF_PREFIX)

/**
 * Who published each of the lines a trip followed, in the same order, or null where the phone no longer
 * holds that row.
 *
 * The dataset's row is the only place the publisher is written down: a trip keeps its line's reference,
 * never a copy of somebody's facts. Read from the cache the walker downloaded rather than fetched, so this
 * answers with no reception, and answers null for a row a monthly rebuild has taken away - the words then
 * say "another publisher" rather than a name nobody checked.
 *
 * **The cache is read once for the whole list.** One call per reference is one parse of a thousand rows
 * per reference, which a walk that changed track three times would pay three times over for the same
 * answer.
 */
suspend fun followedPublishers(refs: List<String>, cache: OtherTracksCache): List<String?> {
    if (refs.none { otherTrackGlobalId(it) != null }) return refs.map { null }
    val held = runCatching { cache.cached() }.getOrNull().orEmpty().associateBy { it.globalId }
    return refs.map { ref ->
        otherTrackGlobalId(ref)?.let { id -> held[id]?.suppliers?.firstOrNull() }
    }
}

/** The word a publisher of DOC's material is named by, when it is named beside others. */
private const val DOC_WORD = "DOC"

/** What is said of a line whose publisher the phone no longer holds. */
private const val UNKNOWN_PUBLISHER = "another publisher"

/**
 * "A", "A and B", "A, B and C": the app's own English, without the comma before "and" that a list of three
 * gets in American writing and does not here.
 */
private fun namedInWords(names: List<String>): String = when (names.size) {
    0 -> UNKNOWN_PUBLISHER
    1 -> names.first()
    2 -> "${names[0]} and ${names[1]}"
    else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
}
