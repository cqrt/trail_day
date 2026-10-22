package nz.mckenzie.trailday.map

/**
 * What the two DOC track switches mean when read together.
 *
 * **Tracks is the base layer: every DOC track, including the ones that can be ridden.** **Bike
 * trails adds the bicycle badges**, and doubles as the way to see the rideable tracks on their own
 * when Tracks is switched off. That is what keeps a walker with Bike trails off from losing a
 * dual-use track, while a rider with Tracks off still has a map with lines on it.
 *
 * A dual-use track is drawn once either way: when Tracks is on, the base layer owns it and the bike
 * line layer is quiet; when Tracks is off, the bike line layer takes over.
 */
object DocTrackVisibility {

    /** The base DOC line and its casing: every track, so dual-use tracks belong here too. */
    fun baseTracksShown(tracksShown: Boolean): Boolean = tracksShown

    /** The bike-only line and casing: used only when the base layer is not there to draw it. */
    fun bikeTracksShown(tracksShown: Boolean, bikeTrailsShown: Boolean): Boolean =
        bikeTrailsShown && !tracksShown

    /** The bicycle badges belong to Bike trails, whatever the base line is doing. */
    fun badgesShown(bikeTrailsShown: Boolean): Boolean = bikeTrailsShown

    /**
     * Whether a DOC track of [bikeFriendly] kind is on the map.
     *
     * A walking-only track needs Tracks. A dual-use track is on when either switch is on: Tracks
     * draws it as a walkable line, and Bike trails draws it when Tracks is off.
     */
    fun lineShown(
        tracksShown: Boolean,
        bikeTrailsShown: Boolean,
        bikeFriendly: Boolean
    ): Boolean = tracksShown || (bikeTrailsShown && bikeFriendly)
}
