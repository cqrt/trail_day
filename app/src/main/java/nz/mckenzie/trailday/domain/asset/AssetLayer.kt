package nz.mckenzie.trailday.domain.asset

/**
 * One part of DOC's reference material on the map, as the walker's switches see it.
 *
 * **DOC's half of the sheet, not the walker's.** The walker's own spots, tracks, bike tracks and
 * recordings are grouped under [YourPlaceLayer]; these four switches are the material DOC publishes
 * underneath that work. The reason to hide a layer is still to read another: a network of DOC tracks
 * under a set of spots is noise, and a scatter of spots buries the one DOC hut being looked for.
 *
 * **Tracks and Bike trails are two views of DOC's lines, not two exclusive halves.** Tracks is
 * every DOC track, including the dual-use ones; Bike trails adds the bicycle badges, and draws the
 * rideable subset when Tracks is off. A walker with Tracks on therefore never loses a dual-use track
 * because Bike trails is off, and a rider with Tracks off still has a map with lines on it. The
 * walker's own bike tracks are not here - they are under [YourPlaceLayer.BIKE_TRACKS].
 *
 * [kind] is the kind the layer is drawn as, which is what the map's own style filters on and what
 * the marker's shape and colour are read from.
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
        displayName = "Bike trails",
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
    );

    companion object {

        /** Everything that can be shown or hidden, in the order the switches list them. */
        val ALL: List<AssetLayer> = entries

        /**
         * The id an older build stored for the one switch that hid every place.
         *
         * Before the map had a switch per kind, the fifth kind of thing was "places". The two place
         * kinds DOC publishes are read back from it rather than dropped - a hidden layer coming back
         * is a map that has quietly changed under somebody. The walker's own spots are a
         * [YourPlaceLayer] now and have their own legacy mapping.
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

        /** The layers one stored id names: one, or - the old "places" - both DOC place kinds. */
        private fun layersFor(id: String): List<AssetLayer> = if (id == LEGACY_PLACES) {
            entries.filter { it.kind.shape == AssetShape.POINT }
        } else {
            entries.filter { it.id == id }
        }
    }
}
