package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
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
 *
 * [containerAlpha] is how solid that circle is. The home map's controls are drawn at
 * [nz.mckenzie.trailday.ui.theme.TrailDayPaper.ALPHA] so a fifth of the ground shows through and the map is
 * read to the edge; a map that is a page's own top - a place's page, a preview - leaves it at solid, because
 * there the control sits on a header rather than on the ground. **The glyph is not thinned with it**: the
 * paper goes, the ink stays.
 */
@Composable
internal fun RoundIconButton(
    glyph: IconGlyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    containerAlpha: Float = 1f
) {
    // The paper is read before it is thinned, and the glyph's colour comes from that: a colour with an
    // alpha in it matches no role in the scheme, so `contentColorFor` would answer nothing and the glyph
    // would fall back to the default black - which is a dark glyph inside a dark circle at night.
    val paper = MaterialTheme.colorScheme.surface
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = paper.copy(alpha = containerAlpha),
        contentColor = MaterialTheme.colorScheme.contentColorFor(paper),
        shadowElevation = TrailDayElevation.floating,
        modifier = modifier
    ) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            AppIcon(glyph = glyph, contentDescription = description, dimension = size * 0.46f)
        }
    }
}
