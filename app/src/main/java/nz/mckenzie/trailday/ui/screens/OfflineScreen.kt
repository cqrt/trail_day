package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.map.AssetGeoJson
import nz.mckenzie.trailday.map.AssetLine
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.offline.OfflineArea
import nz.mckenzie.trailday.offline.docHeldWords
import nz.mckenzie.trailday.viewmodel.OfflineViewModel

/**
 * What is downloaded for offline use, in two tabs: basemap imagery, and what DOC publishes.
 *
 * The two are **independent caches** - imagery makes the map work with no reception, DOC's material
 * makes the map's reference layer and the browser work with no reception - so they are chosen, counted
 * and cleared separately, and neither can fill the other's tab. Choosing an area is the map picker
 * either way; the DOC one keeps all four of DOC's services in one act.
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
    val docBounds by viewModel.docBounds.collectAsStateWithLifecycle()
    val docCounts by viewModel.docCounts.collectAsStateWithLifecycle()

    var chosenTab by rememberSaveable { mutableStateOf(0) }
    var confirmingClearImagery by rememberSaveable { mutableStateOf(false) }
    var confirmingClearDocData by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Offline areas") }) },
        bottomBar = { TrailDayNavBar(current = Tab.OFFLINE, onSelect = onOpenTab) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = chosenTab) {
                Tab(
                    selected = chosenTab == 0,
                    onClick = { chosenTab = 0 },
                    text = { Text("Imagery") }
                )
                Tab(
                    selected = chosenTab == 1,
                    onClick = { chosenTab = 1 },
                    text = { Text("DOC data") }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (chosenTab == 0) {
                    ImageryTab(
                        apiKey = apiKey,
                        stored = stored,
                        working = working,
                        summaryLabel = "${summary.tiles} tiles · ${summary.sizeLabel}",
                        showClear = summary.tiles > 0L,
                        error = error,
                        onChooseArea = onChooseArea,
                        onResume = viewModel::resume,
                        onDelete = viewModel::delete,
                        onClear = { confirmingClearImagery = true }
                    )
                } else {
                    DocDataTab(
                        apiKey = apiKey,
                        count = docDataCount,
                        counts = docCounts,
                        bounds = docBounds,
                        onChooseArea = onChooseDocArea,
                        onClear = { confirmingClearDocData = true }
                    )
                }
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
                    "This removes the ${docHeldWords(docCounts)} kept for offline use. The map's DOC " +
                        "layer and the DOC browser will need reception until an area is downloaded " +
                        "again. Nothing of your own is touched."
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

/** Imagery for the map: the areas chosen on it, and the tiles they hold. */
@Composable
private fun ImageryTab(
    apiKey: String,
    stored: List<OfflineArea>,
    working: Boolean,
    summaryLabel: String,
    showClear: Boolean,
    error: String?,
    onChooseArea: () -> Unit,
    onResume: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit
) {
    Text(
        text = "Aerial imagery for the map where there is no reception. Choose an area on the map " +
            "and the tiles inside it are stored on the phone.",
        style = MaterialTheme.typography.bodySmall
    )

    OutlinedButton(onClick = onChooseArea, modifier = Modifier.fillMaxWidth()) {
        Text("Choose an area on the map")
    }

    if (apiKey.isBlank()) {
        Text(
            text = "Add a LINZ Basemaps key in Settings before downloading imagery.",
            style = MaterialTheme.typography.bodySmall
        )
    }

    error?.let {
        Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Stored areas (${stored.size})",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall
        )
        Text(text = summaryLabel, style = MaterialTheme.typography.bodySmall)
    }

    if (stored.isEmpty()) {
        EmptyState(
            glyph = IconGlyph.OFFLINE,
            title = "No imagery downloaded yet",
            body = "Choose an area and its tiles are stored on the phone, so the map keeps " +
                "working where there is no reception.",
            actionLabel = "Choose an area",
            onAction = onChooseArea
        )
    } else {
        stored.forEach { area ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    // Where the area is, over the imagery it cached: a listing says where as well as
                    // what, and the white box is the ground the tiles cover.
                    CachedAreaMap(bounds = area.bounds, apiKey = apiKey)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(area.name, style = MaterialTheme.typography.bodyMedium)
                        Text(areaStatus(area), style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!area.isComplete) {
                                TextButton(onClick = { onResume(area.id) }, enabled = !working) {
                                    Text("Resume")
                                }
                            }
                            DestructiveTextButton(text = "Delete", onClick = { onDelete(area.id) })
                        }
                    }
                }
            }
        }
    }

    if (showClear) {
        DestructiveTextButton(text = "Clear downloaded imagery", onClick = onClear)
    }
}

