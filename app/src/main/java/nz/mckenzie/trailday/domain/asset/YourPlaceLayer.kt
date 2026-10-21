package nz.mckenzie.trailday.domain.asset

/**
 * One part of the walker's own work on the map.
 *
 * The layers sheet has a master **Your Places** switch over these four: the spots they saved, the
 * lines they planned or imported, the lines they planned to ride, and the passes they recorded. They
 * are grouped together because they are all the walker's, but each can be switched off on its own:
 * hiding yesterday's recorded lines should not take the water spot off the map.
 *
 * **Spots is every point of the walker's own**, including huts and campsites made by an older build.
 * The app has no separate hut or campsite of its own to add now, and leaving an old one outside every
 * switch would make it impossible to hide.
 *
 * A recorded line is not a place, but it is the walker's own work and belongs under the same master.
 * The raw recording is [RECORDINGS]; a planned line with a pass recorded against it is still its own
 * [TRACKS] or [BIKE_TRACKS], coloured as recorded.
 */
enum class YourPlaceLayer(
    /** Stable id: it is what preferences store. Never renamed. */
    val id: String,
    /** What the tile calls it. */
    val displayName: String
) {
    SPOTS(id = "spots", displayName = "Spots"),
    TRACKS(id = "tracks", displayName = "Tracks"),
    BIKE_TRACKS(id = "bike-tracks", displayName = "Bike tracks"),
    RECORDINGS(id = "recordings", displayName = "Recordings");

    companion object {

        val ALL: List<YourPlaceLayer> = entries

        /** The walker's layer [kind] belongs to. */
        fun forKind(kind: AssetKind): YourPlaceLayer = when (kind) {
            AssetKind.TRACK -> TRACKS
            AssetKind.BIKE_TRACK -> BIKE_TRACKS
            AssetKind.HUT, AssetKind.CAMPSITE, AssetKind.WAYPOINT -> SPOTS
        }

        /**
         * The layers a stored set of ids hides.
         *
         * Unknown values are dropped rather than kept: a preference written by a build with a
         * layer this one does not have must not hide anything or stop the map drawing.
         */
        fun hiddenIn(stored: Collection<String>?): Set<YourPlaceLayer> = stored
            ?.flatMapTo(mutableSetOf()) { id -> entries.filter { it.id == id } }
            .orEmpty()
    }
}
