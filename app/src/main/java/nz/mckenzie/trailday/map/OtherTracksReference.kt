package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
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

    /**
     * The colour of somebody else's track.
     *
     * **Chosen against the colours already on the map**, which is the whole of the reasoning: the
     * walker's own tracks and DOC's are both blue (`#1565C0`), DOC's bike trails are purple
     * (`#6A1B9A`) and the Te Araroa trail is rust (`#C1440E`). A teal is the gap left in that set, and
     * it reads over green bush, pale scree and water alike.
     *
     * The casing under it is **not** given its own copy: there is one white casing on this map, under
     * every line that is not the walker's own, and it is [DocLayerIds.CASING_COLOUR]'s.
     */
    const val COLOUR = "#00695C"

    /**
     * The same colour for a dark screen.
     *
     * The layers sheet draws this layer's tile in the layer's own colour, so that the sheet and the map
     * agree about whose lines are whose - and a deep teal glyph on a dark tile is a glyph nobody sees,
     * which is the reason [nz.mckenzie.trailday.map.AssetColors] keeps two sets of every kind.
     */
    const val COLOUR_DARK = "#4DB6AC"
}

/** Somebody else's tracks as the map's own document, ready for their source. */
object OtherTracksGeoJson {

    /**
     * [tracks] as one GeoJSON document for the map to draw.
     *
     * Through [AssetGeoJson], as DOC's material is: the map draws every line with one builder, and a
     * second one for the same shape is how the two come to differ about a width or a cap. The colour is
     * the layer's own and is set on the layer rather than read from the feature - see
     * [OtherTracksLayerIds.COLOUR] - but it is written here too, so a feature drawn without its layer
     * still comes out in the right colour rather than the default.
     */
    fun build(tracks: List<OtherTrack>): String = AssetGeoJson.build(
        tracks.map { track ->
            AssetLine(
                // No asset id: nothing on the map can act on one of these, and an id that matched one
                // of the walker's own places would be a tap opening the wrong thing.
                assetId = 0L,
                name = track.name,
                colorHex = OtherTracksLayerIds.COLOUR,
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
