package nz.mckenzie.trailday.domain.asset

/**
 * One layer of the work on the map, as the walker's switches see it.
 *
 * **One layer per kind of place** - five of them, the same five the list's chips offer - because
 * the reason to hide a layer is to read another: a network of tracks under a set of waypoints is
 * noise, and a scatter of waypoints buries the one hut that is the thing being looked for. Hiding
 * "places" was not enough for the second of those, so a place kind can be hidden on its own.
 *
 * [kind] is the kind the layer draws, which is what the map's own style filters on, and what the
 * marker's shape and colour are read from. It is the same enum the picker, the list and the record
 * screens use: a layer per kind, a chip per kind, one vocabulary.
 *
 * **What a preference stores is what is hidden, not what is shown.** Everything is drawn until
 * somebody says otherwise, so the map is what it always was for anyone who never opens this, and a
 * layer added in a later build arrives visible rather than missing: a walker who has never touched
 * a switch must not lose a layer because a new one turned up.
 */
enum class AssetLayer(
    /** Stable id: it is what preferences store. Never renamed. */
    val id: String,
    /** What the switch calls it. */
    val displayName: String,
    /** What the layer draws. */
    val kind: AssetKind
) {
    TRACKS(
        id = "tracks",
        displayName = "Tracks",
        kind = AssetKind.TRACK
    ),

    BIKE_TRACKS(
        id = "bike-tracks",
        displayName = "Bike tracks",
        kind = AssetKind.BIKE_TRACK
    ),

    HUTS(
        id = "huts",
        displayName = "Huts",
        kind = AssetKind.HUT
    ),

    CAMPSITES(
        id = "campsites",
        displayName = "Campsites",
        kind = AssetKind.CAMPSITE
    ),

    WAYPOINTS(
        id = "waypoints",
        displayName = "Waypoints",
        kind = AssetKind.WAYPOINT
    );

    companion object {

        /** Everything that can be shown or hidden, in the order the switches list them. */
        val ALL: List<AssetLayer> = entries

        /**
         * The id an older build stored for the one switch that hid every place.
         *
         * Before this release there were four switches and the fifth kind of thing was "places".
         * A walker who hid them meant the huts, the campsites and their own spots alike, so the id
         * is read back as the three place kinds rather than dropped - a hidden layer coming back is
         * a map that has quietly changed under somebody.
         */
        private const val LEGACY_PLACES = "places"

        /**
         * The layers a stored set of ids hides.
         *
         * Anything unrecognised is dropped rather than kept: a preference written by a build
         * that had a layer this one does not must not hide anything and must not stop the map
         * drawing - the same reasoning as
         * [nz.mckenzie.trailday.domain.tiles.Basemap.fromStorage]. Nothing stored hides
         * nothing, which is what makes a fresh install and an untouched switch the same thing.
         */
        fun hiddenIn(stored: Collection<String>?): Set<AssetLayer> = stored
            ?.flatMapTo(mutableSetOf()) { id -> layersFor(id) }
            .orEmpty()

        /** The layers one stored id names: one, or - the old "places" - all three of them. */
        private fun layersFor(id: String): List<AssetLayer> = if (id == LEGACY_PLACES) {
            entries.filter { it.kind.shape == AssetShape.POINT }
        } else {
            entries.filter { it.id == id }
        }
    }
}
