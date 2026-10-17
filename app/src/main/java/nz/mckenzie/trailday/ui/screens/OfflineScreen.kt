package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.offline.OfflineArea
import nz.mckenzie.trailday.offline.docHeldWords
import nz.mckenzie.trailday.offline.pictureCountWords
import nz.mckenzie.trailday.viewmodel.OfflineViewModel

/**
 * What is on the phone for the places with no reception: **imagery, what DOC publishes, and the trips.**
 *
 * The shape is the mock screens': one list rather than tabs - a card per cache, each saying what it holds in one
 * line and carrying its own progress while it downloads - one **Download an area** button that asks which kind,
 * a sentence about what is kept as the walker browses, and a row saying the trips are always here.
 *
 * The three are one list because they answer one question ("what can I do with no reception?"), and because
 * tabs made the walker choose a page before they could see whether they even had anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineScreen(
    viewModel: OfflineViewModel,
    onOpenTab: (Tab) -> Unit = {},
    onChooseArea: () -> Unit,
    onChooseDocArea: () -> Unit
) {
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val stored by viewModel.stored.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val docDataCount by viewModel.docDataCount.collectAsStateWithLifecycle()
    val docCounts by viewModel.docCounts.collectAsStateWithLifecycle()
    val docPictures by viewModel.docPictures.collectAsStateWithLifecycle()

    var confirmingClearImagery by remember { mutableStateOf(false) }
    var confirmingClearDocData by remember { mutableStateOf(false) }
    var choosingDownload by remember { mutableStateOf(false) }

    // The area being downloaded, if one is: its progress is the card's own bar.
    val active = stored.firstOrNull { !it.isComplete }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Offline") }) },
        bottomBar = { TrailDayNavBar(current = Tab.OFFLINE, onSelect = onOpenTab) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DownloadCard(
                title = "Imagery",
                icon = { AppIcon(glyph = IconGlyph.MAP, tint = MaterialTheme.colorScheme.primary) },
                summary = imagerySummary(stored, summary.sizeLabel, summary.tiles),
                progress = active?.let { it.percent / 100f },
                progressLabel = active?.let { "Downloading\u2026 ${it.percent}%" },
                footnote = if (apiKey.isBlank()) {
                    "Add a LINZ Basemaps key in Settings to download imagery."
                } else {
                    null
                },
                error = error,
                clearLabel = "Clear all imagery".takeIf { summary.tiles > 0L },
                onClear = { confirmingClearImagery = true }
            ) {
                stored.forEachIndexed { index, area ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AreaRow(
                        area = area,
                        working = working,
                        onResume = { viewModel.resume(area.id) },
                        onDelete = { viewModel.delete(area.id) }
                    )
                }
            }

            DownloadCard(
                title = "DOC data",
                icon = { AssetKindIcon(kind = AssetKind.HUT, dimension = 24.dp) },
                summary = if (docDataCount == 0) {
                    "Nothing downloaded yet"
                } else {
                    docHeldWords(docCounts) + docPictures
                        .takeIf { it > 0 }
                        ?.let { " \u00b7 ${pictureCountWords(it)}" }
                        .orEmpty()
                },
                progress = null,
                progressLabel = null,
                footnote = "The map's DOC layer and the DOC browser read this with no reception. None of it " +
                    "is yours to change, and none of it needs a LINZ key.",
                error = null,
                clearLabel = "Clear all DOC data".takeIf { docDataCount > 0 },
                onClear = { confirmingClearDocData = true }
            ) {
                // No rows: DOC's material arrives by area and is counted as one thing, which is the whole
                // point of it being one entry.
            }

            Button(
                onClick = { choosingDownload = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Download an area")
            }

            Text(
                text = "Place names and imagery you have looked at are kept as you browse. The map works " +
                    "with no reception anywhere you have downloaded or visited.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // The trips need nothing: they are the record, and the record is on the phone. The row says so
            // and opens them, because a walker wondering whether they need to download their own walks
            // deserves an answer rather than silence.
            Card(
                onClick = { onOpenTab(Tab.RECORDINGS) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AppIcon(glyph = IconGlyph.RECORDINGS, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("My trips", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Always on this phone - nothing to download",
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
    }

    if (choosingDownload) {
        ModalBottomSheet(onDismissRequest = { choosingDownload = false }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "Download an area",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                ListItem(
                    modifier = Modifier.clickable {
                        choosingDownload = false
                        onChooseArea()
                    },
                    headlineContent = { Text("Imagery for the map") },
                    supportingContent = { Text("Tiles inside an area, at a zoom range you choose") },
                    leadingContent = { AppIcon(glyph = IconGlyph.MAP) }
                )
                ListItem(
                    modifier = Modifier.clickable {
                        choosingDownload = false
                        onChooseDocArea()
                    },
                    headlineContent = { Text("What DOC publishes") },
                    supportingContent = {
                        Text("Its tracks, huts, campsites and bike locations, and their pictures")
                    },
                    leadingContent = { AssetKindIcon(kind = AssetKind.HUT) }
                )
            }
        }
    }

    if (confirmingClearImagery) {
        AlertDialog(
            onDismissRequest = { confirmingClearImagery = false },
            title = { Text("Delete downloaded imagery?") },
            text = {
                Text(
                    "This removes all ${summary.tiles} tiles (${summary.sizeLabel}) and the " +
                        "list of areas. The map will need reception until areas are " +
                        "downloaded again."
                )
            },
            confirmButton = {
                DestructiveTextButton(
                    text = "Delete",
                    onClick = {
                        confirmingClearImagery = false
                        viewModel.clearTiles()
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmingClearImagery = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmingClearDocData) {
        AlertDialog(
            onDismissRequest = { confirmingClearDocData = false },
            title = { Text("Delete downloaded DOC data?") },
            text = {
                Text(
                    "This removes the ${docHeldWords(docCounts)}" +
                        (docPictures.takeIf { it > 0 }?.let { " and ${pictureCountWords(it)}" } ?: "") +
                        " kept for offline use. The map's DOC layer and the DOC browser will need " +
                        "reception until an area is downloaded again. Nothing of your own is touched."
                )
            },
            confirmButton = {
                DestructiveTextButton(
                    text = "Delete",
                    onClick = {
                        confirmingClearDocData = false
                        viewModel.clearDocData()
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmingClearDocData = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * One cache, as the mock screens draw it: **an icon, where it stands, a title, and a line saying what it holds**
 * - with the download's own progress along the card's foot while something is coming.
 *
 * The children are whatever the cache needs under that line (imagery has its areas; DOC's material arrives as
 * one thing and has none), so the card is one shape for two different bodies.
 */
