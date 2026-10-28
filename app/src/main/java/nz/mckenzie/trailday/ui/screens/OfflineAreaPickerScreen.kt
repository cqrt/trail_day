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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.doc.whatDocHasIn
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.offline.DownloadService
import nz.mckenzie.trailday.offline.DownloadState
import nz.mckenzie.trailday.offline.OfflineAreaDraft
import nz.mckenzie.trailday.offline.OfflineChoice
import nz.mckenzie.trailday.offline.OfflinePickerMode
import nz.mckenzie.trailday.offline.downloadProgressWords
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
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val previewGeoJson by viewModel.previewGeoJson.collectAsStateWithLifecycle()
    val startBounds by viewModel.startBounds.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val docWorking by viewModel.docWorking.collectAsStateWithLifecycle()
    val docMessage by viewModel.docMessage.collectAsStateWithLifecycle()
    val topoMessage by viewModel.topoMessage.collectAsStateWithLifecycle()
    val docInventory by viewModel.docInventory.collectAsStateWithLifecycle()
    val docCounting by viewModel.docCounting.collectAsStateWithLifecycle()
    val otherTracksWorking by viewModel.otherTracksWorking.collectAsStateWithLifecycle()
    val otherTracksMessage by viewModel.otherTracksMessage.collectAsStateWithLifecycle()
    val otherTracksCount by viewModel.otherTracksCount.collectAsStateWithLifecycle()
    val otherTracksCounting by viewModel.otherTracksCounting.collectAsStateWithLifecycle()
    val download by DownloadState.current.collectAsStateWithLifecycle()

    // What a download in flight is doing, in the words the notification uses. One sentence, read here and
    // there, so the card and the notification cannot describe one download two ways.
    val live = download?.let { downloadProgressWords(it) }

    // A download outlives the screen that began it, so it is reported where a walker already looks for
    // things that are still happening - and the permission to report it is asked for here, where that is
    // about to matter.
    val context = LocalContext.current
    val notifications = rememberNotificationPermission()
    LaunchedEffect(download != null) {
        if (download != null) {
            notifications.ask()
            DownloadService.start(context)
        }
    }

    val imagery = viewModel.mode == OfflinePickerMode.IMAGERY
    val around = viewModel.mode == OfflinePickerMode.AROUND_TRACK
    val topography = viewModel.mode == OfflinePickerMode.TOPOGRAPHY
    // What this run keeps, as separate questions rather than one flag, because the packs are separate
    // caches: the imagery (a name, a zoom range, the tiles), what DOC publishes in the same box, and the
    // drawn Topographic map with the heights its shade comes from. Around a track is the first two.
    val keepsImagery = imagery || around
    val keepsDocData = viewModel.mode == OfflinePickerMode.DOC_DATA || around
    // Somebody else's tracks are the second publisher's material, and their own cache: kept in one act
    // that has nothing to do with DOC's, which is why this is not part of [keepsDocData].
    val keepsOtherTracks = viewModel.mode == OfflinePickerMode.OTHER_TRACKS

    // Which of the two ways this run is choosing ground, and the region it has chosen. Only the track pack
    // can choose a region, so every other run is a box whatever the state says.
    val choice by viewModel.choice.collectAsStateWithLifecycle()
    val region by viewModel.region.collectAsStateWithLifecycle()
    val regionsGeoJson by viewModel.regionsGeoJson.collectAsStateWithLifecycle()
    val pickedRegionGeoJson by viewModel.pickedRegionGeoJson.collectAsStateWithLifecycle()
    val regionMode = keepsOtherTracks && choice == OfflineChoice.REGION
    // How much of the screen the card covers, in the pixels the map camera wants: the country is framed
    // above it rather than behind it.
    val regionCardPaddingPx = with(LocalDensity.current) { 210.dp.roundToPx() }
    // A zoom range is what any pack of tiles is asked, and the imagery and the drawn map are both that.
    val keepsZooms = keepsImagery || topography
    // A name belongs to a pack that keeps **areas** - imagery, the drawn map, and somebody else's tracks -
    // because an area is a row the walker finds again later: "the Tararuas", "the Routeburn". DOC's material
    // is one cache with no rows, so there is nothing to name.
    val keepsAreas = keepsImagery || topography || keepsOtherTracks

    /**
     * The map the box is drawn on: **the pack's own, where it has one.**
     *
     * Choosing what to keep for a hillside while looking at a photograph of it is choosing blind - the two
     * maps do not agree about where the tracks are, and the drawn one is what is being kept. Everything
     * else is drawn on the imagery, which is the app's own default and a fine thing to draw a DOC box on.
     */
    val mapBasemap = when (viewModel.mode) {
        OfflinePickerMode.TOPOGRAPHY -> Basemap.LINZ_TOPO_LIDAR_HILLSHADE
        else -> Basemap.LINZ_AERIAL
    }

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
                            OfflinePickerMode.TOPOGRAPHY -> "Choose an area for the LINZ Topographic map"
                            OfflinePickerMode.AROUND_TRACK -> "Keep the ground around this track"
                            OfflinePickerMode.OTHER_TRACKS -> "Choose an area for other tracks"
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
            // The pack's own map, so a box drawn for the drawn map is drawn on the drawn map.
            BasemapView(
                basemap = mapBasemap,
                assetGeoJson = previewGeoJson,
                fitBounds = startBounds,
                // The card at the foot of this screen is the question, and the country has to fit above
                // it: framed edge to edge, Otago and Southland sit behind the card that names them.
                fitBoundsPaddingBottomPx = if (regionMode) regionCardPaddingPx else 0,
                onMapClick = { latitude, longitude, _ -> viewModel.tapAt(latitude, longitude) },
                // The sixteen regions, and the one tapped: drawn under everything, because on this screen
                // they are not a thing to read on the ground but the choosing of the ground itself.
                regionsGeoJson = regionsGeoJson,
                regionPickedGeoJson = pickedRegionGeoJson,
                modifier = Modifier.fillMaxSize()
            )

            AttributionStrip(
                basemap = mapBasemap,
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
                            around -> "Tap the map to change the box."

                            regionMode && region == null -> "Tap a region"
                            regionMode -> "Tap another region to change it"
                            draft.corners.size == 0 -> "Tap one corner of the area"
                            draft.corners.size == 1 -> "Now tap the opposite corner"
                            else -> "Tap again for a new box."
                        },
                        style = MaterialTheme.typography.titleSmall
                    )

                    // Two ways of choosing ground, and only one of them holding an answer at a time: a
                    // region names itself and needs no drawing, and a box covers the ground no region
                    // describes - the end of a beach, one valley.
                    if (keepsOtherTracks) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = choice == OfflineChoice.REGION,
                                onClick = { viewModel.setChoice(OfflineChoice.REGION) },
                                label = { Text("Region") }
                            )
                            FilterChip(
                                selected = choice == OfflineChoice.BOX,
                                onClick = { viewModel.setChoice(OfflineChoice.BOX) },
                                label = { Text("Box") }
                            )
                        }
                    }

                    // A name belongs to a pack that keeps areas, because an area is a row the walker finds
                    // again later: "the Tararuas", "the Routeburn". DOC's material is one cache with no
                    // rows, so there is nothing to name. A region names itself, so there is nothing to type.
                    if (keepsAreas && !regionMode) {
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = viewModel::setName,
                            label = { Text("Area name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // How deep to keep it, which every pack that is tiles needs asked - the imagery's
                    // tiles, and the drawn map's own.
                    if (keepsZooms) {
                        ZoomSlider(
                            label = "Shallowest level",
                            value = draft.minZoom,
                            deepest = viewModel.deepestZoom,
                            onChange = { viewModel.setZoomRange(it, draft.maxZoom) }
                        )
                        ZoomSlider(
                            label = "Deepest level",
                            value = draft.maxZoom,
                            deepest = viewModel.deepestZoom,
                            onChange = { viewModel.setZoomRange(draft.minZoom, it) }
                        )

                        Text(
                            text = if (draft.isComplete) {
                                "${draft.tileCount} ${if (topography) "map" else "aerial"} tiles, " +
                                    "roughly ${draft.estimatedSizeLabel} " +
                                    "· zoom ${draft.minZoom} to ${draft.maxZoom}"
                            } else {
                                "Tap two corners to see how many tiles that is."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // The imagery keeps this: the difference between zoom 16 and 17 is the difference
                        // between a minute and a quarter of an hour, and it is the one thing here a walker
                        // can be caught out by. The drawn map stops at 15 and has nothing to warn about.
                        if (keepsImagery) {
                            Text(
                                text = "Each deeper level multiplies the download.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (draft.isTooBig) {
                            Text(
                                text = "Too much for one go. Narrow the box or the zoom range.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        active?.let { area ->
                            // The area's own record, which outlives the live line below: it says what the
                            // area came to, where the live line says how it is going. While it is going the
                            // live line says it better, so this one stands aside.
                            if (area.isComplete) {
                                Text(
                                    text = "Downloaded ${area.sizeLabel}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else if (live == null) {
                                Text(
                                    text = "Downloading ${area.percent}%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        error?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // The drawn map's own two questions are answered above - the zoom range, and how many
                    // tiles it comes to - so there is nothing left to explain here but how it went.
                    if (topography) {
                        topoMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
                                "Everything DOC publishes here is kept and drawn on the map."
                            } else {
                                "Everything DOC publishes inside the box is downloaded and drawn on the map."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        docMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // What somebody else's tracks come to in the same box, asked before the button is
                    // tapped. **A count rather than a size**: this service will not say what a box weighs,
                    // and a figure the app made up would be worse than none.
                    if (keepsOtherTracks) {
                        // A region's own name, and what the service holds there: the count is asked when a
                        // region is tapped, so a walker reading this is reading about the region they chose.
                        if (regionMode) region?.let { chosen -> Text(chosen.name, style = MaterialTheme.typography.titleMedium) }
                        val count = otherTracksCount
                        val what = if (regionMode) region?.name ?: "this region" else "this box"
                        Text(
                            text = when {
                                otherTracksCounting -> "Asking the tracks service what is in $what..."

                                // No choice and no answer are different things, and a walker told to tap a
                                // region they have already tapped would tap it again.
                                regionMode && region == null -> "Tap a region to see what is inside it."

                                !regionMode && !draft.isComplete -> "Draw a box to see what tracks are inside it."

                                count == null ->
                                    "The tracks service could not be asked. Try again in a moment."

                                count == 0 && regionMode -> "There are no other tracks inside ${region?.name}."

                                count == 0 -> "There are no other tracks inside this box."

                                count == 1 -> "1 track inside $what."

                                else -> "$count tracks inside $what."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "The tracks councils, trusts and clubs publish inside $what are " +
                                "downloaded and drawn on the map.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        otherTracksMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // What a download in flight is doing, in the same words the notification uses. One line
                    // for every pack, in one place, so no mode can be the one that forgot to say.
                    live?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = viewModel::clearCorners,
                            enabled = (draft.corners.isNotEmpty() || region != null) &&
                                !working && !docWorking && !otherTracksWorking
                        ) { Text("Start over") }
                        Button(
                            onClick = viewModel::download,
                            enabled = when {
                                keepsImagery -> draft.isComplete &&
                                    !draft.isTooBig && !working && !docWorking

                                topography -> draft.isComplete && !draft.isTooBig && !working

                                // A region needs no corners, and a box needs both of them.
                                keepsOtherTracks -> (if (regionMode) region != null else draft.isComplete) &&
                                    !otherTracksWorking

                                else -> draft.isComplete && !docWorking
                            }
                        ) {
                            Text(
                                when {
                                    around && working -> "Keeping the ground…"
                                    around -> "Keep the ground"
                                    topography && working -> "Downloading the map…"
                                    topography -> "Download the map"
                                    keepsOtherTracks && otherTracksWorking -> "Downloading other tracks…"
                                    keepsOtherTracks && regionMode ->
                                        "Download ${region?.name ?: "region"}"
                                    keepsOtherTracks -> "Download other tracks"
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
private fun ZoomSlider(
    label: String,
    value: Int,
    /** The deepest level this run will fetch: a map that stops at zoom 15 is not asked for 16. */
    deepest: Int,
    onChange: (Int) -> Unit
) {
    Column {
        Text(
            text = "$label: zoom $value",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = OfflineAreaDraft.MIN_ZOOM.toFloat()..deepest.toFloat(),
            // One stop per whole level between the two ends, whatever they are.
            steps = (deepest - OfflineAreaDraft.MIN_ZOOM - 1).coerceAtLeast(0)
        )
    }
}
