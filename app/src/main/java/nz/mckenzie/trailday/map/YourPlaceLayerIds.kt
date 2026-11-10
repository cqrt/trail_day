package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.asset.YourPlaceLayer

/**
 * Which style layers each part of the walker's own work is drawn by.
 *
 * The counterpart of [DocLayerIds] for the other half of the sheet: a tile under **Your Places**
 * names one of these, and this is the only table that says what it hides. The two special edges -
 * the gold under a recorded planned line and the gold under a raw recording - are here because
 * they have to travel with the line they edge, not with a single switch for every recording.
 */
object YourPlaceLayerIds {

    /** The gold edge under a planned or imported track that a pass has been recorded against. */
    const val RECORDED_TRACK_CASING = "trailday-recorded-track-casing"

    /** The same edge under a planned or imported bike track. */
    const val RECORDED_BIKE_CASING = "trailday-recorded-bike-casing"

    /** The gold edge under the raw recorded lines, from [BasemapView.YOUR_TRACKS_SOURCE]. */
    const val YOUR_TRACKS_CASING = "trailday-your-tracks-casing"

    /** The green core of the raw recorded walks. */
    const val YOUR_TRACKS_LINE = "trailday-your-tracks-line"

    /**
     * The gold edge under a recorded ride, in the bike track's width.
     *
     * Its own layer rather than one edge for both, for the reason every kind has its own line: a ride is
     * drawn at the walker's bike width and dashed, so an edge cut for a walk would either be buried
     * under the ride or stand out around it.
     */
    const val YOUR_TRACKS_RIDE_CASING = "trailday-your-tracks-ride-casing"

    /** The colour a recorded ride was given - the walker's bike colour - at the bike width and dash. */
    const val YOUR_TRACKS_RIDE_LINE = "trailday-your-tracks-ride-line"

    /**
     * Every style layer [layer] hides.
     *
     * A spot is every point kind of the walker's own: one marker layer each, because a kind can
     * only be hidden on its own if it is drawn on its own. A track takes its name and its recorded
     * edge with it; a bike track takes its recorded edge. A recording is the four layers of the raw
     * recorded source - its walks and its rides, each with the gold under it - because they are one
     * thing to the walker, who switches off yesterday's trips rather than yesterday's rides.
     */
    fun idsOf(layer: YourPlaceLayer): List<String> = when (layer) {
        YourPlaceLayer.SPOTS -> AssetKind.entries
            .filter { it.shape == AssetShape.POINT }
            .map { AssetLayerIds.pointOf(it) }

        YourPlaceLayer.TRACKS -> listOf(
            AssetLayerIds.TRACKS,
            AssetLayerIds.TRACK_NAMES,
            RECORDED_TRACK_CASING
        )

        YourPlaceLayer.BIKE_TRACKS -> listOf(
            AssetLayerIds.BIKE_TRACKS,
            RECORDED_BIKE_CASING
        )

        YourPlaceLayer.RECORDINGS -> listOf(
            YOUR_TRACKS_CASING,
            YOUR_TRACKS_LINE,
            YOUR_TRACKS_RIDE_CASING,
            YOUR_TRACKS_RIDE_LINE
        )
    }
}
