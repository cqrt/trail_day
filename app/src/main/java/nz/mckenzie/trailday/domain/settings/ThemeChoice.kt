package nz.mckenzie.trailday.domain.settings

/**
 * How the app is lit.
 *
 * Three answers rather than a switch, because "dark" and "the phone's own setting" are different
 * requests: a phone set to go dark at night should take the app with it, while a phone held up in
 * the sun is best read light whatever the phone thinks the time of day is. A switch cannot say
 * both, and the walker who wants one of them would be handed the other.
 *
 * Following the phone is the default, which is also what every install has always done: a
 * preference added later must not change how an untouched install looks. See
 * [nz.mckenzie.trailday.data.SettingsRepository.themeChoice] for why it is a setting about this
 * phone's screen and so is not carried in a backup.
 */
enum class ThemeChoice(
    /** Stable id: it names the choice in preferences. Never renamed. */
    val id: String,
    /** What the picker calls it. */
    val displayName: String,
    /** One line for the picker, in the walker's terms. */
    val summary: String
) {
    SYSTEM(
        id = "system",
        displayName = "System",
        summary = "Follow this phone."
    ),

    LIGHT(
        id = "light",
        displayName = "Light",
        summary = "Always light."
    ),

    DARK(
        id = "dark",
        displayName = "Dark",
        summary = "Always dark."
    );

    /**
     * Whether this means a dark screen, given what the phone is set to.
     *
     * The whole of what the three answers disagree about, and it is here rather than in the theme
     * so it can be tested without a composition.
     */
    fun isDark(phoneInDark: Boolean): Boolean = when (this) {
        SYSTEM -> phoneInDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** What an install that has never chosen a theme is lit by. */
        val DEFAULT: ThemeChoice = SYSTEM

        /**
         * The choice a stored id names, or the default.
         *
         * Anything unrecognised reads as the default rather than throwing: a preference written by
         * a later build, or a value edited by hand, must not stop the app drawing.
         */
        fun fromStorage(value: String?): ThemeChoice =
            entries.firstOrNull { it.id == value } ?: DEFAULT
    }
}
