package nz.mckenzie.trailday.domain.look

import nz.mckenzie.trailday.domain.asset.AssetKind

/**
 * The whole look: **a colour and a size for each of the six rows**, and nothing else.
 *
 * A `MapLook` is a value, not a service. Everything that draws asks one of these for a colour or a
 * width, which is what keeps the map, the list's glyphs, a place's own page and the desk from
 * disagreeing about what a hut looks like - they all read the same answer out of the same object.
 *
 * [DEFAULT] is what the app drew before there was a choice, down to the colour and the width, so an
 * install that has never opened the screen looks exactly as it always did. That is worth pinning in a
 * test rather than trusting: the default is the one look nobody chose.
 *
 * **Stored as the differences, not the whole thing.** A look that is the default writes nothing, and a
 * look with one row changed writes one row. That way a build that adds a row, or renames a colour,
 * leaves every other row alone: what it cannot read it skips, and that row falls back to its own
 * default rather than the whole look being lost. A look is a small comfort, and losing it to a typo is
 * the sort of thing that makes a walker stop choosing.
 */
@ConsistentCopyVisibility
data class MapLook private constructor(
    private val colours: Map<LookTarget, LookColour>,
    private val widths: Map<LookTarget, LineWidthChoice>,
    private val sizes: Map<LookTarget, MarkerSizeChoice>
) {

    /** The colour [target] is drawn in. */
    fun colourOf(target: LookTarget): LookColour = colours.getValue(target)

    /** The colour a [kind] of place or line is drawn in - its row's. */
    fun colourOf(kind: AssetKind): LookColour = colourOf(LookTarget.of(kind))

    /** [target]'s colour at a light tone, for a glyph on a dark card. */
    fun colourOnDark(target: LookTarget): String = colourOf(target).onDark

    /** A [kind]'s colour at a light tone. */
    fun colourOnDark(kind: AssetKind): String = colourOnDark(LookTarget.of(kind))

    /**
     * The colour a place is drawn in: **the recorded green when a walk has been made of it**, its row's
     * colour otherwise.
     *
     * A place with a recorded pass is evidence of a walk, whatever the walker has chosen for its kind, and
     * the green is not on the shelf ([LookColour]) for exactly this reason. The map, the place's own page
     * and the recorder all ask this, so they cannot disagree about it.
     */
    fun colourOfPlace(kind: AssetKind, recorded: Boolean): String =
        if (recorded) RECORDED else colourOf(kind).hex

    /** How wide [target]'s line is drawn. Read for a place row only by mistake - use [sizeOf]. */
    fun widthOf(target: LookTarget): LineWidthChoice = widths.getValue(target)

    /** How wide a kind's line is drawn. */
    fun widthOf(kind: AssetKind): LineWidthChoice = widthOf(LookTarget.of(kind))

    /** How big a place's marker is drawn. */
    fun sizeOf(target: LookTarget): MarkerSizeChoice = sizes.getValue(target)

    /** How big a kind's marker is drawn. */
    fun sizeOf(kind: AssetKind): MarkerSizeChoice = sizeOf(LookTarget.of(kind))

    fun withColour(target: LookTarget, colour: LookColour): MapLook =
        copy(colours = colours + (target to colour))

    fun withWidth(target: LookTarget, width: LineWidthChoice): MapLook =
        copy(widths = widths + (target to width))

    fun withSize(target: LookTarget, size: MarkerSizeChoice): MapLook =
        copy(sizes = sizes + (target to size))

    /** Whether this is the look the app draws without being asked. */
    val isDefault: Boolean get() = this == DEFAULT

    /**
     * This look as one preference string, holding **only the rows that differ from [DEFAULT]**.
     *
     * `tracks=blue:thick;huts=crimson:normal` - the colour first, then the width or the size,
     * whichever the row carries. The default look is the empty string.
     */
    fun toStorage(): String = LookTarget.entries.mapNotNull { target ->
        val colour = colourOf(target)
        val changed = colour != DEFAULT.colourOf(target) ||
            (if (target.control == LookControl.LINE) {
                widthOf(target) != DEFAULT.widthOf(target)
            } else {
                sizeOf(target) != DEFAULT.sizeOf(target)
            })
        if (!changed) return@mapNotNull null
        val measure = if (target.control == LookControl.LINE) {
            widthOf(target).id
        } else {
            sizeOf(target).id
        }
        "${target.id}=${colour.id}:$measure"
    }.joinToString(STORAGE_SEPARATOR)

    companion object {

        /**
         * The recorded green, fixed on purpose: the same value as
         * [nz.mckenzie.trailday.map.AssetColors.RECORDED], and a test holds the two together so neither
         * can be changed without the other.
         *
         * It is here rather than on the shelf because it is nobody's kind. A green line on this map is a
         * line that has been walked, and a walker who could paint a track green could no longer tell
         * their own work from the evidence of a walk.
         */
        const val RECORDED = "#2E7D32"

        /**
         * What the app draws before anybody chooses: the five kinds' own colours, the teal the
         * councils' and trusts' lines have always worn, and the width and size it has always used.
         */
        val DEFAULT: MapLook = build(
            colour = mapOf(
                LookTarget.TRACKS to LookColour.BLUE,
                LookTarget.BIKE_TRACKS to LookColour.PURPLE,
                LookTarget.OTHER_TRACKS to LookColour.TEAL,
                LookTarget.HUTS to LookColour.ORANGE,
                LookTarget.CAMPSITES to LookColour.TEAL,
                LookTarget.WAYPOINTS to LookColour.SLATE
            )
        )

        /**
         * The look [stored] names, or [DEFAULT] when there is none.
         *
         * Read row by row and field by field: a row this build does not know is skipped, and a colour
         * or a width it does not know falls back to that row's own default. Anything unrecognisable
         * leaves the app drawing rather than stopping, because a look is never important enough to
         * stop for.
         */
        fun fromStorage(stored: String?): MapLook {
            if (stored.isNullOrBlank()) return DEFAULT
            var look = DEFAULT
            stored.split(STORAGE_SEPARATOR).forEach { entry ->
                val target = LookTarget.fromStorage(entry.substringBefore('=').trim()) ?: return@forEach
                val parts = entry.substringAfter('=', "").trim().split(":").map { it.trim() }
                val colour = parts.getOrNull(0)?.let(LookColour::fromStorage)
                if (colour != null) look = look.withColour(target, colour)
                val measure = parts.getOrNull(1).orEmpty()
                if (target.control == LookControl.LINE) {
                    LineWidthChoice.fromStorage(measure)?.let { look = look.withWidth(target, it) }
                } else {
                    MarkerSizeChoice.fromStorage(measure)?.let { look = look.withSize(target, it) }
                }
            }
            return look
        }

        /** A look with one colour per row, every row at the default width and size. */
        private fun build(colour: Map<LookTarget, LookColour>): MapLook = MapLook(
            colours = LookTarget.entries.associateWith { colour.getValue(it) },
            widths = LookTarget.entries.associateWith { LineWidthChoice.NORMAL },
            sizes = LookTarget.entries.associateWith { MarkerSizeChoice.NORMAL }
        )

        /** Between one row and the next in the stored string. */
        private const val STORAGE_SEPARATOR = ";"
    }
}
