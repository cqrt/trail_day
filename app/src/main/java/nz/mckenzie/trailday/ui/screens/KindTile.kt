package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.look.Look
import nz.mckenzie.trailday.map.KindTileLook
import nz.mckenzie.trailday.ui.theme.TileSize

/**
 * A place's kind as the mock screens draw it: **the glyph on a tile in a pale tint of the kind's own
 * colour.**
 *
 * A bare glyph on a row is a small mark on whatever is behind it; a tile gives the eye something to land on
 * and says which kind it is from further away than a 20-point outline can. The tint is the kind's own colour
 * at a low alpha rather than a second palette, so a kind's tile cannot drift from a kind's line or marker.
 *
 * The colour comes from [Look]: the chosen one on a light screen, its derived dark tone on a dark one,
 * because a deep purple glyph on a dark tile is a glyph nobody sees.
 */
@Composable
fun KindTile(
    kind: AssetKind,
    modifier: Modifier = Modifier,
    size: Dp = TileSize
) {
    val dark = isSystemInDarkTheme()
    val colour = kindColour(kind, dark)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(KindTileLook.CORNER_DP.dp))
            .background(
                colour.copy(alpha = if (dark) KindTileLook.TINT_ALPHA_DARK else KindTileLook.TINT_ALPHA_LIGHT)
            ),
        contentAlignment = Alignment.Center
    ) {
        AssetKindIcon(kind = kind, dimension = size * KindTileLook.GLYPH_FRACTION)
    }
}

/**
 * The kind's colour for the screen it is on: the light set, or the derived dark one.
 *
 * **The look is collected rather than read**, so a glyph already on the screen repaints when the
 * walker changes a colour. Reading it inside a `remember` whose key was only the kind is what left a
 * row of the look screen showing the previous colour: the cached value had no key that changed.
 */
@Composable
internal fun kindColour(kind: AssetKind, dark: Boolean = isSystemInDarkTheme()): Color {
    val hex = kindHex(kind, dark)
    return remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
}

/** The same colour as the hex the map's own drawing takes: one choice, two shapes of it. */
@Composable
internal fun kindHex(kind: AssetKind, dark: Boolean = isSystemInDarkTheme()): String {
    val look by Look.current.collectAsStateWithLifecycle()
    return remember(look, kind, dark) {
        if (dark) look.colourOnDark(kind) else look.colourOf(kind).hex
    }
}
