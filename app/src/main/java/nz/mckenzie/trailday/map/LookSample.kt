package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.look.LookTarget
import nz.mckenzie.trailday.domain.look.MapLook
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import kotlin.math.cos

/**
 * A sample of everything the walker can colour, drawn around one place: **the look screen's preview.**
 *
 * The look screen has to show what a choice does before the walker leaves it, and a swatch on a card
 * does not do that - a colour on a card says nothing about how it reads over aerial imagery. So the
 * preview is the map itself, with one of each thing drawn on it in the colours and widths in force.
 *
 * **Seven things, and every row of the screen is one of them.** Te Araroa is the line across the top,
 * because it is drawn under everything on the map: the one thing in the picture that is a route rather
 * than somebody's work. A track and a bike track are the walker's own lines; the DOC line is the same
 * track drawn from a publisher's source, so the white edge that only a published line wears is in the
 * picture; the fourth is the other tracks' line. Then a hut, a campsite and a waypoint. Nothing is drawn
 * that the screen cannot change, so what the walker sees is exactly what a tap affects.
 *
 * **The trail's colour is not in its document.** On the map the trail is drawn by a layer whose colour
 * is set from the look, so the sample's trail is a geometry and nothing more - which is why it is built
 * like the others and read for its shape alone.
 *
 * **Laid out in metres, not in map units.** The preview follows the ground the map was left on, which
 * may be a valley or a coastline, so the sample is placed relative to a centre and comes out the same
 * size wherever that centre is. A sample in fractional degrees would be a different picture in
 * Invercargill than in Kaitaia.
 *
 * A kilometre across at the preview's zoom is a picture a few hundred pixels wide: close enough to read
 * a colour, far enough apart that nothing touches. The builder is the type's companion so that a sample
 * cannot be built for one look and drawn beside another.
 */
data class LookSample(
    /** The walker's own work: a track, a bike track and the three places, in one source. */
    val assets: String,
    /** The same kind of line as a publisher's, which is the one that wears the white edge. */
    val doc: String,
    /** The councils' and trusts' line, in its own row's colour. */
    val otherTracks: String,
    /** Te Araroa, whose colour the layer has: see [MapLook.colourOf]. */
    val trail: String
) {

    companion object {

        /** How far east or west of the centre a sample lands, in metres. A test holds everything inside. */
        const val HALF_WIDTH_M = 480.0

        /** How far north or south of the centre a sample lands, in metres. */
        const val HALF_HEIGHT_M = 460.0

        /** A degree of latitude, in metres. The same number anywhere on the globe. */
        private const val METRES_PER_DEGREE = 111_320.0

        /** The five lines, north to south, and the row under them that the three places sit in. */
        private const val TRAIL_NORTH_M = 380.0
        private const val TRACK_NORTH_M = 240.0
        private const val BIKE_NORTH_M = 100.0
        private const val DOC_NORTH_M = -40.0
        private const val OTHER_NORTH_M = -180.0
        private const val PLACES_NORTH_M = -400.0

        /** The places, west to east: a hut, a campsite and the walker's own waypoint. */
        private val PLACE_EAST_M = listOf(
            AssetKind.HUT to -300.0,
            AssetKind.CAMPSITE to 0.0,
            AssetKind.WAYPOINT to 300.0
        )

        /**
         * The sample drawn for [centre] in [look].
         *
         * The colours are written into the features rather than left to the layers, so the sample
         * matches the map as the map draws it: a line's colour travels in its own document, and a
         * marker asks for a picture by name.
         */
        fun build(centre: GeoPoint, look: MapLook): LookSample = LookSample(
            assets = AssetGeoJson.build(
                listOf(
                    AssetLine(
                        assetId = 0L,
                        name = "",
                        colorHex = look.colourOf(LookTarget.TRACKS).hex,
                        points = line(centre, TRACK_NORTH_M)
                    ),
                    AssetLine(
                        assetId = 0L,
                        name = "",
                        colorHex = look.colourOf(LookTarget.BIKE_TRACKS).hex,
                        points = line(centre, BIKE_NORTH_M),
                        kind = AssetKind.BIKE_TRACK
                    )
                ) + PLACE_EAST_M.map { (kind, east) ->
                    AssetLine(
                        assetId = 0L,
                        name = "",
                        colorHex = look.colourOf(kind).hex,
                        points = listOf(at(centre, eastM = east, northM = PLACES_NORTH_M)),
                        kind = kind,
                        shape = AssetShape.POINT
                    )
                }
            ),
            doc = AssetGeoJson.build(
                listOf(
                    AssetLine(
                        assetId = 0L,
                        name = "",
                        colorHex = look.colourOf(LookTarget.TRACKS).hex,
                        points = line(centre, DOC_NORTH_M)
                    )
                )
            ),
            otherTracks = AssetGeoJson.build(
                listOf(
                    AssetLine(
                        assetId = 0L,
                        name = "",
                        colorHex = look.colourOf(LookTarget.OTHER_TRACKS).hex,
                        points = line(centre, OTHER_NORTH_M)
                    )
                )
            ),
            // The trail's colour is the layer's, not its document's; the hex below is written because
            // every line this builder draws carries one, and the trail layer never reads it.
            trail = AssetGeoJson.build(
                listOf(
                    AssetLine(
                        assetId = 0L,
                        name = "",
                        colorHex = look.colourOf(LookTarget.TE_ARAROA).hex,
                        points = line(centre, TRAIL_NORTH_M)
                    )
                )
            )
        )

        /**
         * The box [build] draws [centre] in, for a map that has to frame the whole sample.
         *
         * The preview is a small map with no gestures, so how much of the sample it shows cannot be
         * left to a zoom somebody picked: at the map's own reading zoom the box is a fifth of the
         * preview and most of what it draws is off the edge. Framing the box is the same answer as
         * the map's own "fit my tracks", and the padding it brings is what keeps the sample clear of
         * the credit strip along the foot.
         */
        fun bounds(centre: GeoPoint): LatLngBounds {
            val northSouth = HALF_HEIGHT_M / METRES_PER_DEGREE
            val eastWest = HALF_WIDTH_M / (METRES_PER_DEGREE * cos(Math.toRadians(centre.lat)))
            return LatLngBounds(
                minLat = centre.lat - northSouth,
                minLng = centre.lng - eastWest,
                maxLat = centre.lat + northSouth,
                maxLng = centre.lng + eastWest
            )
        }

        /**
         * A line with a bend in it, [northM] from [centre].
         *
         * Not a straight line: a straight line says how wide and how coloured, and nothing about whether
         * two lines close together can still be told apart - which is the question a heavier width is
         * asked over a valley of DOC's tracks.
         */
        private fun line(centre: GeoPoint, northM: Double): List<GeoPoint> =
            listOf(-420.0, -140.0, 140.0, 420.0).mapIndexed { index, east ->
                val wobble = if (index % 2 == 0) 40.0 else -40.0
                at(centre, eastM = east, northM = northM + wobble)
            }

        /**
         * The point [eastM] east and [northM] north of [centre].
         *
         * The longitude step shrinks with latitude, which is the one piece of arithmetic worth naming: a
         * metre east is a longer walk in degrees at Invercargill than at Kaitaia, and a sample that
         * ignored it would come out stretched north to south.
         */
        private fun at(centre: GeoPoint, eastM: Double, northM: Double): GeoPoint = GeoPoint(
            lat = centre.lat + northM / METRES_PER_DEGREE,
            lng = centre.lng + eastM / (METRES_PER_DEGREE * cos(Math.toRadians(centre.lat)))
        )
    }
}
