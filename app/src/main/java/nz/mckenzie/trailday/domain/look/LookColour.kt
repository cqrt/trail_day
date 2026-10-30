package nz.mckenzie.trailday.domain.look

/**
 * The colours a walker can put on the map: **one shelf, and every row picks from it.**
 *
 * A shelf per kind was the other option, and it was turned down for a simple reason: a walker who
 * wants their tracks the same colour as their campsites has no way to say so, and a walker who wants
 * them different is already served by one shelf. What the shelf costs is that a hut can be made blue
 * and a track orange, which is the walker's business rather than a mistake to prevent.
 *
 * **The five colours the app already uses are on it**, so today's look can always be picked again,
 * and so is the trail's rust, because the trail has a row now and its own colour has to be one the row
 * can show as chosen.
 *
 * **No green.** A green line on this map is a line that has been walked - the recorded green, and its
 * gold edge with it - and a walker who could paint a track green could no longer tell their own work
 * from the evidence of a walk. That is the one colour the shelf leaves out.
 *
 * **The lemon and the sky are the pale end of it.** They were asked for as the two the shelf was
 * missing: nothing bright, and no hue between the blue and the teal. Both read as lines over imagery
 * and both nearly vanish as a glyph on a white card, which is what [onLight] is for.
 *
 * [onDark] and [onLight] are the same hue at the tone each card needs, derived rather than picked
 * twice, so a colour added to this shelf cannot arrive without a readable version on either theme. See
 * [CardTone].
 */
enum class LookColour(
    /** Stable id: it names the colour in preferences. Never rename one. */
    val id: String,
    /** The colour itself, as it is drawn on the map. */
    val hex: String,
    /** What the swatch is labelled with, and what a screen reader reads. */
    val displayName: String
) {
    BLUE("blue", "#1565C0", "Blue"),
    PURPLE("purple", "#6A1B9A", "Purple"),
    ORANGE("orange", "#EF6C00", "Orange"),
    TEAL("teal", "#00897B", "Teal"),
    SLATE("slate", "#546E7A", "Slate"),
    CRIMSON("crimson", "#B3261E", "Crimson"),
    MAGENTA("magenta", "#AD1457", "Magenta"),
    CHARCOAL("charcoal", "#212121", "Charcoal"),

    /** Bright, and the only yellow: the shelf had none. */
    LEMON("lemon", "#FBC02D", "Lemon"),

    /** The hue the shelf was missing between the blue and the teal. */
    SKY("sky", "#4FC3F7", "Sky"),

    /** Te Araroa's own, and the one strong colour on the map that was nobody's to change until it was. */
    RUST("rust", "#C1440E", "Rust");

    /** The same colour, light enough to read as a glyph on a dark card. */
    val onDark: String get() = CardTone.lifted(hex)

    /**
     * The same colour, dark enough to read as a glyph on a light card.
     *
     * Only the pale ones move: a crimson is a crimson on both, and a lemon deepened towards black is a
     * mustard that a 24 dp line can be seen in. The swatch keeps the map's own hex either way.
     */
    val onLight: String get() = CardTone.deepened(hex)

    companion object {

        /** The colour a stored id names, or null when this build does not know it. */
        fun fromStorage(id: String): LookColour? = entries.firstOrNull { it.id == id }
    }
}
