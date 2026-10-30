package nz.mckenzie.trailday.domain.look

import nz.mckenzie.trailday.domain.asset.AssetKind

/**
 * One thing on the map the walker can set the width and colour of.
 *
 * **Seven rows, and they are not the five kinds.** Tracks, bike tracks and huts and campsites are
 * kinds; [OTHER_TRACKS] is not - it is a publisher, the councils and trusts whose lines are drawn
 * beside DOC's, and giving it a row of its own is what lets a walker keep somebody else's track apart
 * from DOC's once they have chosen a colour for tracks. A kind of place is a kind; a publisher is a
 * decision about whose line is whose.
 *
 * [TE_ARAROA] is the third thing a row is not: it is a route, drawn under everything else, and it has
 * a row because its colour and its weight are the walker's to set like anything else they can see. It
 * was fixed at rust for a long time on the argument that the ground the work sits on is not the work.
 * What the row keeps of that is the trail's own widths: Thin, Normal and Thick are the trail's, drawn
 * at 0.8 of a track's, so the default is the look it always had.
 *
 * [control] is which of the two controls the row carries, because a line has a width and a marker has
 * a size, and reading one as the other is how a hut would come to be drawn three pixels wide.
 *
 * [id] is written to preferences. Never rename one.
 */
enum class LookTarget(
    val id: String,
    /** What the card calls it. */
    val displayName: String,
    val control: LookControl
) {

    /**
     * The trail, first in the list because it is first under the map: a national route is the ground
     * the walker's own work is read against.
     */
    TE_ARAROA(
        id = "te-araroa",
        displayName = "Te Araroa",
        control = LookControl.LINE
    ),

    TRACKS(
        id = "tracks",
        displayName = "Tracks",
        control = LookControl.LINE
    ),

    BIKE_TRACKS(
        id = "bike-tracks",
        displayName = "Bike tracks",
        control = LookControl.LINE
    ),

    OTHER_TRACKS(
        id = "other-tracks",
        displayName = "Other tracks",
        control = LookControl.LINE
    ),

    HUTS(
        id = "huts",
        displayName = "Huts",
        control = LookControl.PLACE
    ),

    CAMPSITES(
        id = "campsites",
        displayName = "Campsites",
        control = LookControl.PLACE
    ),

    WAYPOINTS(
        id = "waypoints",
        displayName = "Waypoints",
        control = LookControl.PLACE
    );

    companion object {

        /**
         * The row a [kind] of place is set by.
         *
         * A kind belongs to one row and only one, which is what makes the answer the same wherever it
         * is asked: the map's line layers, the list's glyphs and the desk all reach a kind's colour
         * through this. [OTHER_TRACKS] and [TE_ARAROA] are nobody's kind, which is why they are absent.
         */
        fun of(kind: AssetKind): LookTarget = when (kind) {
            AssetKind.TRACK -> TRACKS
            AssetKind.BIKE_TRACK -> BIKE_TRACKS
            AssetKind.HUT -> HUTS
            AssetKind.CAMPSITE -> CAMPSITES
            AssetKind.WAYPOINT -> WAYPOINTS
        }

        /** The rows a line is set by, in the order the screen lists them. */
        val LINES: List<LookTarget> = entries.filter { it.control == LookControl.LINE }

        /** The rows a marker is set by. */
        val PLACES: List<LookTarget> = entries.filter { it.control == LookControl.PLACE }

        /** The row a stored id names, or null when this build does not know it. */
        fun fromStorage(id: String): LookTarget? = entries.firstOrNull { it.id == id }
    }
}

/** Which control a row carries: a line's width, or a marker's size. */
enum class LookControl { LINE, PLACE }
