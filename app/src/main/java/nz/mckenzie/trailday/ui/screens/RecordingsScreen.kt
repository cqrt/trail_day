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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.recording.ActivityMode
import nz.mckenzie.trailday.domain.recording.RecordingStatus
import nz.mckenzie.trailday.domain.reference.followedLineWords
import nz.mckenzie.trailday.domain.track.ImportDestination
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.ui.ImportWords
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
    onOpenSettings: () -> Unit = {},
    onOpenRecording: (Long) -> Unit
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val question by viewModel.question.collectAsStateWithLifecycle()

    val searching = query.isNotBlank()

    // Several at once: a week of walks exported from a watch is a week of files, and importing them
    // one at a time is the sort of chore this app is meant to save.
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.importFiles(uris) }

    question?.let { asking ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text(asking.title) },
            text = { Text(asking.sentence) },
            // Both answers are answers: the walker pressed Import on this screen, so the likelier one
            // sits where a confirm always sits. A file sent to Places is never asked what it was - the
            // question is only put to one going to Activities.
            confirmButton = {
                TextButton(
                    onClick = {
                        when (asking) {
                            is ImportWords.Ask.WhereItGoes ->
                                viewModel.answerImport(ImportDestination.ACTIVITIES)

                            is ImportWords.Ask.WalksOrRides ->
                                viewModel.answerMode(ActivityMode.RIDE)
                        }
                    }
                ) { Text(asking.firstLabel) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        when (asking) {
                            is ImportWords.Ask.WhereItGoes ->
                                viewModel.answerImport(ImportDestination.PLACES)

                            is ImportWords.Ask.WalksOrRides ->
                                viewModel.answerMode(ActivityMode.WALK)
                        }
                    }
                ) { Text(asking.secondLabel) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activities") },
                actions = { SettingsAction(onOpenSettings) }
            )
        },
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
                    placeholder = "Search activities"
                )

                // Walks and rides, with the count of each, once there is a reason to tell them apart: a
                // list of nothing but walks needs no chip saying so. A search is a question of its own,
                // so the row steps aside while one is being asked - the same rule the places list follows.
                if (!searching && bothModes(sessions)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(
                            selected = filter == null,
                            label = "All ${sessions.size}",
                            onClick = { viewModel.showOnly(null) }
                        )
                        ActivityMode.entries.forEach { mode ->
                            ChoiceChip(
                                selected = filter == mode,
                                label = "${modeWord(mode)} ${sessions.count { it.mode == mode }}",
                                onClick = { viewModel.showOnly(mode) }
                            )
                        }
                    }
                }

                // What the list holds, in the mock screens' line under the search: how many trips and how far
                // they went between them. It counts what is shown, so a search's answer is described too.
                Text(
                    text = summaryOf(shown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // The way a walk recorded somewhere else comes in, in the same button Places uses, because
            // it does the same thing. It steps aside while the walker is looking for an activity, so
            // the answer has the screen.
            if (!searching) {
                SecondaryPlaceAction(
                    label = if (busy) "Importing…" else "Import",
                    glyph = IconGlyph.OFFLINE,
                    enabled = !busy,
                    onClick = { importLauncher.launch(TRACK_MIME_TYPES) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            message?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }

            when {
                sessions.isEmpty() -> EmptyState(
                    glyph = IconGlyph.RECORDINGS,
                    title = "No activities yet",
                    body = "Walks and rides you record appear here.",
                    actionLabel = "Record a track",
                    onAction = { onOpenTab(Tab.RECORD) }
                )

                shown.isEmpty() -> Text(
                    text = "No activity of yours has \"${query.trim()}\" in its name.",
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
 * "4 activities - 46.5 km between them", for whatever the list is showing.
 *
 * One activity is its own sentence: "1 activity - 2.4 km between them" would be describing a crowd of one.
 * **Once the list holds both walks and rides it says how many of each**, because a list of eighteen trips
 * is a season somebody wants to read the shape of - and the chips above can only count what is there.
 */
internal fun summaryOf(rows: List<RecordingRow>): String {
    val metres = rows.sumOf { it.distanceM }
    val walks = rows.count { it.mode == ActivityMode.WALK }
    val rides = rows.size - walks
    return when {
        rows.isEmpty() -> "No activities"
        rows.size == 1 -> if (metres <= 0.0) "1 activity" else "1 activity \u00b7 ${formatDistance(metres)}"
        bothModes(rows) -> listOfNotNull(
            "${rows.size} activities",
            "${counted(walks, "walk", "walks")}, ${counted(rides, "ride", "rides")}",
            formatDistance(metres).takeIf { metres > 0.0 }
        ).joinToString(" \u00b7 ")
        metres <= 0.0 -> "${rows.size} activities"
        else -> "${rows.size} activities \u00b7 ${formatDistance(metres)} between them"
    }
}

/** Whether these trips are of both modes, which is when the chips and the split sentence are worth it. */
internal fun bothModes(rows: List<RecordingRow>): Boolean =
    rows.any { it.mode == ActivityMode.RIDE } && rows.any { it.mode == ActivityMode.WALK }

/** "1 ride", "3 rides": the count and the word that agrees with it. */
private fun counted(count: Int, one: String, many: String): String =
    "$count ${if (count == 1) one else many}"

/** "Walks", "Rides": the chip's own word, capitalised because it starts the label. */
private fun modeWord(mode: ActivityMode): String =
    mode.many.replaceFirstChar { it.uppercase() }

/**
 * One trip, as a card: **its own tile, the name, when and how far, and the place it was for.**
 *
 * A walk wears the recorded line's green, because a walk is the line that was walked and that is the
 * colour it wears on every map in the app. **A ride wears a bike track's tile** - the walker's own bike
 * colour, with the bike track's glyph - so the list tells a day on the bike from a day on foot without
 * the row having to say so in words.
 */
@Composable
private fun TripCard(row: RecordingRow, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (row.mode == ActivityMode.RIDE) {
                KindTile(kind = AssetKind.BIKE_TRACK)
            } else {
                RecordedTile()
            }
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
internal fun recordingDetail(row: RecordingRow): String = listOfNotNull(
    formatDate(row.startedAtEpochMs),
    formatDistance(row.distanceM),
    // A walk from a file with no times in it has no length to claim, so the row says nothing rather
    // than "0s" beside a distance - see [RecordingRow.durationMs].
    row.durationMs?.let { formatDuration(it) },
    "${row.pointCount} points"
).joinToString(" \u00b7 ")

/** Which place a trip was made for, or that the link to it is gone. */
internal fun recordingAsset(row: RecordingRow): String {
    row.assetName?.let { return "For $it" }
    // A published line never becomes a place, so a trip that followed one names whose line it was -
    // and which words those are is the reference's and the publisher's to say: see [followedLineWords].
    followedLineWords(row.followed)?.let { return it }
    // A walk that came in a file was not recorded against anything, so it says where it came from
    // rather than calling itself a new track - which is what a walk of the walker's own is.
    if (row.imported) return "Imported from a file"
    if (row.assetWasDeleted) return "Its place has since been deleted"
    return when (row.mode) {
        ActivityMode.RIDE -> "A new bike track"
        ActivityMode.WALK -> "A new track"
    }
}
