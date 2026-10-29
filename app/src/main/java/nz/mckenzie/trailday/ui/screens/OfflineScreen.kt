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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.doc.DocArea
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.other.OtherTrackArea
import nz.mckenzie.trailday.offline.DownloadService
import nz.mckenzie.trailday.offline.DownloadState
import nz.mckenzie.trailday.offline.OfflineArea
import nz.mckenzie.trailday.offline.OfflinePack
import nz.mckenzie.trailday.offline.docHeldWords
import nz.mckenzie.trailday.offline.downloadProgressWords
import nz.mckenzie.trailday.offline.docAreasSummary
import nz.mckenzie.trailday.offline.docAreaWords
import nz.mckenzie.trailday.offline.formatBytes
import nz.mckenzie.trailday.offline.otherTracksHeldWords
import nz.mckenzie.trailday.offline.otherTracksSummary
import nz.mckenzie.trailday.offline.otherTrackAreaWords
import nz.mckenzie.trailday.offline.pictureCountWords
import nz.mckenzie.trailday.viewmodel.OfflineViewModel

/**
 * What is on the phone for the places with no reception: **imagery and what DOC publishes.**
 *
 * The shape is the mock screens': one list rather than tabs - a card per cache, each saying what it holds in one
 * line and carrying its own progress while it downloads - one **Download an area** button that asks which kind,
 * and a sentence about what is kept as the walker browses.
 *
 * The two are one list because they answer one question ("what can I do with no reception?"), and because
 * tabs made the walker choose a page before they could see whether they even had anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineScreen(
    viewModel: OfflineViewModel,
    onOpenTab: (Tab) -> Unit = {},
    onChooseArea: () -> Unit,
    onChooseDocArea: () -> Unit,
    onChooseOtherTracksArea: () -> Unit,
    onChooseTopoArea: () -> Unit
) {
    val stored by viewModel.stored.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val docDataCount by viewModel.docDataCount.collectAsStateWithLifecycle()
    val docBytes by viewModel.docBytes.collectAsStateWithLifecycle()
    val docCounts by viewModel.docCounts.collectAsStateWithLifecycle()
    val docPictures by viewModel.docPictures.collectAsStateWithLifecycle()
    val docAreas by viewModel.docAreas.collectAsStateWithLifecycle()
    val otherTracksDataCount by viewModel.otherTracksDataCount.collectAsStateWithLifecycle()
    val otherTracksBytes by viewModel.otherTracksBytes.collectAsStateWithLifecycle()
    val otherTrackAreas by viewModel.otherTrackAreas.collectAsStateWithLifecycle()
    val topoSummary by viewModel.topoSummary.collectAsStateWithLifecycle()
    val topoAreas by viewModel.topoAreas.collectAsStateWithLifecycle()
    val download by DownloadState.current.collectAsStateWithLifecycle()

    // What is downloading now, if it is this card's own pack: the same sentence the picker's card and the
    // notification show, so the three cannot describe one download three ways.
    val imageryLive = download?.takeIf { it.pack == OfflinePack.IMAGERY }
    val topoLive = download?.takeIf { it.pack == OfflinePack.TOPOGRAPHY }
    val docLive = download?.takeIf { it.pack == OfflinePack.DOC }
    val otherTracksLive = download?.takeIf { it.pack == OfflinePack.OTHER_TRACKS }

    var confirmingClearImagery by remember { mutableStateOf(false) }
    var confirmingClearDocData by remember { mutableStateOf(false) }
    var confirmingClearOtherTracks by remember { mutableStateOf(false) }
    var confirmingClearTopo by remember { mutableStateOf(false) }
    var choosingDownload by remember { mutableStateOf(false) }

    // Counted as the screen appears: the drawn map has no rows to watch, so the honest moment to count
    // it is when the walker is looking at the count - which is also just after a download from the picker.
    LaunchedEffect(Unit) { viewModel.refreshTopography() }

    // A download can also be started from here - resuming an area - so this screen puts the notification
    // up too. Asking twice is safe: the service is already running by then.
    val context = LocalContext.current
    LaunchedEffect(download != null) {
        if (download != null) DownloadService.start(context)
    }

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
            // **The way in comes first.** Downloading is what this page is for, and it used to sit under
            // four cards of things already kept - so a walker arriving here had to read past their own
            // library to find the one action that adds to it.
            Button(
                onClick = { choosingDownload = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Download an area")
            }

            DownloadCard(
                title = OfflinePack.IMAGERY.title,
                icon = { AppIcon(glyph = IconGlyph.MAP, tint = MaterialTheme.colorScheme.primary) },
                // While it is downloading the card's own line is the live sentence: a count of what is
                // held so far would be a second, staler number beside the one that matters.
                summary = imageryLive?.let { downloadProgressWords(it) }
                    ?: imagerySummary(stored.size, summary.sizeLabel, summary.tiles),
                progress = imageryLive?.let { it.percent / 100f } ?: active?.let { it.percent / 100f },
                progressLabel = if (imageryLive != null) {
                    null
                } else {
                    active?.takeIf { !it.isComplete }?.let { "Downloading\u2026 ${it.percent}%" }
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
                title = OfflinePack.DOC.title,
                icon = { AssetKindIcon(kind = AssetKind.HUT, dimension = 24.dp) },
                // What is held, how much of it, and what it costs on disk: "1 area - 543 records - 5.7 MB".
                // The regions are named by the rows underneath, so this line does not repeat them, and
                // the size is the one thing those names cannot say.
                summary = docLive?.let { downloadProgressWords(it) }
                    ?: if (docAreas.isNotEmpty()) {
                        docAreasSummary(docAreas.size, docDataCount, formatBytes(docBytes))
                    } else if (docDataCount == 0) {
                        "Nothing downloaded yet"
                    } else {
                        docHeldWords(docCounts) + docPictures
                            .takeIf { it > 0 }
                            ?.let { " \u00b7 ${pictureCountWords(it)}" }
                            .orEmpty()
                    },
                progress = docLive?.let { it.percent / 100f },
                progressLabel = null,
                // The desk's own line about reading the material offline went with the rest of the
                // preambles, and with Delete in the rows there is nothing left that needs explaining.
                footnote = null,
                error = null,
                clearLabel = "Clear all DOC data".takeIf { docDataCount > 0 },
                onClear = { confirmingClearDocData = true }
            ) {
                // **The named boxes DOC's material was downloaded for**, each one a row: the material is held
                // once and shared, so this is the walker's own list of places rather than a second copy.
                docAreas.forEachIndexed { index, area ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DocAreaRow(area = area, onDelete = { viewModel.deleteDocArea(area) })
                }
            }

            // The second publisher's tracks, on their own card: their own cache, their own download and
            // their own Clear, because a walker clearing DOC's material has not asked to lose the tracks
            // their own council publishes.
            DownloadCard(
                title = OfflinePack.OTHER_TRACKS.title,
                icon = { AppIcon(glyph = IconGlyph.TRAIL, tint = MaterialTheme.colorScheme.primary) },
                // How many regions, how many tracks, and what they cost on disk. The regions are named by
                // the rows underneath, so this line does not repeat them.
                summary = otherTracksLive?.let { downloadProgressWords(it) }
                    ?: otherTracksSummary(
                        otherTrackAreas.size,
                        otherTracksDataCount,
                        formatBytes(otherTracksBytes)
                    ),
                progress = otherTracksLive?.let { it.percent / 100f },
                progressLabel = null,
                footnote = null,
                error = null,
                clearLabel = "Clear other tracks".takeIf { otherTracksDataCount > 0 },
                onClear = { confirmingClearOtherTracks = true }
            ) {
                // **The named boxes they were downloaded for**, each one a row: the tracks are held once
                // and shared, so this is the walker's own list of places rather than a second copy of them.
                otherTrackAreas.forEachIndexed { index, area ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    OtherTrackAreaRow(area = area, onDelete = { viewModel.deleteOtherTrackArea(area) })
                }
            }

            // The drawn map: the other thing a walker can choose to keep, and the cheap one - vector
            // tiles are a fraction of imagery for the same ground, and it is what reads on a hillside.
            DownloadCard(
                title = OfflinePack.TOPOGRAPHY.title,
                icon = { AppIcon(glyph = IconGlyph.LAYERS, tint = MaterialTheme.colorScheme.primary) },
                summary = topoLive?.let { downloadProgressWords(it) }
                    ?: if (topoSummary.tiles == 0L) {
                        "Nothing kept yet"
                    } else {
                        "${topoSummary.tiles} ${tileWord(topoSummary.tiles)} \u00b7 ${topoSummary.sizeLabel}"
                    },
                progress = topoLive?.let { it.percent / 100f },
                progressLabel = null,
                error = null,
                clearLabel = "Clear the map".takeIf { topoSummary.tiles > 0L },
                onClear = { confirmingClearTopo = true }
            ) {
                // **The named pieces of the drawn map**, each one a row. This is what the pack is for: its
                // tiles are a fraction of imagery for the same ground, so a walker keeps several small ones
                // - and several small ones need names to be told apart.
                //
                // A row's own Delete takes the tiles no other piece needs; "Clear the map" takes the rest.
                topoAreas.forEachIndexed { index, area ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AreaRow(
                        area = area,
                        working = working,
                        onResume = { viewModel.resumeTopography(area.id) },
                        onDelete = { viewModel.deleteTopoArea(area.id) }
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
                    headlineContent = { Text("LINZ imagery") },
                    supportingContent = {
                        Text("Land Information NZ tiles inside an area, at a zoom range you choose")
                    },
                    leadingContent = { AppIcon(glyph = IconGlyph.MAP) }
                )
                ListItem(
                    modifier = Modifier.clickable {
                        choosingDownload = false
                        onChooseDocArea()
                    },
                    headlineContent = { Text("DOC assets") },
                    supportingContent = {
                        Text("Dept of Conservation tracks, huts, campsites and bike trails, and their pictures")
                    },
                    leadingContent = { AssetKindIcon(kind = AssetKind.HUT) }
                )
                ListItem(
                    modifier = Modifier.clickable {
                        choosingDownload = false
                        onChooseOtherTracksArea()
                    },
                    headlineContent = { Text("Other tracks") },
                    supportingContent = {
                        Text("Tracks from councils, trusts and clubs, drawn on the map")
                    },
                    leadingContent = { AppIcon(glyph = IconGlyph.TRAIL) }
                )
                ListItem(
                    modifier = Modifier.clickable {
                        choosingDownload = false
                        onChooseTopoArea()
                    },
                    headlineContent = { Text("LINZ Topographic") },
                    supportingContent = {
                        Text("LINZ's drawn map - roads, tracks, contours and names - for reading the land")
                    },
                    leadingContent = { AppIcon(glyph = IconGlyph.LAYERS) }
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
                    "Removes all ${summary.tiles} tiles (${summary.sizeLabel}). The map needs " +
                        "reception until an area is downloaded again."
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
                        " kept for offline use. The map needs reception until an area is downloaded again. " +
                        "Your own places are not touched."
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

    if (confirmingClearOtherTracks) {
        AlertDialog(
            onDismissRequest = { confirmingClearOtherTracks = false },
            title = { Text("Delete downloaded other tracks?") },
            text = {
                Text(
                    "This removes the ${otherTracksHeldWords(otherTracksDataCount)} kept for offline " +
                        "use. The map needs reception until an area is downloaded again. " +
                        "DOC's material and your own places are not touched."
                )
            },
            confirmButton = {
                DestructiveTextButton(
                    text = "Delete",
                    onClick = {
                        confirmingClearOtherTracks = false
                        viewModel.clearOtherTracksData()
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmingClearOtherTracks = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmingClearTopo) {
        AlertDialog(
            onDismissRequest = { confirmingClearTopo = false },
            title = { Text("Delete the kept map?") },
            text = {
                Text(
                    "This removes the ${topoSummary.tiles} tiles (${topoSummary.sizeLabel}) the drawn " +
                        "Topographic map is kept from. The map needs reception until an area is " +
                        "downloaded again, or until it is looked at with a signal."
                )
            },
            confirmButton = {
                DestructiveTextButton(
                    text = "Delete",
                    onClick = {
                        confirmingClearTopo = false
                        viewModel.clearTopography()
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmingClearTopo = false }) { Text("Cancel") }
            }
        )
    }
}

/** "1 tile" rather than "1 tiles": a count is read at a glance, and the wrong word is what gets noticed. */
private fun tileWord(count: Long): String = if (count == 1L) "tile" else "tiles"

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
    /**
     * A line under the card, when there is one worth saying.
     *
     * Mostly there is not: a walker who has opened the Offline screen knows what LINZ imagery is, and a
     * sentence describing the thing above it is a sentence in the way of the thing above it.
     */
    footnote: String? = null,
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
 * One named region of DOC's material: what it is called, and what came back from it.
 *
 * **Delete, and it deletes**: the records inside the region go, except any that another region still
 * listed also covers - a track that crosses a boundary belongs to both.
 */
