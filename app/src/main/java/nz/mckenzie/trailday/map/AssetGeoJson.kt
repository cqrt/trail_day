package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.GeoPoint

/**
 * One stretch of a place's line, drawn in its own colour.
 *
 * The map needs these because a track can be in two states at once: half of it walked this
 * morning and half of it still to do. One colour for the whole line cannot say that.
 */
data class AssetStretch(
    val colorHex: String,
    val points: List<GeoPoint>,
    /**
     * Set on the one stretch of a track that carries the track's name - see
     * [nz.mckenzie.trailday.map.TrackNames]. The line layers leave it alone and the name layer draws
     * nothing else, so the name is written once and the stretch is never drawn as a second line.
     */
    val carriesName: Boolean = false
)

/** One planned asset ready to be drawn on the map. */
data class AssetLine(
    val assetId: Long,
    val name: String,
    val colorHex: String,
    val points: List<GeoPoint>,
    /**
     * What it is, which decides how it is drawn: solid, dashed, or a marker.
     *
     * Defaulted for the map's previews - a line being drawn, or a planned line shown
     * over a recording - which are not places and have no kind to be drawn by.
     */
    val kind: AssetKind = AssetKind.TRACK,
    /** A line to travel along, or a single place to stop at. */
    val shape: AssetShape = AssetShape.LINE,
    /**
     * The side tracks hanging off [points], drawn in the same colour as the line they leave.
     *
     * A track on the ground is not always one path - see
     * [nz.mckenzie.trailday.domain.geo.AssetGeometry] - so the map draws every path, and a tap on any
     * of them opens the same asset. Empty for a place, and for the previews above.
     */
    val sideTracks: List<List<GeoPoint>> = emptyList(),
    /**
     * Set when the line is not all in one state - a track half walked - in which case these
     * are drawn instead of the single line above, one feature each.
     *
     * Every one of them carries this asset's id, so a tap on any part of the line opens the
     * asset, and the map's layers filter on kind and shape exactly as they did before.
     */
    val stretches: List<AssetStretch> = emptyList()
) {

    /** Every path to draw: the line first, then its side tracks. */
    val paths: List<List<GeoPoint>> get() = listOf(points) + sideTracks
}

/**
 * Colours for what the map draws, matching the Compose theme (ui/theme/Color.kt).
 * Kept here as plain strings so the map layer can be unit tested on the JVM without
 * Compose on the classpath.
 */
object AssetColors {

    /**
     * The recorded line: the mock screens' green, with the gold edge below it.
     *
     * A green that is nobody's kind - the line is the evidence of what was walked or ridden, not one of
     * the places, and it must not be read as one. It is drawn wherever a line that has been walked is
     * drawn: the recorder, a trip's page, and a place on the map that a walk has been made of.
     */
    const val RECORDED = "#2E7D32"

    /**
     * The gold edge around the recorded line, as the mock screens draw it.
     *
     * Not a kind's colour and not the line's: it is a casing, saying "this is the line that was actually
     * walked" wherever a recorded line is drawn - the recorder, a trip's page, a trip's card.
     */
    const val RECORDED_EDGE = "#D89B2A"

    /** The recorded green, light enough to read on a dark card - derived, like the kinds'. */
    const val RECORDED_DARK = "#81C784"

    /**
     * One colour per kind of place, for the tile beside a place in the list and the line the map draws it
     * as - **the mock screens' colours**, which is what makes the list and the map agree about a hut.
     *
     * Five kinds and five colours, so the colour says which kind a thing is as well as the glyph does, and
     * the two lines, a track and a bike track, are told apart as much by their colour as by their dashes.
     */
    const val TRACK_KIND = "#1565C0"

    /** Bike tracks. */
    const val BIKE_TRACK_KIND = "#6A1B9A"

    /** Huts: orange in the mock screens, which is what a walker looks for on a hillside. */
    const val HUT_KIND = "#EF6C00"

    /** Campsites: unchanged, and the one kind the look did not move. */
    const val CAMPSITE_KIND = "#00897B"

    /** Waypoints: the mock screens' slate, the colour huts used to wear. */
    const val WAYPOINT_KIND = "#546E7A"

