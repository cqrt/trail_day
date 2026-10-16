package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
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
 * **A bike location is drawn as a place**, because that is what DOC publishes - a point where riding is
 * permitted, with a difficulty and a time. The app's marker vocabulary has no bicycle for a single
 * spot (its bicycle is a line kind, because a bike track is a line), and inventing one here would put
 * a marking of the reference layer's into the walker's own kind picker. What it is - a place, and one
 * DOC says is for riding - is said in words instead, on its row and its card.
 */
fun DocSource.drawingKind(): AssetKind = when (this) {
    DocSource.TRACKS -> AssetKind.TRACK
    DocSource.HUTS -> AssetKind.HUT
    DocSource.CAMPSITES -> AssetKind.CAMPSITE
    DocSource.BIKES -> AssetKind.WAYPOINT
}

/** The shape DOC published, in the app's own words: a line or a single place. */
fun DocSource.drawingShape(): AssetShape =
    if (isLine) AssetShape.LINE else AssetShape.POINT

/**
 * The style layers DOC's material is drawn by.
 *
 * Its own names rather than the walker's, because they are a different thing on the map: the reference
 * layer has no switches, is drawn under the work, and cannot be tapped into anything.
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

    /** How much wider the casing is than the line it holds, in pixels. */
    const val CASING_WIDTH_PX = 4f

    /** The casing's colour: the same white a marker's edge wears, for the same reason. */
    const val CASING_COLOUR = "#FFFFFF"

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
     * The app's own place kinds, because that is what DOC publishes: a hut, a campsite, and a bike
     * location - which is a place where riding is permitted, and is drawn as the place it is. See
     * [drawingKind].
     *
     * Declared before [ALL] because an object's properties are built in the order they are written, and
     * [ALL] is read from this one.
     */
    val REACHABLE_MARKER_KINDS: List<AssetKind> = PlaceIcons.KINDS

    /** Every layer DOC's material is drawn by, in the order the style builds them. */
    val ALL: List<String> = listOf(CASING, TRACKS) + REACHABLE_MARKER_KINDS.map { pointOf(it) }

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
    fun build(items: List<DocItem>): String = AssetGeoJson.build(
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
                shape = item.source.drawingShape()
            )
        }
    )
}
