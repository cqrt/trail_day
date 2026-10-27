package nz.mckenzie.trailday

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.domain.settings.ThemeChoice
import nz.mckenzie.trailday.ui.screens.AddSpotScreen
import nz.mckenzie.trailday.ui.screens.DocBrowserScreen
import nz.mckenzie.trailday.ui.screens.DocDetailScreen
import nz.mckenzie.trailday.ui.screens.MapScreen
import nz.mckenzie.trailday.ui.screens.OfflineAreaPickerScreen
import nz.mckenzie.trailday.ui.screens.OfflineScreen
import nz.mckenzie.trailday.ui.screens.RecordScreen
import nz.mckenzie.trailday.ui.screens.RecordingDetailScreen
import nz.mckenzie.trailday.ui.screens.RecordingsScreen
import nz.mckenzie.trailday.ui.screens.SettingsScreen
import nz.mckenzie.trailday.ui.screens.Tab
import nz.mckenzie.trailday.ui.screens.AssetDetailScreen
import nz.mckenzie.trailday.ui.screens.AssetEditScreen
import nz.mckenzie.trailday.ui.screens.AssetListScreen
import nz.mckenzie.trailday.ui.screens.OtherTrackScreen
import nz.mckenzie.trailday.ui.screens.OtherTracksBrowserScreen
import nz.mckenzie.trailday.ui.theme.TrailDayTheme
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.viewmodel.AddSpotViewModel
import nz.mckenzie.trailday.viewmodel.DocBrowserViewModel
import nz.mckenzie.trailday.viewmodel.DocDetailViewModel
import nz.mckenzie.trailday.viewmodel.MapFocus
import nz.mckenzie.trailday.viewmodel.MapViewModel
import nz.mckenzie.trailday.viewmodel.OfflineAreaPickerViewModel
import nz.mckenzie.trailday.viewmodel.PendingFollow
import nz.mckenzie.trailday.viewmodel.PendingMapFocus
import nz.mckenzie.trailday.viewmodel.OfflineViewModel
import nz.mckenzie.trailday.viewmodel.OtherTrackViewModel
import nz.mckenzie.trailday.viewmodel.OtherTracksBrowserViewModel
import nz.mckenzie.trailday.viewmodel.RecordingDetailViewModel
import nz.mckenzie.trailday.viewmodel.RecordingsViewModel
import nz.mckenzie.trailday.viewmodel.RecordingViewModel
import nz.mckenzie.trailday.viewmodel.SettingsViewModel
import nz.mckenzie.trailday.viewmodel.UpdateViewModel
import nz.mckenzie.trailday.viewmodel.AssetDetailViewModel
import nz.mckenzie.trailday.viewmodel.AssetListViewModel
import nz.mckenzie.trailday.offline.OfflinePickerMode
import nz.mckenzie.trailday.web.WebEditorService
import nz.mckenzie.trailday.web.WebEditorState

/** Destinations for now; swap for a NavHost when routes need arguments. */
private enum class Destination { MAP, ASSETS, ADD_SPOT, RECORD, OFFLINE, OFFLINE_PICKER, OFFLINE_DOC_PICKER, OFFLINE_OTHER_TRACKS_PICKER, OFFLINE_TOPO_PICKER, OFFLINE_PICKER_AROUND, ASSET_DETAIL, ASSET_EDIT, RECORDINGS, RECORDING_DETAIL, SETTINGS, DOC, DOC_DETAIL, OTHER_TRACK_DETAIL, OTHER_TRACKS_BROWSER }

class MainActivity : ComponentActivity() {

