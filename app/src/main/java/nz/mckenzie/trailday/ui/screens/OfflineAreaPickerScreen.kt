package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.doc.whatDocHasIn
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.offline.OfflineAreaDraft
import nz.mckenzie.trailday.offline.OfflinePickerMode
import nz.mckenzie.trailday.offline.formatBytes
import nz.mckenzie.trailday.viewmodel.OfflineAreaPickerViewModel
import kotlin.math.roundToInt

/**
 * Picks an offline area on the map: two taps for the corners, then a download.
 *
 * **One screen, two packs.** The offline imagery and DOC's tracks are independent caches, but they
 * are chosen the same way - a box on the map - so this is the same screen choosing for either, and
 * [OfflinePickerMode] decides what the download does and which questions are worth asking. A DOC area
 * is only the box; an imagery area also wants a name and a zoom range, and those are the only rows
 * that appear for it.
 *
 * Every imagery choice shows its cost immediately. That matters more than it sounds: the difference
 * between caching to zoom 16 and to zoom 17 is the difference between a minute and a quarter of an
 * hour on a rural connection, and the walker is the only one who knows whether they need to see
 * individual rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineAreaPickerScreen(
    viewModel: OfflineAreaPickerViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val previewGeoJson by viewModel.previewGeoJson.collectAsStateWithLifecycle()
    val startBounds by viewModel.startBounds.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val docWorking by viewModel.docWorking.collectAsStateWithLifecycle()
    val docMessage by viewModel.docMessage.collectAsStateWithLifecycle()
    val docInventory by viewModel.docInventory.collectAsStateWithLifecycle()
    val docCounting by viewModel.docCounting.collectAsStateWithLifecycle()

    val imagery = viewModel.mode == OfflinePickerMode.IMAGERY
    val around = viewModel.mode == OfflinePickerMode.AROUND_TRACK
    // What this run keeps: the imagery (a name, a zoom range, the tiles), and/or what DOC publishes in the
    // same box. Around a track is both, which is why they are two questions rather than one flag.
    val keepsImagery = viewModel.mode != OfflinePickerMode.DOC_DATA
    val keepsDocData = viewModel.mode != OfflinePickerMode.IMAGERY

    LaunchedEffect(saved) {
        if (saved) {
            // Consume before leaving, so a reused view model does not bounce the
            // walker straight back out next time.
            viewModel.consumeSaved()
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (viewModel.mode) {
                            OfflinePickerMode.IMAGERY -> "Choose an offline area"
                            OfflinePickerMode.DOC_DATA -> "Choose a DOC area"
                            OfflinePickerMode.AROUND_TRACK -> "Keep the ground around this track"
                        }
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(IconGlyph.BACK, contentDescription = "Back") } }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Aerial imagery, whichever basemap the walker reads their own maps on: it is what an
            // imagery area downloads, and it is a fine thing to draw a DOC area on besides.
            BasemapView(
                basemap = Basemap.LINZ_AERIAL,
                apiKey = apiKey,
                assetGeoJson = previewGeoJson,
                fitBounds = startBounds,
                onMapClick = { latitude, longitude, _ -> viewModel.tapAt(latitude, longitude) },
                modifier = Modifier.fillMaxSize()
            )

            AttributionStrip(
                basemap = Basemap.LINZ_AERIAL,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 6.dp)
            )

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 40.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when {
                            // The box is already drawn around the track, so the question is not about
                            // corners but about whether this is the ground the walker wants kept.
                            around -> "This is the ground ${draft.name} needs. Tap the map to draw a " +
                                "different box, or drag it to check this one."

                            draft.corners.size == 0 -> "Tap one corner of the area"
                            draft.corners.size == 1 -> "Now tap the opposite corner"
                            else -> "Tap again to start a new box, or drag the map to check it"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )

                    // A name and a zoom range are an imagery area's own questions. A DOC area is the
                    // box and nothing else, so the picker asks nothing else of it.
                    if (keepsImagery) {
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = viewModel::setName,
                            label = { Text("Area name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        ZoomSlider(
                            label = "Shallowest level",
                            value = draft.minZoom,
                            onChange = { viewModel.setZoomRange(it, draft.maxZoom) }
                        )
                        ZoomSlider(
                            label = "Deepest level",
                            value = draft.maxZoom,
                            onChange = { viewModel.setZoomRange(draft.minZoom, it) }
                        )

                        Text(
                            text = if (draft.isComplete) {
                                "${draft.tileCount} aerial tiles, roughly ${draft.estimatedSizeLabel} " +
                                    "· zoom ${draft.minZoom} to ${draft.maxZoom}"
                            } else {
                                "Tap two corners to see how many tiles that is."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Each deeper level multiplies the download. The shallow ones are " +
                                "nearly free, and are what let the map open on a zoomed-out view " +
                                "with no reception.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (draft.isTooBig) {
                            Text(
                                text = "That is too much to cache in one go. Narrow the box or the " +
                                    "zoom range.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        active?.let { area ->
                            Text(
                                text = if (area.isComplete) {
                                    "Downloaded ${area.sizeLabel}"
                                } else {
                                    "Downloading ${area.percent}%"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        error?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // What DOC publishes in the same box: the whole download for a DOC area, and the half
                    // that goes on top of the map for a track's ground.
                    if (keepsDocData) {
                        // What the box would take on, asked before the button is tapped: the size of the
                        // download is the one thing a phone in the hills cannot change its mind about.
                        // DOC's own counts, and the app's own rough arithmetic for the bytes.
                        val inventory = docInventory
                        Text(
                            text = when {
                                docCounting -> "Asking DOC what is in this box..."

                                inventory == null -> "Draw a box to see what DOC has inside it."

                                !inventory.complete ->
                                    "DOC could not be asked what is in this box. Try again in a moment."

                                inventory.totalRecords == 0 && inventory.pictures == 0 ->
                                    "DOC has nothing inside this box."

                                else ->
                                    "${whatDocHasIn(inventory)} inside this box, roughly " +
                                        "${formatBytes(inventory.estimatedBytes)}."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (around) {
                                "Everything DOC publishes here is kept too - the tracks around the " +
                                    "walk, the huts and campsites to stop at - and drawn on the map as " +
                                    "DOC's own layer."
                            } else {
                                "Everything DOC publishes inside the box is downloaded and drawn on " +
                                    "the map as DOC's own layer. Nothing else is fetched, and no LINZ " +
                                    "key is needed."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        docMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = viewModel::clearCorners,
                            enabled = draft.corners.isNotEmpty() && !working && !docWorking
                        ) { Text("Start over") }
                        Button(
                            onClick = viewModel::download,
                            enabled = if (keepsImagery) {
                                apiKey.isNotBlank() && draft.isComplete && !draft.isTooBig &&
                                    !working && !docWorking
                            } else {
                                draft.isComplete && !docWorking
                            }
                        ) {
                            Text(
                                when {
                                    around && working -> "Keeping the ground…"
                                    around -> "Keep the ground"
                                    !imagery && docWorking -> "Downloading DOC data…"
                                    !imagery -> "Download DOC data"
                                    else -> "Download area"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A zoom level, as a slider with the number spelled out. */
@Composable
private fun ZoomSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Text(
            text = "$label: zoom $value",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = OfflineAreaDraft.MIN_ZOOM.toFloat()..OfflineAreaDraft.MAX_ZOOM.toFloat(),
            // Twelve whole levels, so eleven gaps and ten intermediate stops.
            steps = OfflineAreaDraft.MAX_ZOOM - OfflineAreaDraft.MIN_ZOOM - 1
        )
    }
}
