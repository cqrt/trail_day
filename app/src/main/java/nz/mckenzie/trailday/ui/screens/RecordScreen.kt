package nz.mckenzie.trailday.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nz.mckenzie.trailday.ui.theme.TrailDayElevation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetPhrase
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.ElevationPhrase
import nz.mckenzie.trailday.domain.geo.ElevationProfile
import nz.mckenzie.trailday.domain.geo.TravelMode
import nz.mckenzie.trailday.domain.recording.RecordingStatus
import nz.mckenzie.trailday.domain.geo.FollowPoint
import nz.mckenzie.trailday.domain.geo.FollowProgress
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.formatDuration
import nz.mckenzie.trailday.ui.docBikeLine
import nz.mckenzie.trailday.ui.docRowDetail
import nz.mckenzie.trailday.ui.formatEstimate
import nz.mckenzie.trailday.ui.formatLengthOf
import nz.mckenzie.trailday.viewmodel.PendingFollow
import nz.mckenzie.trailday.viewmodel.RecordingViewModel

/**
 * Records a walk or a ride.
 *
 * Location is requested here (rather than at app launch) so the permission prompt
 * arrives with an obvious reason. Choosing a place turns the trip into one that
 * followed that line: the recorded line is drawn as it is made, and finishing saves
 * the trip with its soft link to the place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(viewModel: RecordingViewModel, onOpenTab: (Tab) -> Unit = {}) {
    val state by viewModel.tracking.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
    val geoJson by viewModel.recordedGeoJson.collectAsStateWithLifecycle()
    val targetGeoJson by viewModel.targetGeoJson.collectAsStateWithLifecycle()
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
    val follow by viewModel.follow.collectAsStateWithLifecycle()
    val heading by viewModel.heading.collectAsStateWithLifecycle()
    val drift by viewModel.drift.collectAsStateWithLifecycle()
    val assetName by viewModel.selectedTrackName.collectAsStateWithLifecycle()
    val recordingTargets by viewModel.recordingTargets.collectAsStateWithLifecycle()
    val pendingTrackName by viewModel.pendingTrackName.collectAsStateWithLifecycle()

    var permissionGranted by remember { mutableStateOf(viewModel.hasLocationPermission()) }
    var pickingTrack by remember { mutableStateOf(false) }
    var assetNameDraft by remember { mutableStateOf("") }
    var assetKindDraft by remember { mutableStateOf(AssetKind.TRACK) }

    // A new dialog starts on the common case: a walk. The last saved track's type must not leak into
    // the next one, which is a different place.
    LaunchedEffect(pendingTrackName) {
        if (pendingTrackName != null) assetKindDraft = AssetKind.TRACK
    }

    // The kind of recording being set up: following a line, or a new line of the walker's own. Not held
    // while a pass is being driven - by then it is a fact, not a choice.
    var followingMode by rememberSaveable { mutableStateOf(false) }
    var docQuery by rememberSaveable { mutableStateOf("") }
    val docChoices by viewModel.docChoices.collectAsStateWithLifecycle()
    val docTracksHeld by viewModel.docTracksHeld.collectAsStateWithLifecycle()

    // DOC's lines are read when the chooser opens, not on every visit to the screen.
    LaunchedEffect(pickingTrack) {
        if (pickingTrack) viewModel.refreshDocTracks()
    }

    // A line asked for somewhere else - the map's card, the places list: the recorder takes it as the line to
    // follow, so the walk starts from the screen that asks about walking.
    LaunchedEffect(Unit) {
        PendingFollow.wanted.collect { wanted ->
            if (wanted != null) {
                viewModel.selectDoc(wanted)
                followingMode = true
                PendingFollow.take()
            }
        }
    }

    // The picker opens fully rather than at half height. Half a sheet is a third of the
    // places hidden below the fold, and the row that matters - the one just recorded, or
    // "no place" - is as likely as any other to be the hidden one.
    val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Finish asks what to call the line; the suggested name arrives with the prompt.
    LaunchedEffect(pendingTrackName) {
        pendingTrackName?.let { assetNameDraft = it }
    }

    // The pass that has just been saved is on the card while the walker is here to see it -
    // that is what stops Save looking like it wiped the work - and is given up when they leave:
    // another tab, back to the map, or the app put down.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenLeft() }
    DisposableEffect(Unit) {
        onDispose { viewModel.onScreenLeft() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // **Only the answer it was asked about is acted on.** The same launcher asks for two different
        // things at two different moments - location to record, and the notification a recording puts in the
        // shade - so a result that does not mention location is not a refusal of it.
        results[Manifest.permission.ACCESS_FINE_LOCATION]?.let { granted ->
            permissionGranted = granted
            if (!granted) viewModel.onPermissionDenied()
        }
    }

    // Asked when a walk is started, not on the way in: the notification is what says the app is still
    // tracking, and that is the moment it starts to matter.
    val notifications = rememberNotificationPermission()
    val context = LocalContext.current

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
                // The line being followed, drawn wide over the work: while a walk is being followed it is
                // the line the screen is about, and the position marker sits on it rather than under it.
                targetGeoJson = targetGeoJson,
                // Recording starts where the walker is, not on a view of the country.
                fitBounds = initialFrame,
                // While a pass is being driven the map keeps the phone in the middle, so the
                // walker can see the line they are on without touching the screen. A drag
                // takes the map off them - see the follow control - and nothing else moves it.
                follow = followPhone,
                onFollowBroken = viewModel::onMapPanned,
                modifier = Modifier.fillMaxSize()
            )

            // Following the phone, as the mock screens draw it: a white pill at the top right while a pass is
            // being driven. Paused, the map deliberately does not follow - the walker is stopped, with both
            // hands free and the map theirs to read - so the pill is not offered while nothing moves.
            if (state.status == RecordingStatus.RECORDING) {
                FollowPhonePill(
                    following = following,
                    onToggle = { viewModel.setFollowing(!following) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = 12.dp)
                )
            }

            // What the pass has done, at the top of the map: the mock screens' card, with the distance as the
            // one big figure and the rest divided into cells under it.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 68.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PassCard(
                    summary = summary,
                    elapsedMs = elapsedMs,
                    elapsing = state.status == RecordingStatus.RECORDING,
                    follow = follow,
                    drift = drift,
                    heading = heading,
                    passElevation = passElevation,
                    followMode = followingMode,
                    targetName = assetName,
                    onFollowMode = { following ->
                        followingMode = following
                        // A new line is a line of the walker's own: whatever was chosen before is let go,
                        // so what is recorded is not quietly linked to a track nobody is following.
                        if (!following) viewModel.selectTrack(null) else if (assetName == null) pickingTrack = true
                    },
                    onChooseTarget = { pickingTrack = true }
                )

                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // A walker who expects a notification and sees none should not be left wondering whether the
                // app is still recording. Said while a pass is being driven, and only when the app has been
                // told it may not post - and tappable, because the app cannot ask again once it has been
                // told no, so the only way back is its own notification settings.
                if (state.status == RecordingStatus.RECORDING && !notifications.allowed) {
                    Text(
                        text = "Notifications are off, so this walk will not show in the shade. " +
                            "Tap to turn them on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            context.startActivity(notificationSettingsIntent(context))
                        }
                    )
                }
            }

            // Which place this pass is for, and the way to change it: a pill rather than a line inside the
            // card, because it is a choice rather than a figure.
            if (state.status != null) {
                TargetPill(
                    name = assetName,
                    onClick = { pickingTrack = true },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 104.dp)
                )
            }

            AttributionStrip(
                basemap = basemap,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 78.dp)
            )

            // The actions, as the mock screens float them over the map: one big pill and one small, white on
            // the imagery, with the shadow saying they are above it.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!permissionGranted) {
                    MapPill(
                        label = "Allow location to record",
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            )
                        },
                        modifier = Modifier.weight(1.6f)
                    )
                } else {
                    when (state.status) {
                        RecordingStatus.RECORDING -> {
                            MapPill(label = "Pause", onClick = viewModel::pause, modifier = Modifier.weight(1.6f))
                            MapPill(label = "Finish", onClick = viewModel::finish, modifier = Modifier.weight(1f))
                        }

                        RecordingStatus.PAUSED -> {
                            MapPill(label = "Resume", onClick = viewModel::resume, modifier = Modifier.weight(1.6f))
                            MapPill(label = "Finish", onClick = viewModel::finish, modifier = Modifier.weight(1f))
                        }

                        else -> MapPill(
                            label = "Start recording",
                            onClick = {
                                // Asked here, and the walk goes ahead either way: a walker who says no to
                                // the notification still gets their walk, and one who says yes gets told
                                // the app is still tracking while their phone is in a pocket.
                                notifications.ask()
                                if (followingMode && assetName == null) pickingTrack = true else viewModel.start()
                            },
                            enabled = !followingMode || assetName != null,
                            modifier = Modifier.weight(1f)
                        )
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
                        text = "Choose a track to follow",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { pickingTrack = false }) { Text("Cancel") }
                }

                SearchField(
                    value = docQuery,
                    onValueChange = {
                        docQuery = it
                        viewModel.setDocQuery(it)
                    },
                    placeholder = "Find a track",
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                // The rows scroll inside a window of their own rather than running off the bottom of the
                // sheet: a season is dozens of places, and the sheet's content does not scroll by itself.
                // Your places first, then DOC's, because the walker's own work comes first.
                LazyColumn(modifier = Modifier.heightIn(max = PICKER_LIST_MAX_HEIGHT)) {
                    item { GroupLabel("Your tracks and places") }
                    if (recordingTargets.isEmpty()) {
                        item {
                            Text(
                                text = "No track or place to follow yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }
                    }
                    itemsIndexed(recordingTargets, key = { _, item -> item.id }) { _, item ->
                        ListItem(
                            modifier = Modifier.clickable {
                                viewModel.selectTrack(item.id)
                                followingMode = true
                                pickingTrack = false
                            },
                            headlineContent = { Text(item.name) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        AssetPhrase.kind(AssetKind.fromStorage(item.kind)),
                                        formatLengthOf(AssetShape.fromStorage(item.shape), item.lengthM)
                                            .takeIf { it.isNotEmpty() }
                                    ).joinToString(" \u00b7 ")
                                )
                            }
                        )
                    }

                    item { GroupLabel("DOC's tracks near you") }
                    if (docChoices.isEmpty()) {
                        item {
                            Text(
                                text = if (docTracksHeld) "Nothing nearby" else "Nothing from DOC kept yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }
                    }
                    items(docChoices, key = { it.item.sourceRef }) { choice ->
                        ListItem(
                            modifier = Modifier.clickable {
                                viewModel.selectDoc(choice.item)
                                followingMode = true
                                pickingTrack = false
                            },
                            headlineContent = { Text(choice.item.name) },
                            supportingContent = {
                                Column {
                                    Text(docRowDetail(choice.item, choice.distanceM))
                                    choice.bike?.let { bike ->
                                        Text(
                                            text = docBikeLine(bike),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            // Whose line it is, in one word: nothing here is renamed or deleted.
                            trailingContent = {
                                Text("DOC", style = MaterialTheme.typography.labelSmall)
                            }
                        )
                    }
                }
                if (recordingTargets.isNotEmpty()) HorizontalDivider()
                // Recording without choosing a place stays possible: the line that was
                // recorded is the evidence, and which place it belongs to can be settled later.
                // The way out of the list, on a strip of its own: the same sheet, but not one more row of
                // tracks - a row that looked like the list was a row nobody read as a choice.
                HorizontalDivider()
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            viewModel.selectTrack(null)
                            followingMode = false
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
            title = { Text("Name this track") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Saved as a place. Cancelling keeps recording.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = assetNameDraft,
                        onValueChange = { assetNameDraft = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    ChoiceRow(
                        label = "What kind of track",
                        choices = listOf(AssetKind.TRACK, AssetKind.BIKE_TRACK),
                        selected = assetKindDraft,
                        onChoose = { assetKindDraft = it },
                        text = AssetPhrase::kind
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmFinish(assetNameDraft, assetKindDraft) }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelFinish() }) { Text("Cancel") }
            }
        )
    }
}

/** A heading over a group of rows in the chooser: your places, DOC's tracks. */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 2.dp)
    )
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