    /**
     * The same five kinds, for a dark background.
     *
     * **Derived, not invented**: each is the light colour's own hue taken to a lighter tone, because a
     * colour that reads on a white card disappears on a black one - which is exactly what a deep purple
     * and a brown did before the last look. The app follows the system theme, so the icon beside a place
     * and the tile behind it have to be readable in both.
     */
    const val TRACK_KIND_DARK = "#64B5F6"
    const val BIKE_TRACK_KIND_DARK = "#CE93D8"
    const val HUT_KIND_DARK = "#FFB74D"
    const val CAMPSITE_KIND_DARK = "#4DB6AC"
    const val WAYPOINT_KIND_DARK = "#90A4AE"

    /**
     * Where the phone is.
     *
     * Blue with a white ring, the way a device marker reads everywhere else. It must not be
     * readable as a kind colour: the blue here is deliberately not [TRACK_KIND]'s, and the white
     * ring - plus the fact that only one such dot is ever on the map at a time - is what keeps it from
     * being taken for a track.
     */
    const val POSITION = "#1A73E8"

    /**
     * The colour of the icon that says what a place *is*, and the line the map draws it as.
     */
    fun forKind(kind: AssetKind): String = when (kind) {
        AssetKind.TRACK -> TRACK_KIND
        AssetKind.BIKE_TRACK -> BIKE_TRACK_KIND
        AssetKind.HUT -> HUT_KIND
        AssetKind.CAMPSITE -> CAMPSITE_KIND
        AssetKind.WAYPOINT -> WAYPOINT_KIND
    }

    /**
     * The colour a place is drawn in: the recorded green when a walk has been made of it, its kind's own
     * colour otherwise.
     *
     * **A line that has been walked says so.** A place with a recorded pass is the line the walker actually
     * went along, and drawing it in its kind's colour would put it beside a line that was only ever drawn.
     * The map, the place's own page and the recorder all ask this, so they cannot disagree about it.
     */
    fun forPlace(kind: AssetKind, recorded: Boolean): String = if (recorded) RECORDED else forKind(kind)

    /**
     * The same kind of thing, on a dark background: [forKind]'s hue, light enough to read there.
     *
     * The map keeps the light colours whatever the theme: its lines and markers are over imagery rather
     * than over a card, and the white edges and casings are what make them readable there.
     */
    fun forKindOnDark(kind: AssetKind): String = when (kind) {
        AssetKind.TRACK -> TRACK_KIND_DARK
        AssetKind.BIKE_TRACK -> BIKE_TRACK_KIND_DARK
        AssetKind.HUT -> HUT_KIND_DARK
        AssetKind.CAMPSITE -> CAMPSITE_KIND_DARK
        AssetKind.WAYPOINT -> WAYPOINT_KIND_DARK
    }}

/**
 * Builds the GeoJSON that MapLibre draws for the place network.
 *
 * Each feature carries what the map needs and nothing more: its id for tapping, its
 * colour, its kind and its shape. The kind and shape are properties rather than baked
 * into the geometry because the layers read them - the kind picks which line layer draws
 * a feature, and a point place is drawn as a marker rather than a line at all. A place
 * also carries the name of the picture to draw it with ([ICON_PROPERTY]), because a
 * marker is one picture per kind and which picture a kind asks for belongs where the
 * kind is known.
 *
 * Hand-rolled rather than pulling in a JSON library: the payload is tiny, and
 * keeping it pure Kotlin means it is covered by fast JVM unit tests.
 */
object AssetGeoJson {

    /**
     * The property naming the picture a place is drawn with.
     *
     * The same idiom as [PositionGeoJson.PART_PROPERTY]: the feature says what it wants and
     * the layer reads it, so which picture a colour asks for is decided in Kotlin - and
     * tested there - rather than in a style expression nobody can read.
     */
    internal const val ICON_PROPERTY = "icon"

    private const val EMPTY = "{\"type\":\"FeatureCollection\",\"features\":[]}"

    fun build(lines: List<AssetLine>): String {
        // A line needs two points to be a line; a place needs only the one it is at. Any *path* of an
        // asset qualifies it: a track whose line is too short but whose side track is drawn is still
        // a track with something on the map.
        val drawable = lines.filter { line ->
            val least = if (line.shape == AssetShape.POINT) 1 else 2
            line.paths.any { it.size >= least }
        }
        if (drawable.isEmpty()) return EMPTY

        val builder = StringBuilder(128 + drawable.size * 256)
        builder.append("{\"type\":\"FeatureCollection\",\"features\":[")
        var written = 0
        drawable.forEach { line ->
            stretchesOf(line).forEach { stretch ->
                if (written > 0) builder.append(',')
                appendFeature(builder, line, stretch)
                written++
            }
        }
        builder.append("]}")
        return builder.toString()
    }

