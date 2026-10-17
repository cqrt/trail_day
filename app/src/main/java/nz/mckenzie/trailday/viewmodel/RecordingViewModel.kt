package nz.mckenzie.trailday.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.data.RecordingProgress
import nz.mckenzie.trailday.data.RecordingRepository
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.DriftWord
import nz.mckenzie.trailday.domain.geo.FollowPoint
import nz.mckenzie.trailday.domain.geo.FollowProgress
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.RecordingBreak
import nz.mckenzie.trailday.domain.geo.splitAtBreaks
import nz.mckenzie.trailday.domain.recording.RecordingStatus
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.map.AssetGeoJson
import nz.mckenzie.trailday.map.AssetLine
import nz.mckenzie.trailday.map.AssetStretch
import nz.mckenzie.trailday.tracking.DevicePosition
import nz.mckenzie.trailday.tracking.FusedLocationSource
import nz.mckenzie.trailday.tracking.LocationSource
import nz.mckenzie.trailday.tracking.TrackingService
import nz.mckenzie.trailday.tracking.TrackingState
import nz.mckenzie.trailday.tracking.frameOnDevice
import nz.mckenzie.trailday.ui.formatShortDate

/**
 * Drives the recorder screen.
 *
 * The service owns the recording loop; this only starts/stops it and mirrors
 * [TrackingState] plus the growing geometry from the database, so leaving and
 * re-entering the screen (or restarting the app) loses nothing.
 *
 * A recording is a walk or a ride: the line, the distance, the time, the fixes, and
 * the pauses. It may be followed for a place, which is kept as a soft link.
 */
