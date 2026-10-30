package nz.mckenzie.trailday.domain.look

/**
 * How a colour looks on a dark screen: **the same hue, lifted towards white until it reads.**
 *
 * The app follows the phone's theme, and a colour that reads on a white card disappears on a black
 * one - which is exactly what a deep purple and a brown did before the kinds were paired. The five
 * pairs the app already has were each picked by hand; a colour the walker chooses cannot be, because
 * the shelf is chosen from rather than added to, so the light tone is worked out instead.
 *
 * **Nothing is taken away, only white is added.** Mixing towards white keeps the hue a hut's colour
 * is, and pulls a deep saturated colour back towards a tint of itself - which is what the hand-picked
 * pairs already are. Checked against the five the app ships, this lands the teal (`#00897B` to
 * `#59B2A9`, where the picked one is `#4DB6AC`) and the slate within a shade or two of what was chosen
 * by eye, and takes the blue and the orange somewhere a little less bright than the hand-picked
 * `#64B5F6` and `#FFB74D`. A rule bent to match all five would stop being a rule; the difference is
 * measured on a phone and written in the plan log instead.
 *
 * **As far as it needs to go, and no further.** Lifting every colour to a fixed lightness makes a
 * mid-tone gaudy and a pale one dull; lifting until it reaches the contrast the app's own five dark
 * tones already read at leaves a colour that was nearly light enough almost untouched. Measured
 * against the app's dark page, those five read between 6.3 and 9.4 to one; [TARGET_CONTRAST] is the
 * low end of that, so no derived tone is dimmer than the dimmest the app ships today.
 *
 * Plain arithmetic on a `#RRGGBB` string rather than Compose or Android's colour classes, so the
 * derivation is covered by a JVM test rather than by looking at two phones.
 */
object LightTone {

    /**
     * The page a dark phone draws behind a card: the app's own dark scheme's background.
     *
     * The same value `AssetColorsTest` measures the hand-picked tones against, so a derived tone and a
     * picked one are judged on the same ground.
     */
    const val DARK_PAGE = "#1D211A"

    /** The least contrast a dark-screen tone is given, as a ratio against [DARK_PAGE]. */
    const val TARGET_CONTRAST = 6.5

    /** One shade of white, which is the smallest step worth mixing in. */
    private const val STEP = 1.0 / 255.0

    /**
     * [hex] light enough to read on [DARK_PAGE], or [hex] itself when it is not a colour this can read.
     *
     * A colour that already reads is returned unchanged rather than nudged: the tone is a floor, not an
     * instruction to wash a colour out.
     */
    fun of(hex: String): String {
        val channels = channels(hex) ?: return hex
        val page = channels(DARK_PAGE)?.let(::luminanceOf) ?: return hex
        if (contrast(luminanceOf(channels), page) >= TARGET_CONTRAST) return hex
        var mix = 0.0
        while (true) {
            // Measured on the rounded value that is actually handed back, not on the arithmetic in
            // between: a tone half a step short of the target is a tone that fails its own test.
            val lifted = toHex(channels.map { channel -> channel + (255.0 - channel) * mix })
            if (contrast(luminance(lifted)!!, page) >= TARGET_CONTRAST) return lifted
            if (mix >= 1.0) return "#FFFFFF"
            mix = (mix + STEP).coerceAtMost(1.0)
        }
    }

    /** The relative luminance of [hex], or null when it is not a colour this can read. */
    fun luminance(hex: String): Double? = channels(hex)?.let(::luminanceOf)

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