    /**
     * What one place is drawn as: every path of it in its own colour, or the stretches of those
     * paths that are in different states.
     *
     * A stretch too short to be a line is dropped rather than drawn as a dot where the line
     * should be, and a place whose stretches all came out that way falls back to being
     * drawn whole - a map that has lost a track is worse than one that shows it in one
     * colour.
     */
    private fun stretchesOf(line: AssetLine): List<AssetStretch> {
        if (line.shape == AssetShape.POINT) {
            return listOf(AssetStretch(colorHex = line.colorHex, points = line.points))
        }
        val stretches = line.stretches.filter { it.points.size >= 2 }
        val drawn = if (stretches.isNotEmpty()) {
            stretches
        } else {
            // Nothing part-done: the whole track, one feature per path. A side track is drawn in the same
            // colour as the line it leaves, because the kind is the whole track's answer.
            line.paths
                .filter { it.size >= 2 }
                .map { AssetStretch(colorHex = line.colorHex, points = it) }
        }
        return drawn + nameStretch(line)
    }

    /**
     * The one stretch of [line] that carries its name, or nothing when this line is not named.
     *
     * Only a track has a name written beside it, and only when there is one to write: a preview line - one
     * being drawn, or a planned line shown over a recording - has no name at all, and a bike track is
     * not a name the map writes. The stretch is taken from the track's own points rather than
     * from the pieces above, so a track that is part walked still carries its name once, near the middle
     * of the whole track.
     */
    private fun nameStretch(line: AssetLine): List<AssetStretch> {
        if (line.kind != AssetKind.TRACK || line.shape != AssetShape.LINE) return emptyList()
        if (line.name.isBlank()) return emptyList()
        val piece = TrackNames.pieceOf(line.points)
        if (piece.size < 2) return emptyList()
        return listOf(AssetStretch(colorHex = line.colorHex, points = piece, carriesName = true))
    }

    private fun appendFeature(builder: StringBuilder, line: AssetLine, stretch: AssetStretch) {
        builder.append("{\"type\":\"Feature\",\"properties\":{")
        builder.append("\"id\":").append(line.assetId).append(',')
        builder.append("\"name\":\"").append(escape(line.name)).append("\",")
        builder.append("\"stroke\":\"").append(escape(stretch.colorHex)).append("\",")
        builder.append("\"kind\":\"").append(line.kind.name).append("\",")
        builder.append("\"shape\":\"").append(line.shape.name).append("\"")
        // The one stretch that carries the name is marked, so the line layers can leave it alone and the
        // name layer can draw nothing else.
        if (stretch.carriesName) {
            builder.append(",\"").append(TrackNames.CARRIES_NAME).append("\":true")
        }
        // Only a place is drawn as a picture. A line carries its colour and the layer draws
        // it, so a line has no picture to ask for and does not carry this property at all.
        if (line.shape == AssetShape.POINT) {
            builder.append(",\"").append(ICON_PROPERTY).append("\":\"")
                .append(escape(PlaceIcons.imageName(line.kind))).append("\"")
        }
        builder.append("},\"geometry\":{")
        if (line.shape == AssetShape.POINT) {
            builder.append("\"type\":\"Point\",\"coordinates\":")
            appendPoint(builder, stretch.points.first())
        } else {
            builder.append("\"type\":\"LineString\",\"coordinates\":[")
            stretch.points.forEachIndexed { pointIndex, point ->
                if (pointIndex > 0) builder.append(',')
                appendPoint(builder, point)
            }
            builder.append("]")
        }
        builder.append("}}")
    }

    /** GeoJSON is [longitude, latitude] - the opposite of how humans say it. */
    private fun appendPoint(builder: StringBuilder, point: GeoPoint) {
        builder.append('[').append(format(point.lng)).append(',')
            .append(format(point.lat)).append(']')
    }

    private fun format(value: Double): String =
        String.format(java.util.Locale.US, "%.7f", value)

    internal fun escape(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) append(' ') else append(ch)
            }
        }
    }
}