    /**
     * A screen asked for by whatever started the activity - today, a tap on a backup or
     * update notification. Held outside the composition so `onNewIntent` can set it too.
     */
    private val requestedDestination = mutableStateOf<Destination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedDestination.value = destinationFrom(intent)
        setContent {
            val settings = remember { SettingsRepository(applicationContext) }
            val themeChoice by settings.themeChoice
                .collectAsStateWithLifecycle(initialValue = ThemeChoice.DEFAULT)
            val darkTheme = themeChoice.isDark(isSystemInDarkTheme())

            // The bars at the edges are the app's own background showing through, so the icons over
            // them have to follow the theme the walker chose rather than the phone's own setting:
            // choosing Light on a phone set to dark would otherwise leave pale icons on a pale bar.
            // Asked for again whenever the answer changes, because this is the same call the launch
            // above makes - with what the setting says rather than with what Android guessed.
            LaunchedEffect(darkTheme) {
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            TrailDayTheme(darkTheme = darkTheme) {
                var destination by rememberSaveable { mutableStateOf(Destination.MAP) }
                var selectedAssetId by rememberSaveable { mutableStateOf<Long?>(null) }
                var selectedSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
                var selectedDocItem by remember { mutableStateOf<DocItem?>(null) }
                var selectedDocBike by remember { mutableStateOf<DocItem?>(null) }
                var docDetailOrigin by remember { mutableStateOf(Destination.MAP) }

                /**
                 * Somebody else's track, on its way to its own page.
                 *
                 * Held rather than looked up again, because everything the page shows is in the row that
                 * was tapped - see [nz.mckenzie.trailday.viewmodel.OtherTrackViewModel] - so the page has
                 * nothing to ask the cache for and works whether or not the area is still held.
                 */
                var selectedOtherTrack by remember { mutableStateOf<OtherTrack?>(null) }
    // The place a page sent the recorder here to follow: chosen on the page, and read by the recorder's own
    // view model for as long as it is up.
    var recordTargetId by rememberSaveable { mutableStateOf<Long?>(null) }

                // Bumped on every visit to the add-a-spot screen so it gets its own view
                // model: a shared one would carry the previous visit's draft (and
                // its "just saved" signal) into the next one.
                var addSpotVisit by rememberSaveable { mutableStateOf(0) }

                // The same for the offline area picker, and for the same reason: a shared view model opened
                // the next area on the last one's name, corners and zoom range, so a walker downloading a
                // second area found the first one's answers already filled in.
                var offlinePickerVisit by rememberSaveable { mutableStateOf(0) }

                // System back returns the way the walker came, and never leaves the app.
                BackHandler(enabled = destination != Destination.MAP) {
                    destination = if (destination == Destination.DOC_DETAIL) {
                        docDetailOrigin
                    } else {
                        Destination.MAP
                    }
                }

                // A reminder tap asks for the track list. Done here rather than in the
                // initial value so it also works when the app was already open.
                LaunchedEffect(requestedDestination.value) {
                    requestedDestination.value?.let { wanted ->
                        destination = wanted
                        requestedDestination.value = null
                    }
                }

                // The editor serves itself from the moment the app is opened, unless the walker
                // has turned it off - which is what "on by default" has to mean for a service that
                // Android only lets an app on screen start. Read here rather than in the
                // application, where the same code would run for a scheduled job that started the
                // process with no screen behind it and no right to start a service at all. Left
                // alone when a run is already going, because starting again would build a new run
                // and hand the walker a new address under a page they are already reading.
                LaunchedEffect(Unit) {
                    if (settings.webEditorEnabled.first() && !WebEditorState.isOn) {
                        WebEditorService.start(applicationContext)
                    }
                }

                when (destination) {
                    Destination.MAP -> {
                        val mapViewModel: MapViewModel = viewModel(
                            factory = MapViewModel.factory(applicationContext)
                        )
                        MapScreen(
                            viewModel = mapViewModel,
                            onOpenTab = { destination = destinationOf(it) },
                            onOpenSettings = { destination = Destination.SETTINGS },
                            onOpenAsset = { assetId ->
                                selectedAssetId = assetId
                                destination = Destination.ASSET_DETAIL
                            },
                            onOpenDocDetail = { item, bike ->
                                selectedDocItem = item
                                selectedDocBike = bike
                                docDetailOrigin = Destination.MAP
                                destination = Destination.DOC_DETAIL
                            },
                            onOpenOtherTrack = { track ->
                                selectedOtherTrack = track
                                destination = Destination.OTHER_TRACK_DETAIL
                            }
                        )
                    }

                    Destination.ASSETS -> {
                        val assetViewModel: AssetListViewModel = viewModel(
                            factory = AssetListViewModel.factory(applicationContext)
                        )
                        AssetListScreen(
                            viewModel = assetViewModel,
                            onOpenTab = { destination = destinationOf(it) },
                            onOpenAsset = { assetId ->
                                selectedAssetId = assetId
                                destination = Destination.ASSET_DETAIL
                            },
                            onShowOnMap = { place ->
                                PendingMapFocus.offer(MapFocus.Place(place.id))
                                destination = Destination.MAP
                            },
                            onOpenDocDetail = { item, bike ->
                                selectedDocItem = item
                                selectedDocBike = bike
                                docDetailOrigin = Destination.ASSETS
                                destination = Destination.DOC_DETAIL
                            },
                            onAddSpot = {
                                addSpotVisit++
                                destination = Destination.ADD_SPOT
                            },
                            onOpenDoc = { destination = Destination.DOC },
                            onOpenOtherTracks = { destination = Destination.OTHER_TRACKS_BROWSER },
                            onOpenSettings = { destination = Destination.SETTINGS }
                        )
                    }

                    Destination.DOC -> {
                        val docViewModel: DocBrowserViewModel = viewModel(
                            factory = DocBrowserViewModel.factory(applicationContext)
                        )
                        DocBrowserScreen(
                            viewModel = docViewModel,
                            onBack = { destination = Destination.ASSETS },
                            onOpenDoc = { item, bike ->
                                selectedDocItem = item
                                selectedDocBike = bike
                                docDetailOrigin = Destination.DOC
                                destination = Destination.DOC_DETAIL
                            }
                        )
                    }

                    Destination.DOC_DETAIL -> {
                        val item = selectedDocItem
                        if (item == null) {
                            destination = Destination.MAP
                        } else {
                            val detailViewModel: DocDetailViewModel = viewModel(
                                key = "doc-detail-${item.sourceRef}",
                                factory = DocDetailViewModel.factory(
                                    applicationContext,
                                    item,
                                    selectedDocBike
                                )
                            )
                            DocDetailScreen(
                                viewModel = detailViewModel,
                                onBack = { destination = docDetailOrigin },
                                onFollow = { followed ->
                                    PendingFollow.offer(followed)
                                    destination = Destination.RECORD
                                }
                            )
                        }
                    }

                    Destination.OTHER_TRACKS_BROWSER -> {
                        val browserViewModel: OtherTracksBrowserViewModel = viewModel(
                            factory = OtherTracksBrowserViewModel.factory(applicationContext)
                        )
                        OtherTracksBrowserScreen(
                            viewModel = browserViewModel,
                            onBack = { destination = Destination.ASSETS },
                            onOpenTrack = { track ->
                                selectedOtherTrack = track
                                destination = Destination.OTHER_TRACK_DETAIL
                            }
                        )
                    }

                    Destination.OTHER_TRACK_DETAIL -> {
                        val track = selectedOtherTrack
                        if (track == null) {
                            destination = Destination.MAP
                        } else {
                            val otherTrackViewModel: OtherTrackViewModel = viewModel(
                                key = "other-track-${track.globalId}",
                                factory = OtherTrackViewModel.factory(applicationContext, track)
                            )
                            OtherTrackScreen(
                                viewModel = otherTrackViewModel,
                                // Back to the map, which is the only place this page is opened from: the
                                // browser that will open it too is the next piece of this work, and it
                                // brings its own origin with it.
                                onBack = { destination = Destination.MAP }
                            )
                        }
                    }

                    Destination.ASSET_DETAIL -> {
                        val assetId = selectedAssetId
                        if (assetId == null) {
                            destination = Destination.ASSETS
                        } else {
                            val detailViewModel: AssetDetailViewModel = viewModel(
                                key = "detail-$assetId",
                                factory = AssetDetailViewModel.factory(applicationContext, assetId)
                            )
                            AssetDetailScreen(
                                viewModel = detailViewModel,
                                onBack = { destination = Destination.ASSETS },
                                onEdit = { destination = Destination.ASSET_EDIT },
                                onKeepGround = {
                                    offlinePickerVisit++
                                    destination = Destination.OFFLINE_PICKER_AROUND
                                },
                                onRecord = {
                                    recordTargetId = assetId
                                    destination = Destination.RECORD
                                },
                                onOpenRecording = { sessionId ->
                                    selectedSessionId = sessionId
                                    destination = Destination.RECORDING_DETAIL
                                }
                            )
                        }
                    }

                    // Editing is a screen rather than a dialog, and it asks for the same view
                    // model the screen behind it uses: that one is keyed by asset id, so the
                    // form opens on the place that was just on screen, and saving updates it.
                    Destination.ASSET_EDIT -> {
                        val assetId = selectedAssetId
                        if (assetId == null) {
                            destination = Destination.ASSETS
                        } else {
                            val editViewModel: AssetDetailViewModel = viewModel(
                                key = "detail-$assetId",
                                factory = AssetDetailViewModel.factory(applicationContext, assetId)
                            )
                            AssetEditScreen(
                                viewModel = editViewModel,
                                onDone = { destination = Destination.ASSET_DETAIL }
                            )
                        }
                    }

                    Destination.RECORD -> {
                        val recordingViewModel: RecordingViewModel = viewModel(
                            key = "record-$recordTargetId",
                            factory = RecordingViewModel.factory(
                                applicationContext,
                                initialTargetId = recordTargetId
                            )
                        )
                        RecordScreen(
                            viewModel = recordingViewModel,
                            onOpenTab = { destination = destinationOf(it) }
                        )
                    }

                    Destination.ADD_SPOT -> {
                        val spotViewModel: AddSpotViewModel = viewModel(
                            key = "add-spot-$addSpotVisit",
                            factory = AddSpotViewModel.factory(applicationContext)
                        )
                        AddSpotScreen(
                            viewModel = spotViewModel,
                            onBack = { destination = Destination.ASSETS }
                        )
                    }

                    Destination.RECORDINGS -> {
                        val recordingsViewModel: RecordingsViewModel = viewModel(
                            factory = RecordingsViewModel.factory(applicationContext)
                        )
                        RecordingsScreen(
                            viewModel = recordingsViewModel,
                            onOpenTab = { destination = destinationOf(it) },
                            onOpenRecording = { sessionId ->
                                selectedSessionId = sessionId
                                destination = Destination.RECORDING_DETAIL
                            }
                        )
                    }

                    Destination.RECORDING_DETAIL -> {
                        val sessionId = selectedSessionId
                        if (sessionId == null) {
                            destination = Destination.RECORDINGS
                        } else {
                            val detailViewModel: RecordingDetailViewModel = viewModel(
                                key = "recording-$sessionId",
                                factory = RecordingDetailViewModel.factory(applicationContext, sessionId)
                            )
                            RecordingDetailScreen(
                                viewModel = detailViewModel,
                                onBack = { destination = Destination.RECORDINGS }
                            )
                        }
                    }

                    Destination.SETTINGS -> {
                        val settingsViewModel: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.factory(applicationContext)
                        )
                        val updateViewModel: UpdateViewModel = viewModel(
                            factory = UpdateViewModel.factory(applicationContext)
                        )
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            updateViewModel = updateViewModel,
                            onBack = { destination = Destination.MAP }
                        )
                    }

                    Destination.OFFLINE -> {
                        val offlineViewModel: OfflineViewModel = viewModel(
                            factory = OfflineViewModel.factory(applicationContext)
                        )
                        OfflineScreen(
                            viewModel = offlineViewModel,
                            onOpenTab = { destination = destinationOf(it) },
                            onChooseArea = {
                                offlinePickerVisit++
                                destination = Destination.OFFLINE_PICKER
                            },
                            onChooseDocArea = {
                                offlinePickerVisit++
                                destination = Destination.OFFLINE_DOC_PICKER
                            },
                            onChooseOtherTracksArea = {
                                offlinePickerVisit++
                                destination = Destination.OFFLINE_OTHER_TRACKS_PICKER
                            },
                            onChooseTopoArea = {
                                offlinePickerVisit++
                                destination = Destination.OFFLINE_TOPO_PICKER
                            }
                        )
                    }

                    Destination.OFFLINE_PICKER -> {
                        val pickerViewModel: OfflineAreaPickerViewModel = viewModel(
                            key = "offline-picker-$offlinePickerVisit",
                            factory = OfflineAreaPickerViewModel.factory(
                                applicationContext,
                                OfflinePickerMode.IMAGERY
                            )
                        )
                        OfflineAreaPickerScreen(
                            viewModel = pickerViewModel,
                            onBack = { destination = Destination.OFFLINE },
                            onSaved = { destination = Destination.OFFLINE }
                        )
                    }

                    Destination.OFFLINE_DOC_PICKER -> {
                        val pickerViewModel: OfflineAreaPickerViewModel = viewModel(
                            key = "offline-doc-picker-$offlinePickerVisit",
                            factory = OfflineAreaPickerViewModel.factory(
                                applicationContext,
                                OfflinePickerMode.DOC_DATA
                            )
                        )
                        OfflineAreaPickerScreen(
                            viewModel = pickerViewModel,
                            onBack = { destination = Destination.OFFLINE },
                            onSaved = { destination = Destination.OFFLINE }
                        )
                    }

                    Destination.OFFLINE_OTHER_TRACKS_PICKER -> {
                        val pickerViewModel: OfflineAreaPickerViewModel = viewModel(
                            key = "offline-other-tracks-picker-$offlinePickerVisit",
                            factory = OfflineAreaPickerViewModel.factory(
                                applicationContext,
                                OfflinePickerMode.OTHER_TRACKS
                            )
                        )
                        OfflineAreaPickerScreen(
                            viewModel = pickerViewModel,
                            onBack = { destination = Destination.OFFLINE },
                            onSaved = { destination = Destination.OFFLINE }
                        )
                    }

                    // The drawn map, kept for a box the walker draws: LINZ's vector Topographic tiles and
                    // the heights its hillshade is shaded from, so the map draws this ground with no
                    // reception. See OfflinePickerMode.TOPOGRAPHY.
                    Destination.OFFLINE_TOPO_PICKER -> {
                        val pickerViewModel: OfflineAreaPickerViewModel = viewModel(
                            key = "offline-topo-picker-$offlinePickerVisit",
                            factory = OfflineAreaPickerViewModel.factory(
                                applicationContext,
                                OfflinePickerMode.TOPOGRAPHY
                            )
                        )
                        OfflineAreaPickerScreen(
                            viewModel = pickerViewModel,
                            onBack = { destination = Destination.OFFLINE },
                            onSaved = { destination = Destination.OFFLINE }
                        )
                    }

                    // The ground around one track: the same screen, opened with the box already drawn
                    // around the line, keeping the imagery and what DOC publishes inside it in one act.
                    Destination.OFFLINE_PICKER_AROUND -> {
                        val assetId = selectedAssetId
                        if (assetId == null) {
                            destination = Destination.ASSETS
                        } else {
                            val pickerViewModel: OfflineAreaPickerViewModel = viewModel(
                                key = "around-$assetId-$offlinePickerVisit",
                                factory = OfflineAreaPickerViewModel.factory(
                                    applicationContext,
                                    OfflinePickerMode.AROUND_TRACK,
                                    aroundAssetId = assetId
                                )
                            )
                            OfflineAreaPickerScreen(
                                viewModel = pickerViewModel,
                                onBack = { destination = Destination.ASSET_DETAIL },
                                onSaved = { destination = Destination.OFFLINE }
                            )
                        }
                    }
                }
            }
        }
    }

    /** The app was already open when the reminder was tapped. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDestination.value = destinationFrom(intent)
    }

    companion object {
        /** The screen an intent would like opened, e.g. from a backup or update notification. */
        const val EXTRA_DESTINATION = "nz.mckenzie.trailday.extra.DESTINATION"
        const val DESTINATION_SETTINGS = "settings"

        private fun destinationFrom(intent: Intent?): Destination? =
            when (intent?.getStringExtra(EXTRA_DESTINATION)) {
                DESTINATION_SETTINGS -> Destination.SETTINGS
                else -> null
            }

        /** A tab is named after the destination it opens, so a tap maps across by name. */
        private fun destinationOf(tab: Tab): Destination = Destination.valueOf(tab.name)
    }
}



