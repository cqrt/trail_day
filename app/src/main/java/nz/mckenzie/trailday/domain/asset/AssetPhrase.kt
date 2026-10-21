package nz.mckenzie.trailday.domain.asset

/**
 * What a place is, in the walker's words.
 *
 * One place for these, so the form, the list and the card all say the same thing about
 * the same place - and so a new kind cannot ship as a picker with a blank label.
 */
object AssetPhrase {

    /**
     * "Track", "Bike track", "Hut", "Campsite", "Spot".
     *
     * The stored kind is still `WAYPOINT`, because a database value is not a word on a screen. The
     * walker's word is **spot**: a marker for something they found, not a point on a route.
     */
    fun kind(kind: AssetKind): String = when (kind) {
        AssetKind.TRACK -> "Track"
        AssetKind.BIKE_TRACK -> "Bike track"
        AssetKind.HUT -> "Hut"
        AssetKind.CAMPSITE -> "Campsite"
        AssetKind.WAYPOINT -> "Spot"
    }

    /**
     * The kinds in the order the picker offers them: the tracks to travel along, then the places
     * to stop at.
     */
    val kinds: List<AssetKind> = AssetKind.entries.toList()

    /**
     * The kinds a place of this shape may be, in that same order.
     *
     * Which shape a place is was decided by how it came to exist - a spot dropped on the map, or a
     * line from DOC, from a file or from a walk - and it is never asked about again. A form that
     * could answer otherwise is the waypoint drawn as a line across a valley that the five kinds
     * exist to prevent, so the picker offers only the kinds that agree with the place already there.
     */
    fun kindsFor(shape: AssetShape): List<AssetKind> = kinds.filter { it.shape == shape }

    /**
     * What the metres of a place are called.
     *
     * A line has a length and a place has none - and every kind that has metres is a line - so the
     * word is the same wherever there is one. It is still a phrase of its own rather than a word
     * buried in a screen, because the card, the drawing screen and the desk's card are all making
     * the same statement about the same number.
     */
    fun lengthLabel(): String = "Length"
}
