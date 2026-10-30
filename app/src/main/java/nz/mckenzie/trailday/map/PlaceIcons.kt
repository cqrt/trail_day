package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.look.Look

/**
 * The picture the map draws a place with: the kind's own glyph, in the kind's own colour.
 *
 * A place - a hut, a campsite, a spot of the walker's own - is drawn as the glyph the kind's own row
 * carries: a hut as a roof over walls, a campsite as a tent, a waypoint as a dot in a ring. The list
 * and the map agree about what the walker is looking at.
 *
 * **The colour and the size are the kind's, as the walker has set them.** Every place is drawn in the
 * colour of its kind's row, so what the map says is what the place *is* - and the shape says it too.
 * See [nz.mckenzie.trailday.domain.look.Look].
 *
 * **Every marker wears a thin white edge** - [MARKER_OUTLINE_DP] of it, round every shape. A marker
 * has to lift off whatever is behind it and imagery is as often dark as not, but white enough to be
 * seen is not the same as white enough to take over.
 *
 * One image per kind rather than one picture tinted per feature: MapLibre can only recolour a picture
 * that has been turned into a signed distance field, and a marker drawn as a plain picture keeps a
 * crisp edge at the size it is actually drawn. Which picture a place wears is decided here, in plain
 * Kotlin, so it is covered by a JVM test rather than by looking at a map.
 */
object PlaceIcons {

    /**
     * The kinds that are a single spot, in the order the picker offers them.
     *
     * Read off [AssetKind.shape] rather than listed again: the kind decides the shape, so this cannot
     * disagree with the picker about which kinds are places.
     */
    val KINDS: List<AssetKind> = AssetKind.entries.filter { it.shape == AssetShape.POINT }

    /**
     * How big a place is drawn, in density-independent pixels: the kind's own size, as chosen.
     *
     * Bigger than the dot it replaced, which was 16 of them across: these glyphs have legs, posts and
     * a ring to read, and at the size of a dot all of it is a smudge. The default is the 22 the app
     * has always drawn.
     */
    fun sizeDp(kind: AssetKind): Float = Look.value.sizeOf(kind).dp

    /**
     * The white edge drawn around a marker, in density-independent pixels.
     *
     * One dp is the least white that separates the shape from the ground and still leaves the shape
     * its own colour.
     */
    const val MARKER_OUTLINE_DP = 1f

    /** The colour of that edge. Its own constant because it is the same white everywhere. */
    const val MARKER_OUTLINE = "#FFFFFF"

    /** The colour a kind is drawn on the map in - its row's, as chosen, and always the light tone. */
    fun colorOf(kind: AssetKind): String = Look.colourOf(kind).hex

    /** Every image a place can ask for: one per kind. */
    val IMAGE_NAMES: List<String> = KINDS.map { nameOf(it) }

    /** The grey ring: what a kind nothing was drawn for falls back to. */
    val FALLBACK_IMAGE_NAME: String = nameOf(AssetKind.WAYPOINT)

    /**
     * The image for a place of [kind].
     *
     * A kind that is not a place at all gets the grey ring, because a line has no marker to draw.
     */
    fun imageName(kind: AssetKind): String =
        if (kind in KINDS) nameOf(kind) else FALLBACK_IMAGE_NAME

    /**
     * The kind an image name was made from, or null when it names no picture.
     *
     * Searched rather than un-picked from the name: [imageName] is the rule, and a second rule for
     * reading a name back would be a second place for the two to disagree. The desk asks for a picture
     * by the name it was given, and the phone has to know which kind that name means.
     */
    fun ofImageName(name: String): AssetKind? = KINDS.firstOrNull { imageName(it) == name }

    /**
     * The image name for a kind, whether or not one was drawn for it.
     *
     * Named from the kind itself so the two cannot drift apart: a picture drawn for a kind is the
     * picture that kind asks for, with nothing in between to get wrong.
     */
    private fun nameOf(kind: AssetKind): String =
        IMAGE_PREFIX + kind.name.lowercase().replace('_', '-')

    private const val IMAGE_PREFIX = "trailday-place-"
}