@Composable
private fun DownloadCard(
    title: String,
    icon: @Composable () -> Unit,
    summary: String,
    progress: Float?,
    progressLabel: String?,
    footnote: String?,
    error: String?,
    clearLabel: String?,
    onClear: () -> Unit,
    children: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                icon()
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                progressLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            children()

            footnote?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            clearLabel?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DestructiveTextButton(
                    text = it,
                    onClick = onClear,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                )
            }
        }
    }
}

/** One imagery area in its card: what it is, how it is going, and the two things it can do. */
@Composable
private fun AreaRow(
    area: OfflineArea,
    working: Boolean,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = area.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = areaStatus(area),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!area.isComplete) {
            TextButton(onClick = onResume, enabled = !working) { Text("Resume") }
        }
        DestructiveTextButton(text = "Delete", onClick = onDelete)
    }
}

/**
 * "2 areas - 61 MB - Nelson Lakes, Mt Aspiring", or that there is nothing yet.
 *
 * With no areas but tiles on the phone, it says where those tiles came from: the map keeps what the walker
 * has looked at, so "nothing downloaded" beside a clear button would be two lines disagreeing.
 */
internal fun imagerySummary(stored: List<OfflineArea>, sizeLabel: String, tiles: Long): String {
    if (stored.isEmpty()) {
        return if (tiles > 0L) "No areas yet \u00b7 $tiles tiles from browsing" else "Nothing downloaded yet"
    }
    val areas = if (stored.size == 1) "1 area" else "${stored.size} areas"
    val names = stored.map { it.name }.take(2).joinToString(", ")
    val more = if (stored.size > 2) "\u2026" else ""
    return "$areas \u00b7 $sizeLabel \u00b7 $names$more"
}

/** What an imagery area is doing: how much of it is here, or that it is still coming. */
internal fun areaStatus(area: OfflineArea): String = when {
    area.isComplete -> "Downloaded, ${area.sizeLabel}"
    area.percent > 0 -> "Downloading ${area.percent}%"
    else -> "Not started"
}
