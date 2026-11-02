package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A quieter way in: one of the smaller buttons beside Add a spot.
 *
 * **Shared rather than copied.** Places has three of these - Import, DOC, Other tracks - and
 * Activities needs one that looks the same, because it does the same thing: a file comes in from
 * somewhere else. Two composables would be two things to keep in step, and the Import button on one
 * screen would drift from the Import button on the other for no reason anybody could name.
 */
@Composable
internal fun SecondaryPlaceAction(
    label: String,
    glyph: IconGlyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface.copy(
            alpha = if (enabled) 1f else 0.45f
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIcon(glyph = glyph, dimension = 20.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }
    }
}

/**
 * What the file picker is shown, when the walker goes looking for a track.
 *
 * Loose on purpose, ending in the wildcard: Android decides a file's MIME type from its extension, and a
 * `.gpx` off a Garmin or out of an email is regularly `application/octet-stream` or `text/plain`.
 * A picker filtered to the exact types is a picker with the walker's own file greyed out.
 */
internal val TRACK_MIME_TYPES = arrayOf(
    "application/gpx+xml",
    "application/vnd.google-earth.kml+xml",
    "application/vnd.google-earth.kmz",
    "application/geo+json",
    "application/json",
    "application/xml",
    "text/xml",
    "text/plain",
    "*/*"
)
