package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.recording.RecordingStatus
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.ui.formatDate
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.formatDuration
import nz.mckenzie.trailday.ui.theme.TileSize
import nz.mckenzie.trailday.viewmodel.RecordingRow
import nz.mckenzie.trailday.viewmodel.RecordingsViewModel

/**
 * Every GPS trip on the device, newest first, and a search over their names.
 *
 * A trip is a walk or a ride, and it deliberately outlives the place it was recorded against - so a trip whose
 * place has been deleted is still listed, and says so. **Nothing destructive is on a row**: a trip is removed
 * from its own page, where the line and its numbers are in front of the walker before it goes.
 *
 * The look is the mock screens': a search pill, a line saying what the list holds, and the trips as cards - the
 * recorded line's own tile, the name, when and how far, and the place it was for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    viewModel: RecordingsViewModel,
    onOpenTab: (Tab) -> Unit = {},
    onOpenRecording: (Long) -> Unit
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val searching = query.isNotBlank()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Trips") }) },
        bottomBar = { TrailDayNavBar(current = Tab.RECORDINGS, onSelect = onOpenTab) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            if (sessions.isNotEmpty()) {
                SearchField(
                    value = query,
                    onValueChange = viewModel::search,
                    placeholder = "Search trips"
                )

                // What the list holds, in the mock screens' line under the search: how many trips and how far
                // they went between them. It counts what is shown, so a search's answer is described too.
                Text(
                    text = summaryOf(shown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            message?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }

            when {
                sessions.isEmpty() -> EmptyState(
                    glyph = IconGlyph.RECORDINGS,
                    title = "No trips yet",
                    body = "Walks and rides you record appear here.",
                    actionLabel = "Record a line",
                    onAction = { onOpenTab(Tab.RECORD) }
                )

                shown.isEmpty() -> Text(
                    text = "No trip of yours has \"${query.trim()}\" in its name.",
                    style = MaterialTheme.typography.bodyMedium
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(shown, key = { row -> row.id }) { row ->
                        TripCard(row = row, onOpen = { onOpenRecording(row.id) })
                    }
                }
            }
        }
    }
}

/**
 * "4 trips - 46.5 km between them", for whatever the list is showing.
 *
 * One trip is its own sentence: "1 trip - 2.4 km between them" would be describing a crowd of one.
 */
internal fun summaryOf(rows: List<RecordingRow>): String {
    val metres = rows.sumOf { it.distanceM }
    return when {
        rows.isEmpty() -> "No trips"
        rows.size == 1 -> if (metres <= 0.0) "1 trip" else "1 trip \u00b7 ${formatDistance(metres)}"
        metres <= 0.0 -> "${rows.size} trips"
        else -> "${rows.size} trips \u00b7 ${formatDistance(metres)} between them"
    }
}

/**
 * One trip, as a card: **the recorded line's own tile, the name, when and how far, and the place it was for.**
 *
 * The tile is the recorded line's green rather than a kind's colour: a trip is not one of the five kinds, it is
 * the line that was walked, and it wears the colour that line wears on every map in the app.
 */
@Composable
private fun TripCard(row: RecordingRow, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RecordedTile()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = row.name, style = MaterialTheme.typography.titleMedium)
                // A trip still being recorded says so: that is what the walker is looking for when they
                // scroll back to find it.
                recordingStatus(row)?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = recordingDetail(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = recordingAsset(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppIcon(
                glyph = IconGlyph.CHEVRON,
                dimension = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The recorded line's own tile: its green, and the glyph that means "what was actually walked". */
@Composable
private fun RecordedTile() {
    val hex = if (isSystemInDarkTheme()) AssetColors.RECORDED_DARK else AssetColors.RECORDED
    val colour = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }

    Box(
        modifier = Modifier
            .size(TileSize)
            .clip(RoundedCornerShape(TileSize / 3.5f))
            .background(colour.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(glyph = IconGlyph.RECORDINGS, dimension = TileSize * 0.5f, tint = colour)
    }
}

/** What a recording is doing, or null once there is nothing left to say about it. */
internal fun recordingStatus(row: RecordingRow): String? = if (row.isFinished) {
    null
} else {
    when (row.status) {
        RecordingStatus.RECORDING -> "still recording"
        RecordingStatus.PAUSED -> "paused"
        RecordingStatus.FINISHED -> null
    }
}

/** When it was, how far it went, and how much of it was kept. */
internal fun recordingDetail(row: RecordingRow): String =
    "${formatDate(row.startedAtEpochMs)} \u00b7 ${formatDistance(row.distanceM)} \u00b7 " +
        "${formatDuration(row.durationMs)} \u00b7 ${row.pointCount} points"

/** Which place a trip was made for, or that the link to it is gone. */
internal fun recordingAsset(row: RecordingRow): String = when {
    row.assetName != null -> "For ${row.assetName}"
    row.assetWasDeleted -> "Its place has since been deleted"
    else -> "Not linked to a place"
}
