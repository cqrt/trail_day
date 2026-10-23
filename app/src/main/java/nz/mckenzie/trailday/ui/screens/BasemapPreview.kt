package nz.mckenzie.trailday.ui.screens

import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.offline.sprayDayUserAgent
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * A real tile of [basemap], for the picker to show what the map looks like.
 *
 * The tile is fetched once per basemap and key, at a fixed place and zoom: one picture of the
 * ground, not a live map, so a settings screen with five of them costs five tiles rather than five
 * renderers. A tile that will not come is a calm tile with the basemap's initial rather than a
 * broken picture.
 */
@Composable
internal fun BasemapPreview(
    basemap: Basemap,
    apiKey: String,
    modifier: Modifier = Modifier
) {
    val tile by produceState<ImageBitmap?>(initialValue = null, basemap, apiKey) {
        value = withContext(Dispatchers.IO) { fetchTile(basemap, apiKey) }
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
    apiKey: String,
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
                apiKey = apiKey,
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

private fun fetchTile(basemap: Basemap, apiKey: String): ImageBitmap? {
    if (basemap.needsKey && apiKey.isBlank()) return null

    val (x, y) = previewTile(PREVIEW_LAT, PREVIEW_LNG, PREVIEW_ZOOM)
    val url = basemap.previewTileUrl(apiKey, PREVIEW_ZOOM, x, y)

    val connection = runCatching {
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", sprayDayUserAgent())
        }
    }.getOrNull() ?: return null

    return try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
        val bytes = connection.inputStream.use { it.readBytes() }
        runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

/** The XYZ tile holding [lat]/[lng] at [zoom], in the usual Web Mercator scheme. */
private fun previewTile(lat: Double, lng: Double, zoom: Int): Pair<Int, Int> {
    val side = 1 shl zoom
    val x = ((lng + 180.0) / 360.0 * side).toInt().coerceIn(0, side - 1)
    val latitude = Math.toRadians(lat)
    val y = ((1.0 - ln(tan(latitude) + 1.0 / cos(latitude)) / Math.PI) / 2.0 * side)
        .toInt()
        .coerceIn(0, side - 1)
    return x to y
}

/** A piece of ground every basemap shows the same way, so the pictures can be compared. */
private const val PREVIEW_LAT = -45.9395714
private const val PREVIEW_LNG = 168.3909702
private const val PREVIEW_ZOOM = 12
