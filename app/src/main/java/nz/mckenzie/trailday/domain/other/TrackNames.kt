package nz.mckenzie.trailday.domain.other

/**
 * What the dataset actually called a track, and whether what it put in the name field is a name at all.
 *
 * **The dataset fills the name field with placeholders on a fifth of its rows.** Of the 15,973 rows this app
 * reads, 2,610 say "Unnamed Track", 652 say exactly "Track" and 22 say "Track 7" and the like. Those are
 * values where a name goes, not names, so [OtherTrack.name] replaces them - with the row's locality where it
 * has one, which is the dataset's own word for the area the track is in.
 *
 * That replacement is why this file exists rather than those two lines living in the reader. **A made-up name
 * cannot be used to decide that two rows are one walkway**: twelve unnamed tracks in Mount Victoria are all
 * renamed "Mount Victoria", and joining by that would draw one enormous fake track across a suburb. What may
 * join two rows is the name the *publisher* gave - [publishedName] - which a placeholder is not.
 */

/**
 * Whether the dataset put something in the name field that is not a name.
 *
 * Anchored and case-blind, so a track genuinely called "Trackway" is left alone - the values it catches
 * were read off the live service rather than guessed at: "Unnamed Track" (2,699 rows), "Unnamed" (97),
 * "Track" (676), "Track unnamed" (4) and "Track 16" and the like (22).
 */
fun isPlaceholderName(name: String): Boolean = PLACEHOLDER.matches(name.trim())

private val PLACEHOLDER = Regex("(?i)unnamed( track)?|track( unnamed| \\d+)?")

/**
 * The name the publisher gave this row, or null when it gave none worth having.
 *
 * The raw field rather than [OtherTrack.name], because the app may have replaced a placeholder with the
 * locality: this is the question "did somebody actually name this track", which is the only question a join
 * between two rows may ask.
 */
fun OtherTrack.publishedName(): String? = facts[OtherTracksUrl.NAME_FIELD]
    ?.trim()
    ?.takeIf { it.isNotEmpty() && !isPlaceholderName(it) }
