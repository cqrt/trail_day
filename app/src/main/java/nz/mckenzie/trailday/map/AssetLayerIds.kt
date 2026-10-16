package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetLayer

/**
 * Which style layer each layer of the work is drawn by.
 *
 * The map is built from a layer per line kind - a track and a bike track - and a marker layer per
 * place kind, because a dash pattern is a constant in MapLibre rather than something a feature can
 * carry, and a kind can only be hidden on its own if it is drawn on its own. This is the only place
 * that says which of them a switch hides. A table rather than a `when` buried in the map view, so
 * that a switch and the layer it hides cannot drift apart without a test noticing: every layer a
 * walker can hide has a layer to hide, and no two share one.
 */
object AssetLayerIds {

    /** Solid lines: tracks. */
    const val TRACKS = "trailday-assets-line-track"

    /** Dashed lines: bike tracks. */
    const val BIKE_TRACKS = "trailday-assets-line-bike-track"

    /**
     * The names beside tracks.
     *
     * Not a layer *of* the work but a layer *about* it: it draws the one short stretch of each track
     * that carries the track's name, and nothing else - see [nz.mckenzie.trailday.map.TrackNames]. It is
     * hidden with the tracks it names, because a name left behind by its track would be a label on
     * nothing at all.
     */
    const val TRACK_NAMES = "trailday-assets-line-track-names"

    /**
     * The marker layer a [kind] of place is drawn by.
     *
     * Named from the kind rather than listed, so a kind added to the app cannot arrive without a
     * layer to be drawn in - and the three names cannot drift from the three kinds.
     */
    fun pointOf(kind: AssetKind): String =
        POINT_PREFIX + kind.name.lowercase().replace('_', '-')

    /**
     * The style layer [layer] is drawn by - its one layer, except for a track, whose name layer is
     * hidden with it. See [idsOf] for what a switch hides.
     */
    fun of(layer: AssetLayer): String = when (layer.kind) {
        AssetKind.TRACK -> TRACKS
        AssetKind.BIKE_TRACK -> BIKE_TRACKS
        else -> pointOf(layer.kind)
    }

    /**
     * Every style layer [layer] is drawn by.
     *
     * One for every layer of the work but a track, which is two: a track's own line and the name
     * written beside it are hidden together. A switch hides what this returns rather than [of]
     * alone, so switching *Tracks* off cannot leave a name hanging on nothing.
     */
    fun idsOf(layer: AssetLayer): List<String> = when (layer.kind) {
        AssetKind.TRACK -> listOf(TRACKS, TRACK_NAMES)
        else -> listOf(of(layer))
    }

    /**
     * Every layer the map draws, in the order the style builds and draws them.
     *
     * The lines first, the names after them so a name is written over the lines it belongs to, and
     * the places last so a name never covers a place: a marker not drawn reads as a marker that has
     * been deleted.
     */
    val ALL: List<String> =
        listOf(TRACKS, BIKE_TRACKS, TRACK_NAMES) +
            PlaceIcons.KINDS.map { pointOf(it) }

    private const val POINT_PREFIX = "trailday-assets-point-"
}
