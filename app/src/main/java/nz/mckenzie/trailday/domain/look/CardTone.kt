package nz.mckenzie.trailday.domain.look

/**
 * How a colour looks on a card: **lifted towards white on a dark screen, and deepened towards black on
 * a light one.**
 *
 * The app follows the phone's theme, and a colour that reads on a white card disappears on a black one
 * - which is exactly what a deep purple and a brown did before the kinds were paired. The five pairs
 * the app already has were each picked by hand; a colour the walker chooses cannot be, because the
 * shelf is chosen from rather than added to, so a card's tone is worked out instead.
 *
 * **Two directions, one rule.** On a dark card a colour is mixed towards white until it reaches the
 * contrast the app's own five dark tones already read at ([DARK_TARGET_CONTRAST]); on a light card it is
 * mixed towards black until it reaches [LIGHT_TARGET_CONTRAST], which is what a glyph drawn in it needs
 * to be seen on white. Nothing but white or black is mixed in either direction, so the hue survives, and
 * a colour that already reads is handed back unchanged: a tone is a floor, not an instruction to wash a
 * colour out.
 *
 * **The map keeps the colour itself.** A line over imagery is drawn in the shelf's own hex whatever the
 * theme, because imagery is light or dark by what it is a picture of, and the white casings are what
 * make a line readable there. A tone is for a glyph drawn *on* a card.
 *
 * Plain arithmetic on a `#RRGGBB` string rather than Compose or Android's colour classes, so the
 * derivation is covered by a JVM test rather than by looking at two phones.
 */
object CardTone {

    /**
     * The page a dark phone draws behind a card: the app's own dark scheme's background.
     *
     * The same value `AssetColorsTest` measures the hand-picked tones against, so a derived tone and a
     * picked one are judged on the same ground.
     */
    const val DARK_PAGE = "#1D211A"

    /** The card a light phone draws behind a glyph: the app's own light scheme puts the work on white. */
    const val LIGHT_PAGE = "#FFFFFF"

    /** The least contrast a dark-screen tone is given, as a ratio against [DARK_PAGE]. */
    const val DARK_TARGET_CONTRAST = 6.5

    /**
     * The least contrast a light-screen tone is given, as a ratio against [LIGHT_PAGE].
     *
     * Lower than the dark one because the two are not the same question. A dark page is unforgiving
     * about a colour that is too deep, while white is unforgiving about one that is too pale - and a
     * glyph thin enough to read against a black page is still a shape on white at three to one.
     */
    const val LIGHT_TARGET_CONTRAST = 3.0

    /** One shade, which is the smallest step worth mixing in. */
    private const val STEP = 1.0 / 255.0

    /** White and black, as channels. The two ends every tone is mixed towards. */
    private const val WHITE = 255
    private const val BLACK = 0

    /**
     * [hex] light enough to read on [DARK_PAGE], or [hex] itself when it already reads or is not a
     * colour this can read.
     */
    fun lifted(hex: String): String = towards(hex, DARK_PAGE, DARK_TARGET_CONTRAST, WHITE)

    /**
     * [hex] dark enough to read on [LIGHT_PAGE], or [hex] itself when it already reads or is not a
     * colour this can read.
     */
    fun deepened(hex: String): String = towards(hex, LIGHT_PAGE, LIGHT_TARGET_CONTRAST, BLACK)

    /** The relative luminance of [hex], or null when it is not a colour this can read. */
    fun luminance(hex: String): Double? = channels(hex)?.let(::luminanceOf)

    /**
     * [hex] mixed towards [edge] (white or black) until it reaches [target] against [page].
     *
     * Measured on the rounded value that is actually handed back, not on the arithmetic in between: a
     * tone half a step short of the target is a tone that fails its own test.
     */
    private fun towards(hex: String, page: String, target: Double, edge: Int): String {
        val channels = channels(hex) ?: return hex
        val pageLuminance = channels(page)?.let(::luminanceOf) ?: return hex
        if (contrast(luminanceOf(channels), pageLuminance) >= target) return hex
        var mix = 0.0
        while (true) {
            val mixed = toHex(channels.map { channel -> channel + (edge - channel) * mix })
            if (contrast(luminance(mixed)!!, pageLuminance) >= target) return mixed
            if (mix >= 1.0) return toHex(List(3) { edge.toDouble() })
            mix = (mix + STEP).coerceAtMost(1.0)
        }
    }

    /** WCAG's relative luminance, from channels on a 0-to-255 scale. */
    private fun luminanceOf(channels: List<Double>): Double {
        val linear = channels.map { channel ->
            val share = channel / 255.0
            if (share <= 0.03928) share / 12.92 else Math.pow((share + 0.055) / 1.055, 2.4)
        }
        return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722
    }

    /** WCAG's contrast ratio: 1.0 is the same colour, 21.0 is black on white. */
    private fun contrast(first: Double, second: Double): Double =
        (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)

    /** Three channels, 0 to 255, or null when [hex] is not `#RRGGBB`. */
    private fun channels(hex: String): List<Double>? {
        val digits = hex.removePrefix("#")
        if (digits.length != 6) return null
        return runCatching {
            (0 until 3).map { index -> digits.substring(index * 2, index * 2 + 2).toInt(16).toDouble() }
        }.getOrNull()
    }

    private fun toHex(channels: List<Double>): String = "#" + channels.joinToString("") { channel ->
        Math.round(channel).toInt().coerceIn(0, 255).toString(16).uppercase().padStart(2, '0')
    }
}
