package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nz.mckenzie.trailday.domain.look.LookColour

/** How big a swatch is: a fingertip's width, because it is tapped rather than pointed at. */
private val CHIP_DP = 34.dp

/**
 * One colour of the shelf, as a round swatch the walker taps.
 *
 * **The map's own colour on every theme.** The swatch is a promise about a line drawn over imagery, and
 * imagery is light or dark by what it is a picture of rather than by what the phone is set to - so the
 * light hex is the one shown, and the derived dark tone is for glyphs on cards, not here. Showing the
 * dark tone would be showing a colour the map will not draw.
 *
 * A chosen swatch wears a ring *and* a tick, for the same reason the chips do: out in the sun a ring on
 * a coloured disc is a thing the eye can miss, and this is a screen used in exactly that light.
 */
@Composable
internal fun ColourChip(
    colour: LookColour,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fill = remember(colour) { Color(android.graphics.Color.parseColor(colour.hex)) }
    Box(
        modifier = modifier
            .size(CHIP_DP + 6.dp)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .semantics { contentDescription = colour.displayName }
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(CHIP_DP)
                .clip(CircleShape)
                // A hairline ring in the app's own outline colour: charcoal on white needs an edge and
                // so does white on charcoal, and one ring answers both.
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .background(fill),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                AppIcon(
                    glyph = IconGlyph.CHECK,
                    dimension = 18.dp,
                    // The tick is drawn in whichever of black and white reads on the swatch under it:
                    // a white tick on the orange is invisible, and a dark one on the charcoal is worse.
                    tint = if (fill.luminance() > 0.5f) Color.Black else Color.White
                )
            }
        }
    }
}
