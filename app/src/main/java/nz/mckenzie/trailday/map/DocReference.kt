package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetLayer
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource

/**
 * DOC's material, drawn under the walker's own work.
 *
 * This is the reference layer the plan calls for: the tracks, huts, campsites and bike locations DOC
 * publishes, kept on the phone and drawn so a walk can be planned against them - and **not** the
 * walker's to change. Nothing here is an asset: these features have no id of their own in the app's
 * tables, so nothing on the map can rename, move or delete one, and a tap on one opens nothing.
 *
 * Two things make DOC's material readable as *not yours*, and both matter more than the pixels:
 *
 *  - **Every DOC line is cased in white.** A photograph of the ground has dark trees, pale scree and
 *    everything between, and a line over it has to say where it is before it says what colour it is;
 *    the walker's own lines are uncased, so the difference between "a track DOC publishes" and "a track
 *    I made" is visible at a glance rather than needing the layer switches read.
 *  - **DOC's bike locations are markers, not lines**, because that is what DOC publishes - see
 *    [DocSource.BIKES]. Drawing them as a route would be the app inventing one.
 */

/**
 * The app's own kind of thing that a service's material is drawn as.
 *
 * **Drawing only.** A DOC item is not one of the app's kinds and never becomes one: this is the colour
 * and the glyph a source's rows wear on the map, and the app's kinds happen to be exactly the
 * vocabulary for that.
 *
 * **A bike location is drawn with the bicycle**, because it is a bike trail, not a waypoint: DOC
 * publishes a point where riding is permitted, with a difficulty and a time, and the app draws every
 * one of those with the bike badge. It does not become a place of the walker's own, and the browser's
 * tile says what it is.
 */
fun DocSource.drawingKind(): AssetKind = when (this) {
    DocSource.TRACKS -> AssetKind.TRACK
    DocSource.HUTS -> AssetKind.HUT
    DocSource.CAMPSITES -> AssetKind.CAMPSITE
    DocSource.BIKES -> AssetKind.BIKE_TRACK
}

/** The shape DOC published, in the app's own words: a line or a single place. */
fun DocSource.drawingShape(): AssetShape =
    if (isLine) AssetShape.LINE else AssetShape.POINT

/**
 * The style layers DOC's material is drawn by.
 *
 * Its own names rather than the walker's, because they are a different thing on the map: the reference
 * layer has no switches, is drawn under the work, and has no editor: a tap on it is answered by the map's card.
 */
object DocLayerIds {

    /**
     * The white line under every DOC track.
     *
     * A route over a photograph has to be readable against dark bush, pale scree and everything
     * between, and the walker's own lines are uncased - so the casing is also what says, at a glance,
     * which lines are DOC's and which are yours. The mock screens draw their track exactly this way.
     */
    const val CASING = "trailday-doc-casing"

    /** DOC's tracks, over their own casing. */
    const val TRACKS = "trailday-doc-line-tracks"

    /** The white edge under the tracks DOC says may be ridden. */
    const val BIKE_CASING = "trailday-doc-bike-casing"

    /** DOC's tracks that can also be ridden, over their own casing. */
    const val BIKE_TRACKS = "trailday-doc-line-bike-tracks"

    /**
     * The bicycle badges for every bike point DOC publishes.
     *
     * A badge rather than a place marker: a bike point on a track is a note about that track, and one
     * with no track is still a bike trail rather than a spot. Both are drawn with the bicycle and
     * hidden by the Bike trails switch - along with the bike-friendly tracks themselves.
     */
    const val BIKE_BADGES = "trailday-doc-bike-badges"

    /**
     * The marker layer one kind of DOC place is drawn by.
     *
     * Named from the kind for the same reason the walker's own are: a kind added to the app cannot
     * arrive without a layer to be drawn in.
     */
    fun pointOf(kind: AssetKind): String =
        POINT_PREFIX + kind.name.lowercase().replace('_', '-')

