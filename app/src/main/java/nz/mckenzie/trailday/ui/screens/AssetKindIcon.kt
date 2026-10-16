package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetPhrase
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.map.GlyphStyle
import nz.mckenzie.trailday.map.drawKindGlyph

/**
 * A small coloured glyph saying what a place is: five types, one shape each.
 *
 * The shapes live in [drawKindGlyph] (`map/KindGlyphs.kt`) rather than here, because the map's
 * markers are the same shapes at a marker's weight - fatter, filled, with a white edge - and a hut
 * drawn twice from two pieces of code is a hut that will one day look like something else in one
 * of the two places.
 *
 * Drawn rather than shipped as an icon font or a set of images: the app has no icon dependency and
 * does not need one for five shapes, and these only mean anything as a set.
 *
 * The colour is the kind's own: colour and glyph together say which kind it is, and neither may be
 * confusable with the recorded line or the dot that says where the phone is.
 */
@Composable
fun AssetKindIcon(
    kind: AssetKind,
    modifier: Modifier = Modifier,
    dimension: Dp = 20.dp
) {
    Canvas(
        modifier = modifier
            .size(dimension)
            .semantics { contentDescription = AssetPhrase.kind(kind) }
    ) {
        drawKindGlyph(kind, AssetColors.forKind(kind), GlyphStyle.LIST)
    }
}

