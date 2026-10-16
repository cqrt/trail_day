package nz.mckenzie.trailday.domain.asset

/**
 * What a place's geometry is: a line to travel along, or a single point.
 *
 * A track and a bike track are lines; a hut, a campsite and a waypoint are places, and a place's
 * geometry is that one coordinate. Nothing offers this as a choice: the kind of the place decides
 * it (see [AssetKind.shape]), because an answer that can disagree with the kind is a waypoint
 * drawn as a line across a valley.
 */
enum class AssetShape {
    /** Two or more points: something you travel along. */
    LINE,

    /** Exactly one point: somewhere you stop. */
    POINT;

    companion object {
        /** The shape a stored value means; anything unrecognised is a line. */
        fun fromStorage(value: String?): AssetShape =
            entries.firstOrNull { it.name == value } ?: LINE
    }
}
