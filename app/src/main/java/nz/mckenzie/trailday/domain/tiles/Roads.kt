package nz.mckenzie.trailday.domain.tiles

/**
 * The roads LINZ publishes, drawn over the aerial imagery.
 *
 * **The problem this answers.** The aerial imagery is a photograph, so a road is a grey ribbon in a
 * paddock with nothing to say where it goes. The drawn map has the roads, but it is a different
 * basemap: a walker reading the photograph to work out where they are cannot see that the ribbon
 * under their feet is State Highway 73. So this draws the roads on top of the photograph, and names
 * them.
 *
 * **Where they come from.** The `streets` layer of the very same vector tiles the place names come
 * from, which is the same service, key and licence as the imagery. Nothing new is trusted, nothing
 * new is fetched: a tile already carries both the names and the roads, so with the names on the roads
 * cost nothing at all. See [PlaceLabels].
 *
 * **Why they are gold.** Because that is LINZ's own road colour on their Topographic map, and a road
 * drawn in somebody else's blue over their photograph would be a second opinion. The casing is a
 * dark grey rather than black: it is there to separate the line from a pale roof, not to draw with.
 *
 * **Four lines rather than LINZ's thirty.** Their style separates roads by lane count, surface and
 * class into a dozen widths each, because it draws a whole cartographic map. Over a photograph the
 * question is only ever "which of these is a road, and which is a track", so this asks it four times:
 * a highway, a road, an unmetalled road, and a track.
 *
 * **Both maps read this file.** The phone's style and the desk's are built by different code - see
 * [nz.mckenzie.trailday.map.BasemapStyles] and [nz.mckenzie.trailday.map.WebStyleJson] - and this is
 * where the numbers they share live, so a road looks the same on both.
 */
object Roads {

    /**
     * The layer inside LINZ's vector tiles that carries the roads.
     *
     * `streets` is also what the place-name tiles carry, which is the whole reason this feature costs
     * no extra download. Read out of real tiles rather than off a page: `kind`, `surface`,
     * `lane_count`, `track_use`, `tunnel` and `name` are its properties.
     */
    const val SOURCE_LAYER = "streets"

    /** The property a road's name is in. Not `label` - that is the place names' property. */
    const val NAME_PROPERTY = "name"

    /** The deepest zoom LINZ publishes these tiles to. Past it the map overzooms them. */
    const val MAX_ZOOM = PlaceLabels.MAX_ZOOM

    /**
     * The colour of a sealed road: LINZ's own, from their Topographic style.
     *
     * Taken from their style document rather than mixed by eye, so a road here is the same road as on
     * the drawn sheet - `rgba(210, 162, 84, 1)` there.
     */
    const val COLOUR = "#D2A254"

    /** An unmetalled road: the same colour, taken towards white, and dashed. */
    const val UNMETALLED_COLOUR = "#EBD3A8"

    /**
     * The edge drawn under every road, and how much of it shows on each side.
     *
     * A dark grey with some transparency rather than black: the edge is there to hold a pale line
     * against a pale roof, and black holds too hard over a photograph. 1.5 style pixels each side is
     * enough to read at any zoom the imagery is used at, and thinner than LINZ's own casing, which is
     * drawing under a flat map rather than a photograph.
     */
    const val CASING_COLOUR = "#545450"
    const val CASING_OPACITY = 0.88f
    const val CASING_WIDTH = 3f

    /** A foot or vehicle track: a dark, thin, dashed line, and only from [TRACK.minZoom] in. */
    const val TRACK_COLOUR = "#3C3C38"
    const val TRACK_OPACITY = 0.62f

    /**
     * The letters a road's name is written in - the app's own, the same as a place name's.
     *
     * White with a dark outline because it is written along a gold line over a photograph, which is
     * the only place on this map where white has a colour under it that is always the same.
     */
    const val NAME_COLOUR = PlaceLabels.COLOUR
    const val NAME_HALO_COLOUR = PlaceLabels.HALO_COLOUR
    const val NAME_HALO_WIDTH = PlaceLabels.HALO_WIDTH

    /**
     * When a road's name appears, and how big it is.
     *
     * From 13, which is LINZ's own answer for these labels: below it the names are longer than the
     * roads that carry them, and a screen of half-written street names is worse than none.
     */
    const val NAME_MIN_ZOOM = 13f
    const val NAME_SIZE_AT_MIN_ZOOM = 11f
    const val NAME_SIZE_AT_MAX_ZOOM = 14f

    /**
     * How sharply a name may turn to follow its road.
     *
     * **Much sharper than a river's** ([PlaceLabels.LINE_MAX_ANGLE], which allows anything): a river's
     * name is long and bends with the water, but a road name is two or three words and a hairpin
     * wrapped in them reads as a scribble. Measured on the West Coast Road at Arthur's Pass, where
     * 180 put "WEST COAST ROAD" around a switchback with the first word upside down and the letters
     * crossing the line. At 45 the placement gives up on that bend and writes the name along the
     * straight beside it, which is what LINZ's own road labels do - they do not set the property at
     * all, so they get MapLibre's default of 45.
     */
    const val NAME_MAX_ANGLE = 45f

