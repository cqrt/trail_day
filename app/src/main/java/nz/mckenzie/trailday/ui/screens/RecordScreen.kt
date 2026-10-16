package nz.mckenzie.trailday.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import nz.mckenzie.trailday.domain.geo.ElevationPhrase
import nz.mckenzie.trailday.domain.geo.ElevationProfile
import nz.mckenzie.trailday.domain.recording.RecordingStatus
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.formatDuration
import nz.mckenzie.trailday.viewmodel.RecordingViewModel

/**
 * Records a walk or a ride.
 *
 * Location is requested here (rather than at app launch) so the permission prompt
 * arrives with an obvious reason. Choosing a place turns the recording into one that
 * followed that line: the recorded line is drawn as it is made, and finishing saves
 * the recording with its soft link to the place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(viewModel: RecordingViewModel, onOpenTab: (Tab) -> Unit = {}) {
    val state by viewModel.tracking.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
    val geoJson by viewModel.recordedGeoJson.collectAsStateWithLifecycle()
    val initialFrame by viewModel.initialFrame.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    // The height of the pass just saved, from the fixes it stored. Null while nothing is saved, or
    // when the fixes carried no height.
    val passElevation = remember(finished) {
        finished?.recorded?.let { ElevationProfile.of(it) }
    }
    val following by viewModel.following.collectAsStateWithLifecycle()
    val followPhone by viewModel.followPhone.collectAsStateWithLifecycle()
    val assetName by viewModel.selectedTrackName.collectAsStateWithLifecycle()
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    val pendingTrackName by viewModel.pendingTrackName.collectAsStateWithLifecycle()

    var permissionGranted by remember { mutableStateOf(viewModel.hasLocationPermission()) }
    var pickingTrack by remember { mutableStateOf(false) }
    var assetNameDraft by remember { mutableStateOf("") }

    // The picker opens fully rather than at half height. Half a sheet is a third of the
    // places hidden below the fold, and the row that matters - the one just recorded, or
    // "no place" - is as likely as any other to be the hidden one.
    val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Finish asks what to call the line; the suggested name arrives with the prompt.
    LaunchedEffect(pendingTrackName) {
        pendingTrackName?.let { assetNameDraft = it }
    }

    // The pass that has just been saved is on the card while the operator is here to see it -
    // that is what stops Save looking like it wiped the work - and is given up when they leave:
    // another tab, back to the map, or the app put down.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenLeft() }
    DisposableEffect(Unit) {
        onDispose { viewModel.onScreenLeft() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!permissionGranted) viewModel.onPermissionDenied()
    }

    // Tick once a second so the elapsed time moves while recording.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.status) {
        while (state.status == RecordingStatus.RECORDING) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    // A saved pass has an end, so its clock stops: the finish time is what the card reads,
    // and the tick above only moves the clock of a pass that is still being driven.
    val elapsedMs = summary.startedAtEpochMs
        ?.let { started -> ((summary.endedAtEpochMs ?: nowMs) - started).coerceAtLeast(0L) }
        ?: 0L

    Scaffold(
        topBar = { TopAppBar(title = { Text("Record") }) },
        // No back arrow: recording is a tab, and the bar below is how you leave it.
        bottomBar = { TrailDayNavBar(current = Tab.RECORD, onSelect = onOpenTab) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BasemapView(
                basemap = basemap,
                apiKey = apiKey,
                assetGeoJson = geoJson,
                // Recording starts where the operator is, not on a view of the country.
                fitBounds = initialFrame,
                // While a pass is being driven the map keeps the phone in the middle, so the
                // operator can see the line they are on without touching the screen. A drag
                // takes the map off them - see the follow control - and nothing else moves it.
                follow = followPhone,
                onFollowBroken = viewModel::onMapPanned,
                modifier = Modifier.fillMaxSize()
            )

            // Following, and whether it is on. The map follows a pass by itself, so the
            // question the operator actually has is the one this answers at a glance: is it
            // following me or not?
            //
            // Only while the pass is being driven. Paused, the map deliberately does not follow
            // - the operator is stopped, with both hands free and the map theirs to read - so a
            // control that said "Following the phone" while nothing moved was telling them
            // something untrue. Resume turns following back on by itself.
            if (state.status == RecordingStatus.RECORDING) {
                FilledTonalIconButton(
                    onClick = { viewModel.setFollowing(!following) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp, top = 16.dp)
                ) {
                    AppIcon(
                        glyph = IconGlyph.LOCATE,
                        tint = if (following) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        contentDescription = if (following) {
                            "Following the phone"
                        } else {
                            "Follow the phone"
                        }
                    )
                }
            }

            AttributionStrip(
                basemap = basemap,
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
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when (summary.status) {
                            RecordingStatus.RECORDING -> "Recording"
                            RecordingStatus.PAUSED -> "Paused"
                            RecordingStatus.FINISHED -> "Finished"
                            null -> "Ready to record"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    // The pass being driven, or - once Save has been pressed - the one just
                    // saved: its own distance, its own time and its own points.
                    Text(
                        text = "${formatDistance(summary.distanceM)} \u00b7 " +
                            "${formatDuration(elapsedMs)} \u00b7 ${summary.pointCount} " +
                            (if (summary.pointCount == 1) "point" else "points"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    summary.accuracyM?.let { accuracy ->
                        Text(
                            text = "Accuracy \u00b1${accuracy.toInt()} m" +
                                if (summary.rejectedFixes > 0) {
                                    " \u00b7 ${summary.rejectedFixes} fixes rejected"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // The pass just saved, as the ground it climbed: the range in words and the
                    // shape under it. A pass with no heights says nothing at all.
                    ElevationPhrase.range(passElevation)?.let { range ->
                        Text(text = range, style = MaterialTheme.typography.bodySmall)
                    }
                    passElevation?.let { profile ->
                        ElevationChart(
                            profile = profile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                        )
                    }

                    message?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }

                    TextButton(onClick = { pickingTrack = true }) {
                        Text(assetName?.let { "Following: $it" } ?: "Choose a place to follow")
                    }

                    if (!permissionGranted) {
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Allow location to record")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (state.status) {
                                RecordingStatus.RECORDING -> {
                                    OutlinedButton(onClick = viewModel::pause) { Text("Pause") }
                                    Button(onClick = viewModel::finish) { Text("Finish") }
                                }

                                RecordingStatus.PAUSED -> {
                                    Button(onClick = viewModel::resume) { Text("Resume") }
                                    OutlinedButton(onClick = viewModel::finish) { Text("Finish") }
                                }

                                else -> {
                                    Button(onClick = viewModel::start) { Text("Start recording") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickingTrack) {
        ModalBottomSheet(
            onDismissRequest = { pickingTrack = false },
            sheetState = pickerSheetState
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                // The picker asks a question, so it takes an answer. Cancel is the answer that
                // means "never mind", and it sits with the question rather than behind a swipe.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Which place are you following?",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { pickingTrack = false }) { Text("Cancel") }
                }
                if (assets.isEmpty()) {
                    Text(
                        text = "Nothing planned yet. You can still record a line.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
                // The rows scroll inside a window of their own rather than running off the
                // bottom of the sheet: a season is dozens of places, and the sheet's content
                // does not scroll by itself, so everything past the screen edge - a track
                // recorded this morning included - could not be reached at all.
                LazyColumn(modifier = Modifier.heightIn(max = PICKER_LIST_MAX_HEIGHT)) {
                    itemsIndexed(assets, key = { _, item -> item.id }) { index, item ->
                        if (index > 0) HorizontalDivider()
                        ListItem(
                            modifier = Modifier.clickable {
                                viewModel.selectTrack(item.id)
                                pickingTrack = false
                            },
                            headlineContent = { Text(item.name) }
                        )
                    }
                }
                if (assets.isNotEmpty()) HorizontalDivider()
                // Recording without choosing a place stays possible: the line that was
                // recorded is the evidence, and which place it belongs to can be settled later.
                ListItem(
                    modifier = Modifier.clickable {
                        viewModel.selectTrack(null)
                        pickingTrack = false
                    },
                    headlineContent = { Text("Just record, no place") },
                    leadingContent = { AppIcon(IconGlyph.RECORD) }
                )
            }
        }
    }

    // Finishing a line that is not already a track makes one, so it needs a name.
    if (pendingTrackName != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelFinish() },
            title = { Text("Name this line") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The line you recorded is saved as an asset, so it appears on " +
                            "the Assets page. Cancelling leaves the recording going, so the line " +
                            "can be named whenever you finish.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = assetNameDraft,
                        onValueChange = { assetNameDraft = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmFinish(assetNameDraft) }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelFinish() }) { Text("Cancel") }
            }
        )
    }
}

/**
 * How tall the picker's rows may get before they scroll.
 *
 * About seven rows - the height the sheet happened to come to before - so the list looks
 * the same as it always did, and the places below it are reached by scrolling the rows
 * rather than lost off the bottom of the screen. The title and "Just record, no place"
 * stay outside it, because those are the two things that must not need finding.
 */
private val PICKER_LIST_MAX_HEIGHT = 400.dp