/**
 * What DOC publishes: drawn on the map as DOC's own layer, and kept here for the hills.
 *
 * One entry for four services, because a walker keeps an area rather than a kind of thing: the same
 * download, the same count, the same clearing. What arrived is listed by service, because "412 tracks,
 * 38 huts" is the shape a walker checks their area against.
 */
@Composable
private fun DocDataTab(
    apiKey: String,
    count: Int,
    counts: Map<DocSource, Int>,
    bounds: LatLngBounds?,
    onChooseArea: () -> Unit,
    onClear: () -> Unit
) {
    Text(
        text = "What the Department of Conservation publishes - its tracks, huts, campsites and bike " +
            "locations - drawn on the map as DOC's own layer. Downloading an area keeps everything " +
            "inside it on the phone, so the layer draws and the DOC browser works with no reception; " +
            "none of it is yours to change, and none of it needs a LINZ key.",
        style = MaterialTheme.typography.bodySmall
    )

    OutlinedButton(onClick = onChooseArea, modifier = Modifier.fillMaxWidth()) {
        Text("Choose an area on the map")
    }

    if (count == 0) {
        EmptyState(
            glyph = IconGlyph.MAP,
            title = "Nothing from DOC downloaded",
            body = "Choose an area and everything DOC publishes inside it is downloaded, drawn on the " +
                "map, and available to the DOC browser with no reception.",
            actionLabel = "Choose an area",
            onAction = onChooseArea
        )
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                // The ground the cached material covers: a cache has no box of its own, so this is the
                // extent of the things themselves.
                bounds?.let { CachedAreaMap(bounds = it, apiKey = apiKey) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${docHeldWords(counts)} kept",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Available to the map and the DOC browser with no reception.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        DestructiveTextButton(text = "Clear downloaded DOC data", onClick = onClear)
    }
}

/**
 * Where a cache is: the area drawn over the imagery, fitted so the whole box is in the frame.
 *
 * Aerial imagery whatever basemap the walker reads their own maps on, for the same reason the
 * picker uses it: this is a picture of what was downloaded, and imagery is the only basemap whose
 * licence allows downloading ahead of time.
 */
@Composable
private fun CachedAreaMap(bounds: LatLngBounds, apiKey: String) {
    Box {
        BasemapView(
            basemap = Basemap.LINZ_AERIAL,
            apiKey = apiKey,
            assetGeoJson = boundsOutline(bounds),
            fitBounds = bounds,
            // A picture of what was downloaded, not a map to explore: no dragging, and no dot for
            // where the phone happens to be - neither is what the thumbnail is about.
            interactive = false,
            showPosition = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        )
        AttributionStrip(
            basemap = Basemap.LINZ_AERIAL,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 4.dp, bottom = 4.dp)
        )
    }
}

private const val CACHED_AREA_OUTLINE_ID = -1L

/** White, because it is the one colour the app uses for nothing else - see the picker's own outline. */
private const val CACHED_AREA_OUTLINE_COLOUR = "#FFFFFF"

/** A box as a closed line, which is all the map's line layer needs to draw it. */
private fun boundsOutline(bounds: LatLngBounds): String = AssetGeoJson.build(
    listOf(
        AssetLine(
            assetId = CACHED_AREA_OUTLINE_ID,
            name = "Cached area",
            colorHex = CACHED_AREA_OUTLINE_COLOUR,
            points = listOf(
                GeoPoint(bounds.minLat, bounds.minLng),
                GeoPoint(bounds.maxLat, bounds.minLng),
                GeoPoint(bounds.maxLat, bounds.maxLng),
                GeoPoint(bounds.minLat, bounds.maxLng),
                GeoPoint(bounds.minLat, bounds.minLng)
            )
        )
    )
)

/** One line describing where an imagery area got to, honest about anything missing. */
private fun areaStatus(area: OfflineArea): String = when {
    area.isComplete && area.missingTiles == 0 ->
        "${area.plannedTiles} tiles · ${area.sizeLabel}"

    area.isComplete ->
        "${area.storedTiles} of ${area.plannedTiles} tiles · ${area.sizeLabel} " +
            "(${area.missingTiles} have no imagery at LINZ)"

    else ->
        "${area.storedTiles} of ${area.plannedTiles} tiles · ${area.percent}%"
}
