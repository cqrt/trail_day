package nz.mckenzie.trailday.map

/**
 * How heavy a line of the work is drawn: one number, and every other line worked out from it.
 *
 * A track is the measure, because a track is what the map is mostly made of - your own, DOC's and any other
 * publisher's. Everything else is a ratio of it: the white edge under a reference line and the gold one
 * under a recorded line, Te Araroa, and the line being followed. Five numbers set by hand is how the trail
 * comes to be nearly as heavy as a track after the tracks are thinned, which is a change nobody chose.
 *
 * **The base is the walker's now**, chosen from three ([nz.mckenzie.trailday.domain.look.LineWidthChoice]),
 * and every other width follows it - so a thicker track is a thicker casing round a published one, and a
 * thicker line being followed. Only the dash does not follow: see [dashArray].
 *
 * **Three is the default, against the five it was.** Five was chosen when the map drew a handful of your
 * own places; over a valley of DOC's tracks it is heavy enough that two nearby walkways read as one line.
 * Four was barely a change on the emulator. Two and a half lost your own lines - the ones with no edge to
 * lift them - against dark bush, which is most of the ground this app is used on. Three still reads over
 * bush and leaves a gap between two lines that are close. Measured on the emulator's 420 dpi screen, where
 * a style pixel is 2.6 screen pixels: **3 is 8 px of glass**, where it was 13.
 *
 * A style pixel rather than a distance on the ground, which is what a map viewer means by a line's width:
 * zooming in makes the line cover less ground and be no less visible, and that is the behaviour wanted here.
 */
@JvmInline
value class LineWeight(val stylePx: Float) {

    /**
     * How wide the edge under this line is drawn.
     *
     * The edge is drawn *under* the line, so only the difference either side shows: 1.2 style pixels of
     * white round each track, where it was 2. It is white or gold for one reason - a line over a photograph
     * has to say where it is before it says what colour it is - and that reason does not want an edge as
     * wide as a line, which is what holding the extra 4 would have given a 3-wide line.
     */
    val edge: Float get() = stylePx * EDGE_RATIO

    /**
     * Te Araroa: narrower than a track, so the trail is the ground the work sits on rather than a rival.
     *
     * Its edge is a touch narrower than the reference edge above, as it always has been - 1.4 against 1.8 -
     * because the trail is a long line seen from far off rather than a route you read up close.
     *
     * **The trail has a width row of its own now, and these two ratios are what its three chips are
     * drawn with.** Normal is the 0.8 of a track it has been since the trail was first drawn, so the
     * default look is unchanged; a thick trail is then allowed to be heavier than a thin track, which is
     * the walker's answer rather than the app's ratio.
     */
    val trail: Float get() = stylePx * TRAIL_RATIO
    val trailEdge: Float get() = stylePx * TRAIL_EDGE_RATIO

    /**
     * The line being followed, while something is: nearly twice a track, so it is the thing in view.
     *
     * It is drawn in its own kind's colour and made bold by its width alone, so a followed bike track
     * still looks like a bike track.
     */
    val followed: Float get() = stylePx * FOLLOWED_RATIO

    /**
     * A bike track's dash and gap as a dash array, for the line it is drawn on.
     *
     * A dash array is in multiples of its own line's width, which is the map engine's unit and not
     * something either map can change - so the numbers are worked out from this width to keep the dash
     * the length it is on the ground. A line that thinned while its dash shrank with it would be a
     * stipple rather than a dash, and the dash is the whole of what a bike track says with its shape.
     *
     * Both maps call this, so neither can be left with the other's numbers.
     */
    val dashArray: Array<Float> get() = arrayOf(DASH_PX / stylePx, GAP_PX / stylePx)

    companion object {

        /** A bike track's dash and gap, in style pixels, as they are drawn. */
        const val DASH_PX = 10f
        const val GAP_PX = 8f

        /** The widths everything else is worked out from. */
        private const val EDGE_RATIO = 1.8f
        private const val TRAIL_RATIO = 0.8f
        private const val TRAIL_EDGE_RATIO = 1.4f
        private const val FOLLOWED_RATIO = 1.8f
    }
}
