package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.look.Look
import nz.mckenzie.trailday.domain.look.LookTarget
import nz.mckenzie.trailday.domain.look.MapLook

/**
 * The line being followed, as the document the recorder's map draws it from.
 *
 * **One document for both kinds of target**: one of the walker's own places, or a line somebody
 * published. The recorder asks the same question of both - is the line underfoot the line I chose? -
 * and for a long time only the walker's own places were answered. Following one of DOC's tracks, or a
 * council's, drew nothing at all, so the only line on the screen was the one being recorded and it had
 * nothing to be a match for.
 *
 * The colour is the row's own, as it is everywhere else on the map: a followed track wears whatever the
 * walker has set Tracks to, and somebody else's wears Other tracks. What makes it read as the line the
 * screen is about is the width, which the map gives the target layer and no other - see
 * [LineWeight.followed] in [BasemapView].
 *
 * A target with nothing to draw is an empty collection rather than a failure, because most recordings
 * follow nothing at all.
 */
object TargetGeoJson {

    /**
     * [paths] as the one line the recorder draws over the map, or an empty collection.
     *
     * Every path of the target is drawn, not just the one underfoot: a track published in pieces is one
     * walkway to the walker, and the piece below them is only readable against the rest of it.
     *
     * [name] and [target] are both nullable so that "nothing is being followed" needs no second entry
     * point - the same call with nothing in it is the answer.
     */
    fun build(
        name: String?,
        target: LookTarget?,
        paths: List<List<GeoPoint>>,
        kind: AssetKind = AssetKind.TRACK,
        look: MapLook = Look.value
    ): String {
        val lines = paths.filter { it.size >= 2 }
        if (name == null || target == null || lines.isEmpty()) return empty()

        return AssetGeoJson.build(
            listOf(
                AssetLine(
                    // No asset id: a followed line is not one of the walker's places, and an id that
                    // matched one of them would be a tap opening the wrong thing.
                    assetId = 0L,
                    name = name,
                    colorHex = look.colourOf(target).hex,
                    points = lines.first(),
                    sideTracks = lines.drop(1),
                    kind = kind,
                    shape = AssetShape.LINE
                )
            )
        )
    }

    /** Nothing being followed, as the document the map draws nothing from. */
    fun empty(): String = AssetGeoJson.build(emptyList())
}
