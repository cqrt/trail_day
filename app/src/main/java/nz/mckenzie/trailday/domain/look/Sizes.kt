package nz.mckenzie.trailday.domain.look

/**
 * How heavy a line is drawn, in three steps, and what the walker is offered.
 *
 * These are style pixels - the map engine's own unit, which is not a pixel of glass on any particular
 * phone - because a line's width is a width on the screen rather than a distance on the ground.
 * [NORMAL] is the three the app drew every line at before there was a choice, so an untouched install
 * looks exactly as it did.
 *
 * The numbers are the ones the app measured its way to: five was heavy enough that two nearby DOC
 * walkways read as one line over a valley, and two and a half lost the walker's own lines - the ones
 * with no white edge under them - against dark bush. See [nz.mckenzie.trailday.map.LineWeight] for the
 * argument, which is unchanged; what is new is that the walker can now answer it for themselves.
 *
 * **A line target's width is not a marker's size**, which is why the two choices below are separate
 * types rather than one with a flag. A hut drawn three style pixels across would be a speck.
 */
enum class LineWidthChoice(
    /** Stable id: it names the width in preferences. Never rename one. */
    val id: String,
    /** The width in style pixels. */
    val stylePx: Float,
    /** What the chip says. */
    val displayName: String
) {
    THIN("thin", 2f, "Thin"),
    NORMAL("normal", 3f, "Normal"),
    THICK("thick", 4.5f, "Thick");

    companion object {

        /** The width a stored id names, or null when this build does not know it. */
        fun fromStorage(id: String): LineWidthChoice? = entries.firstOrNull { it.id == id }
    }
}

/**
 * How big a place's marker is drawn, in density-independent pixels.
 *
 * A marker is a picture handed to the map at the device's own density, so its size is in dp and does
 * not follow the map engine's style pixel. [NORMAL] is the 22 the app has always drawn, which is what
 * an untouched install keeps.
 */
enum class MarkerSizeChoice(
    /** Stable id: it names the size in preferences. Never rename one. */
    val id: String,
    /** The marker's side, in density-independent pixels. */
    val dp: Float,
    /** What the chip says. */
    val displayName: String
) {
    SMALL("small", 18f, "Small"),
    NORMAL("normal", 22f, "Normal"),
    LARGE("large", 27f, "Large");

    companion object {

        /** The size a stored id names, or null when this build does not know it. */
        fun fromStorage(id: String): MarkerSizeChoice? = entries.firstOrNull { it.id == id }
    }
}
