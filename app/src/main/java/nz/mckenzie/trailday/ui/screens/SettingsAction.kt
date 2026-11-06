package nz.mckenzie.trailday.ui.screens

import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

/**
 * The door to Settings, in the corner of a title bar.
 *
 * One composable rather than an icon and a word written into each screen, because there are five
 * tabs now and a tab whose door was called something else would be a second app. The map has no
 * title bar and draws [RoundIconButton] instead - a different shape for a different place, over
 * the ground rather than in a bar - but it says the same word.
 */
@Composable
internal fun SettingsAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        AppIcon(IconGlyph.SETTINGS, contentDescription = SETTINGS_WORD)
    }
}

/**
 * The one name this door has. Read by the map's round button as well, so a change of word is one
 * change rather than four.
 */
internal const val SETTINGS_WORD = "Settings"