@Composable
private fun DocAreaRow(area: DocArea, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = area.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = docAreaWords(area),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DestructiveTextButton(text = "Delete", onClick = onDelete)
    }
}

/**
 * One named region of somebody else's tracks: what it is called, and what came back from it.
 *
 * **Delete, and it deletes** - by district name, which is exact: no two regions can claim the same row.
 */
@Composable
private fun OtherTrackAreaRow(area: OtherTrackArea, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = area.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = otherTrackAreaWords(area),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DestructiveTextButton(text = "Delete", onClick = onDelete)
    }
}

/**
 * "2 areas · 61 MB", or that there is nothing yet.
 *
 * **Without the names.** They are on the rows directly under this line, and saying them twice made the
 * subtitle the longest thing on the card while the list below said it better.
 *
 * With no areas but tiles on the phone, it says where those tiles came from: the map keeps what the walker
 * has looked at, so "nothing downloaded" beside a clear button would be two lines disagreeing.
 */
internal fun imagerySummary(areaCount: Int, sizeLabel: String, tiles: Long): String {
    if (areaCount == 0) {
        return if (tiles > 0L) "No areas yet \u00b7 $tiles tiles from browsing" else "Nothing downloaded yet"
    }
    val areas = if (areaCount == 1) "1 area" else "$areaCount areas"
    return "$areas \u00b7 $sizeLabel"
}

/** What an imagery area is doing: how much of it is here, or that it is still coming. */
internal fun areaStatus(area: OfflineArea): String = when {
    area.isComplete -> "Downloaded, ${area.sizeLabel}"
    area.percent > 0 -> "Downloading ${area.percent}%"
    else -> "Not started"
}
