package nz.mckenzie.trailday.domain.look

/**
 * The colours a walker can put on the map: **one shelf, and every kind picks from it.**
 *
 * A shelf per kind was the other option, and it was turned down for a simple reason: a walker who
 * wants their tracks the same colour as their campsites has no way to say so, and a walker who wants
 * them different is already served by one shelf. What the shelf costs is that a hut can be made blue
 * and a track orange, which is the walker's business rather than a mistake to prevent.
 *
 * **The five colours the app already uses are on it**, so today's look can always be picked again.
 *
 * **No green.** A green line on this map is a line that has been walked - the recorded green, and its
 * gold edge with it - and a walker who could paint a track green could no longer tell their own work
 * from the evidence of a walk. That is the one colour the shelf leaves out.
 *
 * [onDark] is the same hue at a light tone, derived rather than picked twice, so a colour added to
 * this shelf cannot arrive without a readable dark-screen version. See [LightTone].
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
    CHARCOAL("charcoal", "#212121", "Charcoal");

    /** The same colour with [LightTone]'s lightness, for a glyph on a dark card. */
    val onDark: String get() = LightTone.of(hex)

    companion object {

        /** The colour a stored id names, or null when this build does not know it. */
        fun fromStorage(id: String): LookColour? = entries.firstOrNull { it.id == id }
    }
}