    /**
     * One line of roads: which features it draws, and how.
     *
     * The filter is a MapLibre expression held as JSON text, because both style builders emit JSON and
     * neither of them compiles expressions: the phone builds its document as a string, and the desk
     * builds it with kotlinx.serialization. One text serves both.
     */
    data class Line(
        /** The style layer's id, which must be unique across both maps. */
        val id: String,
        /** A MapLibre filter expression, as JSON. */
        val filter: String,
        /** The zoom this line first appears at. */
        val minZoom: Float,
        val colour: String,
        /** The width, in style pixels, at each zoom it is given for. */
        val width: List<Pair<Float, Float>>,
        /** Dash and gap, in multiples of the line's own width. A solid line has none. */
        val dash: List<Float>? = null,
        val opacity: Float = 1f,
        /** Whether [CASING_COLOUR] is drawn wider underneath this line. */
        val casing: Boolean = true
    )

    /**
     * A highway: wider, from further out, and with the casing that says "this one is different".
     *
     * From zoom 6 rather than 10 because a highway is the one road worth seeing on a whole-region
     * view, which is where a walker plans the drive to the start of the walk.
     */
    val HIGHWAY = Line(
        id = "roads-highway",
        filter = roadKind("motorway"),
        minZoom = 6f,
        colour = COLOUR,
        width = listOf(6f to 1.2f, 10f to 2.2f, 15f to 3.0f, 18f to 4.4f, 22f to 8.0f)
    )

    /**
     * A sealed or metalled road of any lane count.
     *
     * LINZ splits this into five widths by lane count and surface. Here a lane count is a fact about a
     * road in a town, and the walker reading a photograph wants the road's course rather than its
     * capacity, so they are one line.
     */
    val ROAD = Line(
        id = "roads-road",
        filter = roadKind(null),
        minZoom = 10f,
        colour = COLOUR,
        width = listOf(10f to 0.9f, 15f to 1.2f, 18f to 2.0f, 22f to 4.5f)
    )

    /**
     * An unmetalled road: the same line, paler and dashed.
     *
     * Drawn as well as the sealed line rather than instead of it - a metalled road and an unmetalled
     * one are both roads, and the difference is worth a dash rather than a whole separate layer that
     * would have to be kept in step.
     */
    val UNMETALLED = Line(
        id = "roads-unmetalled",
        filter = roadKind(null, """["==", ["get", "surface"], "unmetalled"]"""),
        minZoom = 10f,
        colour = UNMETALLED_COLOUR,
        width = listOf(10f to 0.9f, 15f to 1.2f, 18f to 2.0f, 22f to 4.5f),
        dash = listOf(4f, 3f)
    )

    /**
     * A foot or vehicle track, from zoom 14 in.
     *
     * The one line this feature adds for walkers rather than for drivers: a farm track is often the
     * way in, and it is invisible on a photograph. From 14 because a whole-valley view of every farm
     * track is a spiderweb, which is why LINZ draws them from 12 only on their own map.
     */
    val TRACK = Line(
        id = "roads-track",
        filter = """["all", ["==", ["get", "kind"], "track"], ["!=", ["get", "tunnel"], true]]""",
        minZoom = 14f,
        colour = TRACK_COLOUR,
        width = listOf(14f to 0.6f, 18f to 1.0f, 22f to 2.2f),
        dash = listOf(2.5f, 3.5f),
        opacity = TRACK_OPACITY,
        casing = false
    )

    /** Every line, in the order they are drawn: highways under roads, tracks over all of it. */
    val LINES = listOf(HIGHWAY, ROAD, UNMETALLED, TRACK)

    /**
     * Which features carry a road's name.
     *
     * The same test the lines use, asked again for the letters: a name on a feature with no lane
     * count would be written along a railway, an aerodrome or a racecourse, and a name on a tunnel
     * would be written along a line that is not drawn at all, because [roadKind] drops tunnels.
     */
    val NAME_FILTER: String =
        """["all", ["has", "$NAME_PROPERTY"], ["has", "lane_count"], """ +
            """["!=", ["get", "tunnel"], true]]"""

    /**
     * The filter for a road, with `kind` at [kind] where one is given.
     *
     * **A road is a feature with a lane count.** That is LINZ's own test, and it is what keeps the
     * railways, the aerodromes and the racecourses of the same layer out: none of them has lanes.
     * A track is excluded by name as well, because a farm track that did carry a lane count would
     * otherwise be drawn twice - once as a road, once as a track - by two layers that agree about
     * nothing else.
     *
     * A tunnel is dropped rather than drawn dashed, because a straight line through a mountain on a
     * photograph reads as a road that goes over it - and Otira, which is how this was found, is filed
     * as an ordinary road with `tunnel` set.
     *
     * [also] is for the one line that needs a question of its own on top of these - an unmetalled
     * road. Appended to the same `all` rather than wrapped around it, so a filter never holds another
     * filter.
     */
    internal fun roadKind(kind: String?, vararg also: String): String {
        val parts = mutableListOf("\"all\"", """["has", "lane_count"]""", """["!=", ["get", "tunnel"], true]""")
        if (kind != null) {
            parts += """["==", ["get", "kind"], "$kind"]"""
        } else {
            parts += """["!=", ["get", "kind"], "motorway"]"""
            parts += """["!=", ["get", "kind"], "track"]"""
        }
        parts += """["!=", ["get", "status"], "under construction"]"""
        parts += also
        return parts.joinToString(", ", "[", "]")
    }
}
