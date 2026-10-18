package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nz.mckenzie.trailday.ui.theme.TrailDayElevation

/**
 * A round button that floats over a map: a white circle with a shadow, as the mock screens draw the controls
 * on a screen whose subject is the map.
 *
 * White rather than tinted because it sits over imagery: a tinted circle has to argue with whatever colour the
 * ground behind it happens to be, and a white one reads on all of them.
 */
@Composable
internal fun RoundIconButton(
    glyph: IconGlyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = TrailDayElevation.floating,
        modifier = modifier
    ) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            AppIcon(glyph = glyph, contentDescription = description, dimension = size * 0.46f)
        }
    }
}
