package nz.mckenzie.trailday.map

/**
 * How heavy a line of the work is drawn: one number, and every other line worked out from it.
 *
 * A track is the measure, because a track is what the map is mostly made of - your own, DOC's and any other
 * publisher's. Everything else is a ratio of it: the white edge under a reference line and the gold one
 * under a recorded line, Te Araroa, and the line being followed. Five numbers set by hand is how the trail
 * comes to be nearly as heavy as a track after the tracks are thinned, which is a change nobody chose.
 *
 * **3 against the five it was.** Five was chosen when the map drew a handful of your own places; over a
 * valley of DOC's tracks it is heavy enough that two nearby walkways read as one line. Four was barely a
 * change on the emulator. Two and a half lost your own lines - the ones with no edge to lift them - against
 * dark bush, which is most of the ground this app is used on. Three still reads over bush and leaves a gap
 * between two lines that are close. Measured on the emulator's 420 dpi screen, where a style pixel is 2.6
 * screen pixels: **3 is 8 px of glass**, where it was 13.
 *
 * A style pixel rather than a distance on the ground, which is what a map viewer means by a line's width:
 * zooming in makes the line cover less ground and be no less visible, and that is the behaviour wanted here.
 */
object LineWeight {

    /** A track, and every line drawn the same weight as one. */
    const val TRACK = 3f

    /**
     * How much wider than its line an edge is drawn, as a multiple.
     *
     * The edge is drawn *under* the line, so only the difference either side shows: 1.2 style pixels of
     * white round each track, where it was 2. It is white or gold for one reason - a line over a photograph
     * has to say where it is before it says what colour it is - and that reason does not want an edge as
     * wide as a line, which is what holding the extra 4 would have given a 3-wide line.
     */
    const val EDGE = TRACK * 1.8f

    /**
     * Te Araroa: narrower than a track, so the trail is the ground the work sits on rather than a rival.
     *
     * Its edge is a touch narrower than the reference edge above, as it always has been - 1.4 against 1.8 -
     * because the trail is a long line seen from far off rather than a route you read up close.
     */
    const val TRAIL = TRACK * 0.8f
    const val TRAIL_EDGE = TRACK * 1.4f

    /**
     * The line being followed, while something is: nearly twice a track, so it is the thing in view.
     *
     * It is drawn in its own kind's colour and made bold by its width alone, so a followed bike track
     * still looks like a bike track.
     */
    const val FOLLOWED = TRACK * 1.8f

    /**
     * A bike track's dash and gap, in style pixels, as they are drawn.
     *
     * Held at the length they were at a five-wide line rather than scaled down with it. A dash is a shape
     * on the ground rather than a share of the line's weight: shortening it with the line would give a thin
     * line a stipple where it had a dash, and the dash is what says "this one is ridden".
     */
    const val DASH_PX = 10f
    const val GAP_PX = 8f

    /**
     * The same dash as a dash array, for the line it is drawn on.
     *
     * A dash array is in multiples of its own line's width, which is the map engine's unit and not
     * something either map can change - so the numbers have to be raised as the line thins for the dash to
     * stay the length it is. Both maps call this, so neither can be left with the other's numbers.
     */
    fun dashArray(width: Float = TRACK): Array<Float> = arrayOf(DASH_PX / width, GAP_PX / width)
}
