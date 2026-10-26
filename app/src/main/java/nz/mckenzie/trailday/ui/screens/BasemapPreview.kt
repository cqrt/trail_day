package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.map.BasemapPreviewCache

/**
 * A real tile of [basemap], for the picker to show what the map looks like.
 *
 * The tile comes from [BasemapPreviewCache], which the application prefetches at launch: one small
 * tile per basemap, remembered for the life of the process, so the picker draws its pictures at
 * once rather than waiting on the network every time it opens. A tile that will not come is a calm
 * tile with the basemap's initial rather than a broken picture.
 */
@Composable
internal fun BasemapPreview(
    basemap: Basemap,
    modifier: Modifier = Modifier
) {
    val tile by produceState<ImageBitmap?>(initialValue = null, basemap) {
        value = BasemapPreviewCache.load(basemap)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        val picture = tile
        if (picture != null) {
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = basemap.displayName.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * One basemap in the picker: its example tile, its name and its one-line summary.
 *
 * A card rather than a chip because the picture is the point: the walker is choosing what their
 * map looks like, and a name alone asks them to remember.
 */
@Composable
internal fun BasemapChoice(
    basemap: Basemap,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            }
        )
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasemapPreview(
                basemap = basemap,
                modifier = Modifier.size(64.dp)
            )
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
            ) {
                Text(basemap.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    basemap.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            if (selected) {
                AppIcon(glyph = IconGlyph.CHECK, dimension = 20.dp)
            }
        }
    }
}