    /**
     * The kinds DOC publishes a place as, which is what the reference layer needs a marker for.
     *
     * A hut and a campsite. A bike point is not a place here: it is a bike trail, drawn as a bicycle
     * badge by [BIKE_BADGES], whether it sits on a track or stands alone. The walker's waypoint kind
     * has no DOC layer at all now.
     *
     * Declared before [ALL] because an object's properties are built in the order they are written, and
     * [ALL] is read from this one.
     */
    val REACHABLE_MARKER_KINDS: List<AssetKind> =
        PlaceIcons.KINDS.filterNot { it == AssetKind.WAYPOINT }

    /** Every layer DOC's material is drawn by, in the order the style builds them. */
    val ALL: List<String> =
        listOf(CASING, TRACKS, BIKE_CASING, BIKE_TRACKS, BIKE_BADGES) +
            REACHABLE_MARKER_KINDS.map { pointOf(it) }

    /**
     * The style layers [layer] hides of DOC's own material, when the walker switches that layer off.
     *
     * **Tracks is every DOC track; Bike trails adds the rideable view.** Tracks draws the whole
     * network, including dual-use tracks, so a walker with Bike trails off still sees the tracks
     * they can walk. Bike trails adds the bicycle badges, and draws the rideable subset on its own
     * when Tracks is off, so a bike-only view is not empty. A bike point is a bike trail, so its
     * badge goes with the Bike trails switch.
     */
    fun idsOf(layer: AssetLayer): List<String> = when (layer) {
        // The casing is under the line it edges, so it is hidden with it.
        AssetLayer.TRACKS -> listOf(CASING, TRACKS)
        AssetLayer.BIKE_TRACKS -> listOf(BIKE_CASING, BIKE_TRACKS, BIKE_BADGES)
        else -> listOf(pointOf(layer.kind))
    }

    private const val POINT_PREFIX = "trailday-doc-point-"
}

/** DOC's material as the map's own document, ready for the reference source. */
object DocGeoJson {

    /**
     * [items] as one GeoJSON document for the map to draw.
     *
     * Through [AssetGeoJson] rather than written again here: the map draws the walker's lines and
     * places with one builder, and a second one for the same shapes is how the two come to differ about
     * a dash or an icon. What this adds is only what makes a feature DOC's - which source it came from,
     * for the layer that draws it.
     */
    fun build(
        items: List<DocItem>,
        bikeFriendlyTracks: Set<String> = emptySet()
    ): String = AssetGeoJson.build(
        items.map { item ->
            AssetLine(
                // No asset id: nothing on the map can act on a DOC row, and an id that matched one of
                // the walker's places would be a tap opening the wrong thing.
                assetId = 0L,
                name = item.name,
                colorHex = AssetColors.forKind(item.source.drawingKind()),
                points = item.paths.firstOrNull().orEmpty(),
                sideTracks = item.paths.drop(1),
                kind = item.source.drawingKind(),
                shape = item.source.drawingShape(),
                bikeFriendly = item.source == DocSource.TRACKS &&
                    item.sourceRef in bikeFriendlyTracks
            )
        }
    )

    /**
     * The bike points that belong to tracks, as a document of their own.
     *
     * A separate source from the places because the badge layer draws a bicycle over the track, not a
     * waypoint pin: the same point cannot be both a destination and a note about a line. The badge
     * carries only the place it marks; the track it belongs to is the thing that is tapped.
     */
    fun buildBadges(bikes: List<DocItem>): String = AssetGeoJson.build(
        bikes.map { bike ->
            AssetLine(
                assetId = 0L,
                name = bike.name,
                colorHex = AssetColors.forKind(bike.source.drawingKind()),
                points = bike.paths.firstOrNull().orEmpty(),
                kind = bike.source.drawingKind(),
                shape = AssetShape.POINT
            )
        }
    )
}
