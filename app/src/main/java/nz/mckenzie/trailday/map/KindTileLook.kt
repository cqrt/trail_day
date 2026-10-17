package nz.mckenzie.trailday.map

/**
 * How a kind's tile looks: **the numbers the phone's list and the desk's rows both draw it with.**
 *
 * A tile is the kind's glyph on a rounded square in a pale tint of the kind's own colour. The phone draws it
 * with Compose (`KindTile`), and the desk is handed the glyph itself by the phone's own server, in a row it
 * rounds and tints to these proportions - so the corner and the share of the tile the glyph takes up live here
 * rather than in the drawing code, and the two surfaces cannot come to wear tiles of a different shape.
 */
object KindTileLook {

    /** How rounded the tile is, in density-independent pixels on a tile the size of a list row's. */
    const val CORNER_DP = 14f

    /** How much of the tile's side the glyph takes up. */
    const val GLYPH_FRACTION = 0.52f

    /** The tint's strength over the page, in each theme. */
    const val TINT_ALPHA_LIGHT = 0.14f
    const val TINT_ALPHA_DARK = 0.22f
}
