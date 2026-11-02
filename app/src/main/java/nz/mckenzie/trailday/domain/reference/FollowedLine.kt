package nz.mckenzie.trailday.domain.reference

import nz.mckenzie.trailday.domain.other.OTHER_TRACK_REF_PREFIX

/**
 * What a saved trip's reference says about the line it followed, in the words a walker reads.
 *
 * **A reference and nothing else is what a trip keeps of the line**, so the reference is the whole of
 * what the wording can be decided from. Deciding it from "is there a reference at all?" was wrong: every
 * published line is remembered that way, so a walk of Kew Bush Walkway - a track from the national
 * dataset, nobody's in this app - read "Following a DOC track".
 *
 * The reading is by prefix, because the prefix is what the publishers already use to keep their lines
 * apart. A third publisher is a line here rather than a claim made in a screen. Null is a trip that
 * followed no published line, which has its own words for what it was made for.
 */
fun followedLineWords(ref: String?): String? = when {
    ref == null -> null
    ref.startsWith(OTHER_TRACK_REF_PREFIX) -> "Following another publisher's track"
    else -> "Following a DOC track"
}