class RecordingViewModel(
    private val recordings: RecordingRepository,
    private val assetRepository: AssetRepository,
    settingsRepository: SettingsRepository,
    private val context: Context,
    /**
     * Where the phone is, for the first frame. Recording is driving a thing you are
     * standing next to, so opening on a neutral view of the country is no use.
     */
    private val locationSource: LocationSource
) : ViewModel() {

    val apiKey: StateFlow<String> = settingsRepository.linzApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Which map to draw the pass on: the walker's choice in Settings. */
    val basemap: StateFlow<Basemap> = settingsRepository.basemap
        .stateIn(viewModelScope, SharingStarted.Eagerly, Basemap.DEFAULT)

    val tracking: StateFlow<TrackingState.State> = TrackingState.state

    /** Places to follow, so a recording can be linked to the line it was made for. */
    val assets: StateFlow<List<AssetEntity>> = assetRepository.observeActiveAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _initialFrame = MutableStateFlow<LatLngBounds?>(null)

    /** The frame for the camera when the map opens: around the phone, if it knows. */
    val initialFrame: StateFlow<LatLngBounds?> = _initialFrame

    init {
        viewModelScope.launch {
            _initialFrame.value = locationSource.frameOnDevice()
        }
    }

    private val _selectedAssetId = MutableStateFlow<Long?>(null)
    val selectedAssetId: StateFlow<Long?> = _selectedAssetId

    val selectedTrackName: StateFlow<String?> =
        combine(_selectedAssetId, assets) { id, list ->
            list.firstOrNull { it.id == id }?.name
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * The line of the place being followed, or nothing when none is chosen.
     *
     * A line and not the whole place: following is about the ground, and a spot has none. Read from the
     * database once per choice rather than per fix, because a track's geometry does not move while the
     * walker does.
     */
    private val targetLine: StateFlow<List<GeoPoint>> = _selectedAssetId
        .map { id -> id?.let { runCatching { assetRepository.getAssetGeometry(it) }.getOrNull()?.line } }
        .map { line -> line.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Where the phone is, as one state the readouts share: one subscriber to the fixes, and one answer to
     * "where am I" for both kinds of target.
     */
    private val here: StateFlow<GeoPoint?> = DevicePosition.updates(viewModelScope)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Where the walker is against that line: how far off it, how far along, and how much is left.
     *
     * Null when there is no line to follow or the phone has not said where it is - both of which are
     * ordinary at the start of a walk rather than errors, and neither of which has a readout.
     */
    val follow: StateFlow<FollowProgress?> = combine(targetLine, here) { line, position ->
        if (line.size >= 2 && position != null) FollowProgress.of(line, position) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Heading for a point instead: where the chosen place is, when it is a place rather than a line.
     *
     * Null while a line is being followed, or when nothing is chosen. A spot has no line to be off, so the
     * two readouts cannot both be on: each kind of target answers the questions that make sense of it.
     */
    val heading: StateFlow<FollowPoint?> = combine(targetLine, assets, here) { line, places, position ->
        val place = places.firstOrNull { it.id == _selectedAssetId.value }
        val isPlace = place != null && AssetShape.fromStorage(place.shape) == AssetShape.POINT
        if (!isPlace || line.isEmpty() || position == null) {
            null
        } else {
            FollowPoint.of(position, line.first())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val driftWord = DriftWord()

    private val _drift = MutableStateFlow<Double?>(null)

    /**
     * How far off the line the walker is, while they are properly off it: the quiet word, as a state.
     *
     * Null while they are on the line. The distance is the live one rather than the one it first spoke at,
     * because the walker is reading where they are now; what [DriftWord] decides is whether they are off at
     * all, with the gap that stops it flickering.
     */
    val drift: StateFlow<Double?> = _drift

    init {
        // The drift word, said as the fixes arrive - and **after** `follow` and the state above it are
        // built, because a property declared below an initialiser block is not there yet when it runs.
        // Collected here rather than in the screen so the rule is one rule whatever the screen does with
        // it, and so leaving the screen does not leave a half-said drift behind.
        viewModelScope.launch {
            follow.collect { progress ->
                _drift.value = when {
                    progress == null -> null
                    driftWord.isOff(progress.offM) -> progress.offM
                    else -> null
                }
            }
        }
    }

    /**
     * The pass that has just been saved.
     *
     * Held here so the screen a moment after the save still shows what was recorded,
     * and given up the moment the walker says they are done with it: choosing another
     * place, starting another recording, or leaving the recorder - see [onScreenLeft].
     */
    private val _finished = MutableStateFlow<FinishedPass?>(null)
    val finished: StateFlow<FinishedPass?> = _finished

    /**
     * Whether the walker wants the map to keep the phone in the middle of the screen.
     *
     * Their wish, not the map's behaviour: [followPhone] is what the map is told.
     */
    private val _following = MutableStateFlow(false)
    val following: StateFlow<Boolean> = _following

    /**
     * What the map is told: follow the phone while a pass is being driven, and only while the
     * walker has not taken the map for themselves by dragging it.
     *
     * Not while the recording is paused - that is the walker stopped, with both hands free
     * and the map theirs to read - and not after it is finished, so the pass they have just
     * finished stays where it ended rather than sliding around under them.
     */
    val followPhone: StateFlow<Boolean> = combine(tracking, _following) { live, wanted ->
        wanted && live.status == RecordingStatus.RECORDING
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /**
     * The numbers on the card: the pass being driven, or the one just saved.
     *
     * One flow rather than the screen reaching into [TrackingState] itself, so that what the
     * card says and what the map draws come from the same place and cannot disagree about
     * which pass is being looked at.
     */
    val summary: StateFlow<PassSummary> = combine(tracking, _finished) { live, done ->
        when {
            live.sessionId != null -> live.summary()
            done != null -> done.summary
            else -> live.summary()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PassSummary.EMPTY)

    /**
     * The line being followed, drawn wide over the work while a pass is running against a chosen place.
     *
     * Its own kind's colour, as it has on every other map - what makes it bold is the width, because a
     * followed bike track should still look like a bike track - and nothing at all when no place is
     * chosen, which is most recordings.
     */
    val targetGeoJson: StateFlow<String> = combine(targetLine, assets) { line, places ->
        val place = places.firstOrNull { it.id == _selectedAssetId.value }
        val isLine = place != null && AssetShape.fromStorage(place.shape) == AssetShape.LINE
        if (!isLine || line.size < 2) {
            AssetGeoJson.build(emptyList())
        } else {
            AssetGeoJson.build(
                listOf(
                    AssetLine(
                        assetId = 0L,
                        name = place.name,
                        colorHex = AssetColors.forKind(AssetKind.fromStorage(place.kind)),
                        points = line,
                        kind = AssetKind.fromStorage(place.kind),
                        shape = AssetShape.LINE
                    )
                )
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        AssetGeoJson.build(emptyList())
    )

    /** One pass as the card reads it, whether it is still being driven or has just been saved. */
    data class PassSummary(
        val status: RecordingStatus?,
        val startedAtEpochMs: Long?,
        val endedAtEpochMs: Long?,
        val distanceM: Double,
        val pointCount: Int,
        val accuracyM: Float?,
        val rejectedFixes: Int
    ) {
        companion object {
            val EMPTY = PassSummary(null, null, null, 0.0, 0, null, 0)
        }
    }

    /** A pass that has been saved: what it was for, what was recorded, and its numbers. */
    data class FinishedPass(
        val trackName: String?,
        val recorded: List<GeoPoint>,
        /** The stretches of it that were paused for, so the line stays honest. */
        val breaks: List<RecordingBreak> = emptyList(),
        val summary: PassSummary
    )

    /** The live pass's numbers, in the shape the card reads. */
    private fun TrackingState.State.summary(
        status: RecordingStatus? = this.status,
        endedAtEpochMs: Long? = null
    ) = PassSummary(
        status = status,
        startedAtEpochMs = startedAtEpochMs,
        endedAtEpochMs = endedAtEpochMs,
        distanceM = distanceM,
        pointCount = pointCount,
        accuracyM = lastAccuracyM,
        rejectedFixes = rejectedFixes
    )

    /**
     * The fixes of the recording that is happening now - what the screen draws.
     *
     * Followed from [TrackingState] rather than collected when a session starts, so that
     * exactly one collection is alive at a time: `flatMapLatest` drops the previous session's
     * the moment the session changes. Collecting a session's points and merely moving on does
     * not work, and the way it fails is not obvious - a flow over `recorded_points` re-emits
     * whenever *that table* changes, whichever session was written to, so a recording that has
     * already finished keeps handing its whole geometry back.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sessionPoints: StateFlow<List<GeoPoint>> = TrackingState.state
        .map { it.sessionId }
        .distinctUntilChanged()
        .flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList()) else recordings.observePoints(sessionId)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * The breaks in the recording that is happening now: the stretches the walker paused for.
     *
     * Followed exactly as the fixes are, so the line drawn is the pass as it actually was.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sessionBreaks: StateFlow<List<RecordingBreak>> = TrackingState.state
        .map { it.sessionId }
        .distinctUntilChanged()
        .flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList()) else recordings.observeBreaks(sessionId)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * What the map draws: the recorded line, broken where the walker paused.
     *
     * With nothing live, the pass that has just been saved stays where it was rather than the
     * map going blank the moment Save is pressed.
     */
    val recordedGeoJson: StateFlow<String> =
        combine(sessionPoints, sessionBreaks, _finished) { points, breaks, done ->
            when {
                points.isNotEmpty() -> points to breaks
                done != null -> done.recorded to done.breaks
                else -> emptyList<GeoPoint>() to emptyList()
            }
        }
            .map { (points, breaks) -> recordingGeoJson(points, breaks) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                AssetGeoJson.build(emptyList())
            )

    /**
     * The recorded line as the map draws it, in pieces where the walker paused.
     *
     * The gaps where the fixes only went missing are drawn through: the recording is the
     * evidence, and what the walker stopped for is part of it.
     */
    private fun recordingGeoJson(points: List<GeoPoint>, breaks: List<RecordingBreak>): String {
        if (points.isEmpty()) return AssetGeoJson.build(emptyList())
        return AssetGeoJson.build(
            listOf(
                AssetLine(
                    assetId = RECORDING_ID,
                    name = "Recording",
                    colorHex = AssetColors.RECORDED,
                    points = points,
                    stretches = points.splitAtBreaks(breaks).map { piece ->
                        AssetStretch(colorHex = AssetColors.RECORDED, points = piece)
                    }
                )
            )
        )
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /**
     * The name suggested for the track a finished recording is about to become, or
     * null when no name is being asked for. The screen shows a dialog while this is
     * set, so a new line always gets a name before it is saved as a track.
     */
    private val _pendingTrackName = MutableStateFlow<String?>(null)
    val pendingTrackName: StateFlow<String?> = _pendingTrackName

    init {
        // A new pass is a map that should be moving with the walker: following comes back on
        // by itself whenever a session begins, so driving a line one-handed never starts with
        // the map parked somewhere else. Only a drag, or the control, takes it off again.
        viewModelScope.launch {
            tracking.map { it.sessionId }
                .distinctUntilChanged()
                .collect { sessionId -> if (sessionId != null) _following.value = true }
        }

        viewModelScope.launch {
            // Reattach to a session left running, e.g. the app was killed mid-walk.
            val unfinished = recordings.findUnfinishedSession() ?: return@launch
            // Its fixes are still in the database, so how far it has got is too.
            val progress = runCatching { recordings.recordingProgress(unfinished.id) }
                .getOrDefault(RecordingProgress.EMPTY)
            // Those two reads suspend, so the walker may have begun a pass - or finished one
            // and begun another - while they were in flight. Reattach only if nothing is being
            // mirrored yet.
            if (TrackingState.current.sessionId != null) return@launch
            TrackingState.begin(
                sessionId = unfinished.id,
                startedAtEpochMs = unfinished.startedAtEpochMs,
                pointCount = progress.pointCount,
                distanceM = progress.distanceM
            )
            val status = runCatching { RecordingStatus.valueOf(unfinished.status) }
                .getOrDefault(RecordingStatus.RECORDING)
            TrackingState.setStatus(status)
            unfinished.assetId?.let { selectTrack(it) }

            // A foreground service does not survive a process kill, so a session
            // still marked RECORDING has nothing collecting for it: start the
            // service again rather than silently leaving a dead session running.
            if (status == RecordingStatus.RECORDING && hasLocationPermission()) {
                TrackingService.start(context, unfinished.id)
                _message.value = "Resumed recording the track that was already in progress"
            } else {
                _message.value = "Reattached to the track that was already recording"
            }
        }
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun onPermissionDenied() {
        _message.value = "Location permission is needed to record a track"
    }

    /**
     * Chooses which place is being followed. Can be called before or during a recording.
     */
    fun selectTrack(assetId: Long?) {
        _selectedAssetId.value = assetId
        // A new line to follow is a new set of distances and a new drift: the word said about the last
        // one is not about this one.
        driftWord.reset()
        _drift.value = null
        // Choosing a place is the start of a new job, so the pass that was on the screen gives
        // way to the one being set up. Cancelling the choice - assetId null - is not: that is
        // the walker backing out of the picker, with the finished pass still the last thing
        // they did.
        if (assetId != null) _finished.value = null
        viewModelScope.launch {
            if (assetId == null) return@launch

            // Record which place this session is for, even if it was started first.
            TrackingState.current.sessionId?.let { sessionId ->
                runCatching { recordings.setSessionAsset(sessionId, assetId) }
            }
        }
    }

    fun start() {
        if (!hasLocationPermission()) {
            onPermissionDenied()
            return
        }
        // A new pass, so the last one leaves the screen. Following is turned on by the session
        // itself beginning - see the initialiser - rather than twice over here.
        _finished.value = null
        driftWord.reset()
        _drift.value = null
        viewModelScope.launch {
            _message.value = null
            val sessionId = recordings.startRecording(
                name = defaultName(),
                assetId = _selectedAssetId.value
            )
            TrackingState.begin(sessionId, System.currentTimeMillis())
            TrackingService.start(context, sessionId)
        }
    }

    fun pause() = sessionIdOrNull()?.let { TrackingService.pause(context) }

    /**
     * Carrying on is a pass being driven again, so following comes back on: the walker
     * stopped for a reason that has now passed, and the map is theirs to look at until then.
     */
    fun resume() {
        _following.value = true
        sessionIdOrNull()?.let { TrackingService.resume(context) }
    }

    /** The follow control: the walker saying yes, or no, to the map moving with them. */
    fun setFollowing(value: Boolean) {
        _following.value = value
    }

    /**
     * The walker dragged the map while it was following them.
     *
     * Following gives way rather than fighting the drag at the next fix. It stays off until
     * they ask for it again, which the screen's control does - and the same control is what
     * tells them it is off, so the map does not appear to have stopped working.
     */
    fun onMapPanned() {
        _following.value = false
    }

    /**
     * The walker has left the recorder: another tab, back to the map, or the app put down.
     *
     * A saved pass belongs to the visit, not to the screen for good. Left behind, a card that is
     * waiting to start the next job still carries the last job's distance, its points and the
     * sentence about saving it, and so reads as if that pass were still the one in hand.
     *
     * A pass still being driven is left alone. Leaving mid-walk loses nothing - the service owns
     * the recording and the database has its fixes.
     */
    fun onScreenLeft() {
        if (sessionIdOrNull() != null) return
        _finished.value = null
        _message.value = null
    }

    /**
     * Closes the session with the distance the service accumulated.
     *
     * A pass followed for a place that already exists finishes straight away - it has a name.
     * A brand new line asks what to call it first, because on this screen "record" means
     * "make a track".
     */
    fun finish() {
        val assetId = _selectedAssetId.value
        if (assetId == null) {
            _pendingTrackName.value = defaultTrackName()
            return
        }
        completeFinish(newTrackName = null)
    }

    /** From the naming dialog: saves the recorded line as a new, named track. */
    fun confirmFinish(name: String) {
        completeFinish(newTrackName = name.trim().ifBlank { defaultTrackName() })
    }

    /** Dismisses the naming dialog without ending the recording. */
    fun cancelFinish() {
        _pendingTrackName.value = null
    }

    private fun completeFinish(newTrackName: String?) {
        val sessionId = sessionIdOrNull() ?: return
        viewModelScope.launch {
            _pendingTrackName.value = null

            val distanceM = TrackingState.current.distanceM
            val points = TrackingState.current.pointCount
            val sessionStart = TrackingState.current.startedAtEpochMs ?: System.currentTimeMillis()
            val existingAssetId = _selectedAssetId.value

            // The pass as it stands this instant, kept whole. A moment from now the live
            // session is closed and cleared, and the screen should be showing what was just
            // recorded rather than nothing at all.
            val numbersNow = TrackingState.current.summary(
                // The pass is finished by the time this is read off the screen, whatever the
                // service's last word on it was.
                status = RecordingStatus.FINISHED,
                endedAtEpochMs = System.currentTimeMillis()
            )

            // Read the geometry back from the database rather than the live flow: the
            // newest fix may not have reached the flow yet, and it must not be missing
            // from the track.
            val geometry = runCatching { recordings.getPoints(sessionId) }
                .getOrDefault(emptyList())
            // And the pauses with it, for the same reason.
            val breaks = runCatching { recordings.getBreaks(sessionId) }
                .getOrDefault(emptyList())

            var trackError: String? = null
            val assetId = when {
                existingAssetId != null -> existingAssetId
                newTrackName == null -> null
                geometry.size < 2 -> null
                else -> runCatching { assetRepository.createAsset(name = newTrackName, geometry = geometry) }
                    .onFailure { trackError = it.message ?: "could not save the track" }
                    .getOrNull()
            }

            // Read the name from the database, not from the flow the screen collects:
            // whether a recording gets its proper name must not depend on the UI
            // happening to be watching.
            val recordingName = newTrackName ?: assetId?.let { id ->
                runCatching { assetRepository.getAsset(id)?.name }.getOrNull()
            }

            recordings.finishRecording(sessionId, distanceM = distanceM)

            // Name the trip after the work it is: a new line takes the name the
            // walker typed, and a trip made for an existing place takes that
            // place's name and the date. Neither asks a question - a dialog on the
            // common path is friction for an answer the app already has.
            val sessionName = when {
                newTrackName != null -> newTrackName
                recordingName != null -> "$recordingName · ${formatShortDate(sessionStart)}"
                else -> null
            }
            sessionName?.let { runCatching { recordings.renameSession(sessionId, it) } }
            assetId?.let { runCatching { recordings.setSessionAsset(sessionId, it) } }

            TrackingService.stop(context)
            // Clearing the tracking state is what empties the drawing: these are the
            // running session's fixes, and there is no longer one running.
            TrackingState.clear()

            var message = "Saved $points ${if (points == 1) "point" else "points"}"
            message += when {
                newTrackName == null -> ""
                assetId != null -> " as \"$newTrackName\", which is on the Places page now"
                trackError != null -> " - the track could not be saved: $trackError"
                else -> " - too few points for a track, so it is in Trips only"
            }

            _message.value = message
            _finished.value = FinishedPass(
                trackName = recordingName,
                recorded = geometry,
                breaks = breaks,
                summary = numbersNow
            )
            _selectedAssetId.value = null
        }
    }

    private fun sessionIdOrNull(): Long? = TrackingState.current.sessionId

    private fun defaultName(): String = "Trip"

    /** Suggests a track name: "Track 14 Sep" beats an empty box to type into. */
    private fun defaultTrackName(): String = "Track " + formatShortDate(System.currentTimeMillis())

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val RECORDING_ID = -2L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val database = TrailDayDatabase.get(appContext)
                    RecordingViewModel(
                        recordings = RecordingRepository(database),
                        assetRepository = AssetRepository(database),
                        settingsRepository = SettingsRepository(appContext),
                        context = appContext,
                        locationSource = FusedLocationSource(appContext)
                    )
                }
            }
        }
    }
}
