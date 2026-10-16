package nz.mckenzie.trailday.domain.asset

/**
 * What a place is, in the walker's words rather than the database's.
 *
 * Five kinds, and the kind decides the shape: two lines to travel along - a track walked, and a
 * track ridden - and three places to stop at. That is why the shape is a property here rather than
 * a second question on the form - two questions that can disagree is a waypoint drawn as a line
 * across a valley, with nothing on the screen saying the two answers were different.
 *
 * The distinction matters for the map, which draws a track and a hut as different things, and for
 * the record, which has to say what was walked or ridden.
 */
enum class AssetKind(
    /** A line to travel along, or a single place to stop at. */
    val shape: AssetShape
) {
    /** A documented or recorded track: a line walked or ridden. */
    TRACK(AssetShape.LINE),

    /** A track that is ridden. */
    BIKE_TRACK(AssetShape.LINE),

    /** A backcountry hut. */
    HUT(AssetShape.POINT),

    /** A campsite. */
    CAMPSITE(AssetShape.POINT),

    /**
     * A spot of the walker's own: a trailhead, a water source, a lunch spot.
     *
     * The catch-all, and its glyph is the plainest of the five on purpose: what it has to say is
     * "something is here" and nothing beyond that.
     */
    WAYPOINT(AssetShape.POINT);

    companion object {

        /**
         * The kind a stored value means, or null when nothing here does.
         *
         * Null is for the desk, which refuses a write naming a kind this build has never heard of
         * rather than storing it as something else. The app's own reads use [fromStorage], which
         * always has to answer: a record that cannot be read is a record that cannot be looked at.
         */
        fun known(value: String?): AssetKind? = entries.firstOrNull { it.name == value }

        /**
         * The kind a stored value means.
         *
         * Anything unrecognised reads as [TRACK]: a value from a later build, or a database edited
         * by hand, must not stop the app opening its own records.
         */
        fun fromStorage(value: String?): AssetKind = known(value) ?: TRACK
    }
}
