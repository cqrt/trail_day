package nz.mckenzie.trailday.domain.recording

/**
 * The published lines a walk followed, in the order it followed them.
 *
 * **One walk, several lines.** A track published as nine sections, and a walker who walks onto a second
 * track halfway, both end with a recording measured against more than one line - and the trip keeps each
 * reference, never a copy of anybody's facts. `docRefs` holds them newline separated; `docRef` holds the
 * last of them, which is what every reader before this column existed was given.
 *
 * A row written before the list existed has only `docRef`, so it reads as a walk of one line. That is not
 * a fallback so much as the truth about it: nothing in the app could follow a second line in the middle of
 * a walk until now.
 */
fun followedRefs(docRef: String?, docRefs: String?): List<String> {
    val held = docRefs
        ?.split(DOC_REFS_SEPARATOR)
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
    if (held.isNotEmpty()) return held
    return listOfNotNull(docRef?.trim()?.takeIf { it.isNotEmpty() })
}

/**
 * What the references are stored with.
 *
 * A newline rather than a comma, because a reference contains neither and a comma is a character somebody
 * will one day put in an id. The same separator the app's own JSON writes lists with, so a value read out
 * of the database and a value read out of a file are read the same way.
 */
const val DOC_REFS_SEPARATOR = "\n"
