package nz.mckenzie.trailday.domain.reference

import nz.mckenzie.trailday.domain.look.LookTarget
import nz.mckenzie.trailday.domain.other.OTHER_TRACK_REF_PREFIX
import nz.mckenzie.trailday.domain.other.OtherTracksCache

/**
 * What a saved trip's reference says about the line it followed, in the words a walker reads.
 *
 * **A reference and nothing else is what a trip keeps of the line**, so the reference is the beginning of
 * what the wording can be decided from. Deciding it from "is there a reference at all?" was wrong: every
 * published line is remembered that way, so a walk of Kew Bush Walkway - a track from the national
 * dataset, nobody's in this app - read "Following a DOC track".
 *
 * The reading is by prefix, because the prefix is what the publishers already use to keep their lines
 * apart. DOC is named by its own word; a track from the dataset is **named by whoever publishes it** when
 * [publisher] is known, and falls back to the plain sentence when it is not - which is honest, because the
 * cache holding that name can be cleared without costing anything but the download. Null is a trip that
 * followed no published line, which has its own words for what it was made for.
 */
fun followedLineWords(ref: String?, publisher: String? = null): String? = when {
    ref == null -> null
    !ref.startsWith(OTHER_TRACK_REF_PREFIX) -> "Following a DOC track"
    publisher != null -> "Following a track from $publisher"
    else -> "Following another publisher's track"
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
 * Who publishes the track a trip followed, or null when the phone no longer holds that row.
 *
 * The dataset's row is the only place the publisher is written down: a trip keeps its line's reference,
 * never a copy of somebody's facts. Read from the cache the walker downloaded rather than fetched, so this
 * answers with no reception, and answers null for a row a monthly rebuild has taken away - the words then
 * say "another publisher's track" rather than a name nobody checked.
 */
suspend fun followedPublisher(ref: String?, cache: OtherTracksCache): String? {
    val id = otherTrackGlobalId(ref) ?: return null
    return runCatching { cache.find(id) }.getOrNull()?.suppliers?.firstOrNull()
}
