package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.look.Look
import nz.mckenzie.trailday.domain.look.LookTarget
import nz.mckenzie.trailday.domain.look.MapLook
import nz.mckenzie.trailday.domain.other.OtherTrack

/**
 * The national tracks dataset's rows, drawn under the walker's own work **beside** DOC's.
 *
 * A second reference layer rather than a second colour inside DOC's, because it is a second publisher:
 * what is on the map is council walkways, trail trusts' tracks and the Commission's own merges, and a
 * walker who taps one is not looking at something DOC published. Nothing here is an asset - these
 * features have no id of the app's own, so nothing on the map can rename, move or delete one.
 *
 * **Cased in white, like DOC's**, and for the same reason: a line over a photograph has to say where it
 * is before it says what colour it is, and the walker's own lines are uncased - so the difference
 * between "somebody else publishes this" and "I made this" is visible at a glance rather than needing
 * the layer switches read.
 */
object OtherTracksLayerIds {

    /** The GeoJSON source the rows are handed to the style in. */
    const val SOURCE = "trailday-other-tracks"

    /** The white line under every one of them. */
    const val CASING = "trailday-other-tracks-casing"

    /** The tracks themselves, over their own casing. */
    const val LINE = "trailday-other-tracks-line"
}

/** Somebody else's tracks as the map's own document, ready for their source. */
object OtherTracksGeoJson {

    /**
     * [tracks] as one GeoJSON document for the map to draw.
     *
     * Through [AssetGeoJson], as DOC's material is: the map draws every line with one builder, and a
     * second one for the same shape is how the two come to differ about a width or a cap. The colour is
     * the walker's choice for the other tracks' row, written here so a feature drawn without its layer
     * still comes out in the right colour rather than the default.
     */
    fun build(tracks: List<OtherTrack>, look: MapLook = Look.value): String = AssetGeoJson.build(
        tracks.map { track ->
            AssetLine(
                // No asset id: nothing on the map can act on one of these, and an id that matched one
                // of the walker's own places would be a tap opening the wrong thing.
                assetId = 0L,
                name = track.name,
                colorHex = look.colourOf(LookTarget.OTHER_TRACKS).hex,
                points = track.paths.firstOrNull().orEmpty(),
                sideTracks = track.paths.drop(1),
                kind = AssetKind.TRACK,
                shape = AssetShape.LINE
            )
        }
    )

    /** What a map with nothing downloaded draws: the same empty document every other source gets. */
    val EMPTY: String = AssetGeoJson.build(emptyList())
}
