package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind

/**
 * How each kind of line is drawn on the map.
 *
 * A track is a line you travel along and a bike track is a track that is ridden, so a track is drawn
 * solid and a bike track dashed: the two lines must be told apart at a glance. The three kinds that
 * are places are not here: they are drawn as a marker, and a dash pattern has nothing to say about a
 * marker.
 *
 * A dash array is in multiples of the line width rather than in pixels - that is the map engine's unit,
 * not a choice - so the numbers below are worked out from [LineWeight] to keep the dash the length it is
 * on the ground. A line that thinned while its dash shrank with it would be a stipple rather than a dash,
 * and the dash is the whole of what a bike track says with its shape.
 *
 * Kept out of the map view so that which kind is dashed - a decision the walker notices - is
 * covered by a plain unit test instead of by looking at a screen.
 */
object AssetLineStyles {

    /** Dashed: a bike track reads as its own kind of line. Ten pixels of line, eight of gap. */
    val BIKE_TRACK: Array<Float> = LineWeight.dashArray()

    /**
     * Null means solid - which is also what every place gets, because a place is one point and
     * a dash pattern is a thing a line has. Nothing draws a place with a line layer, so this is
     * a statement about the kind rather than about a layer that exists.
     */
    fun forKind(kind: AssetKind): Array<Float>? = when (kind) {
        AssetKind.TRACK -> null
        AssetKind.BIKE_TRACK -> BIKE_TRACK
        AssetKind.HUT,
        AssetKind.CAMPSITE,
        AssetKind.WAYPOINT -> null
    }
}
