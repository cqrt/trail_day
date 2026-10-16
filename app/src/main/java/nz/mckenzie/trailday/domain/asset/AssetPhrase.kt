package nz.mckenzie.trailday.domain.asset

/**
 * What a place is, in the walker's words.
 *
 * One place for these, so the form, the list and the card all say the same thing about
 * the same place - and so a new kind cannot ship as a picker with a blank label.
 */
object AssetPhrase {

    /**
     * "Track", "Bike track", "Hut", "Campsite", "Waypoint".
     */
    fun kind(kind: AssetKind): String = when (kind) {
        AssetKind.TRACK -> "Track"
        AssetKind.BIKE_TRACK -> "Bike track"
        AssetKind.HUT -> "Hut"
        AssetKind.CAMPSITE -> "Campsite"
        AssetKind.WAYPOINT -> "Waypoint"
    }

    /**
     * The kinds in the order the picker offers them: the tracks to travel along, then the places
     * to stop at.
     */
    val kinds: List<AssetKind> = AssetKind.entries.toList()

    /**
     * What the metres of a place are called.
     *
     * A line has a length and a place has none - and every kind that has metres is a line - so the
     * word is the same wherever there is one. It is still a phrase of its own rather than a word
     * buried in a screen, because the card, the drawing screen and the desk's card are all making
     * the same statement about the same number.
     */
    fun lengthLabel(): String = "Length"

    /**
     * What changing a place's drawing is called.
     *
     * The card's own action and the drawing screen's title are the same errand and so the same
     * words.
     */
    fun changeLabel(): String = "Change the line"
}