/**
 * The pass, as the mock screens put it at the top of the recorder: **the distance as one big figure**, and the
 * rest divided into cells under it - the time, the pace, and what the walker is following.
 *
 * The time is the pass's own clock and the pace is its own arithmetic (minutes for a kilometre); the follow
 * readout is only there when there is a line or a point to follow, and it says the off-distance once, by the
 * quiet word when the walker has properly drifted and by the row when they have not.
 */
@Composable
private fun PassCard(
    summary: RecordingViewModel.PassSummary,
    elapsedMs: Long,
    elapsing: Boolean,
    follow: FollowProgress?,
    drift: Double?,
    heading: FollowPoint?,
    passElevation: ElevationProfile?,
    followMode: Boolean,
    targetName: String?,
    onFollowMode: (Boolean) -> Unit,
    onChooseTarget: () -> Unit
) {
    // The one card over the recording map, lifted off the imagery like the map's other floating things.
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = TrailDayElevation.floating),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Text(
                text = when (summary.status) {
                    RecordingStatus.RECORDING -> "RECORDING"
                    RecordingStatus.PAUSED -> "PAUSED"
                    RecordingStatus.FINISHED -> "FINISHED"
                    null -> "READY TO RECORD"
                },
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Nothing being recorded yet: what kind of recording is about to start, then - if it is a
            // line to follow - which line. Two words rather than a paragraph, as everywhere in the app.
            if (summary.status == null) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChoiceChip(
                        selected = followMode,
                        label = "Follow a track",
                        onClick = { onFollowMode(true) }
                    )
                    ChoiceChip(
                        selected = !followMode,
                        label = "Record New Track",
                        onClick = { onFollowMode(false) }
                    )
                }
                if (followMode) {
                    Text(
                        text = targetName ?: "Choose a track",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (targetName == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                            .clickable(onClick = onChooseTarget)
                    )
                }
            }

            // The distance as the one big figure, with its unit beside it rather than inside the number.
            val distance = formatDistance(summary.distanceM).split(" ", limit = 2)
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(text = distance.first(), style = MaterialTheme.typography.displaySmall)
                distance.getOrNull(1)?.let { unit ->
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            FigureCells(
                figures = listOf(
                    Figure(label = "Time", value = formatDuration(elapsedMs)),
                    Figure(label = "Pace", value = paceOf(summary.distanceM, elapsedMs))
                )
            )

            // The quiet word and the readout, when something is being followed.
            if (drift != null || follow != null || heading != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    drift?.let { offM ->
                        Text(
                            text = "You are ${formatDistance(offM)} off the track.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    follow?.let { progress ->
                        val offPart = when {
                            drift != null -> ""
                            progress.offM <= ALONGSIDE_M -> "On the track \u00b7 "
                            else -> "${formatDistance(progress.offM)} off the track \u00b7 "
                        }
                        Text(
                            text = offPart + buildString {
                                append("${formatDistance(progress.alongM)} of ")
                                append(formatDistance(progress.totalM))
                                val toGo = progress.toGoSeconds(TravelMode.WALKING)
                                if (toGo >= ONE_MINUTE_S) append(" \u00b7 about ${formatEstimate(toGo)} to go")
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    heading?.let { point ->
                        Text(
                            text = "${formatDistance(point.distanceM)} to the ${point.compass} \u00b7 " +
                                "about ${formatEstimate(point.toGoSeconds())} on foot",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // The pass just saved, as the ground it climbed: the range in words and the shape under it. A pass
            // with no heights says nothing at all.
            if (passElevation != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ElevationPhrase.range(passElevation)?.let { range ->
                        Text(text = range, style = MaterialTheme.typography.bodySmall)
                    }
                    ElevationChart(
                        profile = passElevation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                    )
                }
            }

            summary.accuracyM?.let { accuracy ->
                Text(
                    text = "Accuracy \u00b1${accuracy.toInt()} m" +
                        if (summary.rejectedFixes > 0) {
                            " \u00b7 ${summary.rejectedFixes} fixes rejected"
                        } else {
                            ""
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
            }
        }
    }
}

/** How many minutes a kilometre took, or nothing while there is no distance to divide by. */
private fun paceOf(distanceM: Double, elapsedMs: Long): String {
    if (distanceM < 50.0 || elapsedMs <= 0L) return "\u2013"
    val minutesPerKm = (elapsedMs / 60_000.0) / (distanceM / 1000.0)
    return String.format(java.util.Locale.US, "%.1f min/km", minutesPerKm)
}

/** "Following" or "Follow me", the map's own camera control, as the mock screens draw it: a white pill. */
@Composable
private fun FollowPhonePill(following: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = TrailDayElevation.floating,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIcon(
                glyph = IconGlyph.LOCATE,
                dimension = 18.dp,
                tint = if (following) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = if (following) "Following" else "Follow me",
                style = MaterialTheme.typography.labelLarge,
                color = if (following) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/** Which place this pass is for, and the way to change it. */
@Composable
private fun TargetPill(name: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = TrailDayElevation.floating,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIcon(glyph = IconGlyph.MAP, dimension = 18.dp, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = name?.let { "Following: $it" } ?: "Choose a place to follow",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/** One action over the map: a white pill, as the mock screens float Pause and Finish. */
@Composable
private fun MapPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = TrailDayElevation.floating,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
/** Within this of the line is on it, as far as a phone's own accuracy can say. */
private const val ALONGSIDE_M = 30.0

/** Under a minute is not worth a "to go" on a card. In seconds, because that is what the estimate is in. */
private const val ONE_MINUTE_S = 60L
