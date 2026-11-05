package nz.mckenzie.trailday.domain.journey

/**
 * What a walk that followed more than one published line is called.
 *
 * A walk recorded against one track already takes that track's name and the day. A walk that wandered onto
 * a second track has two, and naming it after whichever one it happened to finish on would be a title that
 * describes the last half hour and forgets the rest.
 *
 * **Three names, then a count.** A title is a line on a list, read at a glance: three names and the day fit
 * one line on a phone, and a walk that changed over five times reads "…+2 more" rather than wrapping the
 * row to three lines and pushing its own figures off the screen. The full story is on the track's page and
 * on the map, not in its name.
 *
 * **A name written twice is written once**, because a walk that comes back to a track it has already
 * followed has not started following a new one - and "A / B / A" is a title that says the app lost track.
 */
fun joinedWalkName(names: List<String>, date: String): String {
    val walked = names
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

    if (walked.isEmpty()) return "Track $date"

    val shown = if (walked.size <= NAMES_SHOWN) {
        walked.joinToString(SEPARATOR)
    } else {
        walked.take(NAMES_SHOWN).joinToString(SEPARATOR) + " +${walked.size - NAMES_SHOWN} more"
    }
    return "$shown \u2013 $date"
}

/** How many of a walk's tracks its name carries before it starts counting them. */
const val NAMES_SHOWN = 3

/** What the names are joined with: a slash, read as "and then". */
private const val SEPARATOR = " / "
