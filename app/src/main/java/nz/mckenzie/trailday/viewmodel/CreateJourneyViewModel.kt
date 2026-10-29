package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.doc.DocCache
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.journey.JourneyBuilder
import nz.mckenzie.trailday.domain.journey.JourneyNote
import nz.mckenzie.trailday.domain.journey.JourneyPlan
import nz.mckenzie.trailday.domain.journey.JourneyResult
import nz.mckenzie.trailday.domain.journey.JourneyTrack
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.map.AssetGeoJson
import nz.mckenzie.trailday.map.AssetLine
import nz.mckenzie.trailday.map.DocGeoJson
import nz.mckenzie.trailday.offline.TerrainHeights
import nz.mckenzie.trailday.tracking.FusedLocationSource
import nz.mckenzie.trailday.tracking.LocationSource
import nz.mckenzie.trailday.offline.OfflineDocStore

/**
 * Making a journey: the taps, the line they build, and what it costs.
 *
 * The map asks this for one thing at a time and the walker sees the answer on the ground: a tap becomes
 * a point, two points become a walk along DOC's line, and every tap after that extends it. Nothing is
 * stored until Finish.
 */
class CreateJourneyViewModel(
    private val assetRepository: AssetRepository,
    private val docStore: DocCache,
    private val settings: SettingsRepository,
    private val heights: TerrainHeights,
    /** Where the walker is: the map opens here, at a scale a thumb can pick a line at. */
    private val locationSource: LocationSource
) : ViewModel() {

    /** The map the picker draws on: whatever the walker reads the land by. */
    val basemap: StateFlow<Basemap> = settings.basemap
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Basemap.LINZ_AERIAL)

    /** DOC's tracks on the phone, which is all a journey can be built from. */
    private var tracks: List<JourneyTrack> = emptyList()

    /** Where to open the map: the ground DOC's kept tracks are on, so the lines are in sight. */
    private val _startBounds = MutableStateFlow<LatLngBounds?>(null)
    val startBounds: StateFlow<LatLngBounds?> = _startBounds

    /** DOC's own material, drawn as it is: the pieces a walker is choosing between. */
    private val _docGeoJson = MutableStateFlow(AssetGeoJson.build(emptyList()))
    val docGeoJson: StateFlow<String> = _docGeoJson

    private val _picks = MutableStateFlow<List<GeoPoint>>(emptyList())

    /** The points tapped so far, in order. The first is the start. */
    val picks: StateFlow<List<GeoPoint>> = _picks

    private val _plan = MutableStateFlow<JourneyPlan?>(null)

    /** The walk the taps have built, or null while there is not one yet. */
    val plan: StateFlow<JourneyPlan?> = _plan

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _climbM = MutableStateFlow<Double?>(null)

    /** The climb along the walk, once LINZ's heights have answered. Null while they are coming, or if
     *  they cannot be had at all - which is a fact about the ground, not a fault. */
    val climbM: StateFlow<Double?> = _climbM

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    /** The journey that has just been kept, for the screen to leave on. */
    private val _saved = MutableStateFlow<Long?>(null)
    val saved: StateFlow<Long?> = _saved

    /** The heights, once they have answered, so a saved journey carries them on its points. */
    private var sampledHeights: List<Double?> = emptyList()

    private var heightJob: Job? = null

    /** The line as the map draws it: the parts, a marker per tap, and the bridge drawn apart. */
    val previewGeoJson: StateFlow<String> = combine(_plan, _picks) { plan, picks ->
        val lines = mutableListOf<AssetLine>()
        plan?.parts?.forEach { part ->
            lines += AssetLine(
                assetId = 0L,
                name = "",
                colorHex = if (part.bridge) BRIDGE_COLOR else AssetColors.RECORDED,
                points = part.points
            )
        }
        picks.forEach { pick ->
            lines += AssetLine(
                assetId = 0L,
                name = "",
                colorHex = AssetColors.RECORDED,
                points = listOf(pick),
                kind = nz.mckenzie.trailday.domain.asset.AssetKind.WAYPOINT,
                shape = nz.mckenzie.trailday.domain.asset.AssetShape.POINT
            )
        }
        AssetGeoJson.build(lines)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AssetGeoJson.build(emptyList()))

    init {
        viewModelScope.launch {
            val kept = runCatching { docStore.cached(DocSource.TRACKS) }.getOrDefault(emptyList())
            tracks = kept.mapNotNull { item ->
                val paths = item.paths.filter { path -> path.size >= 2 }
                if (paths.isEmpty()) null else JourneyTrack(name = item.name, paths = paths)
            }
            _docGeoJson.value = DocGeoJson.build(kept)
            // **Where the walker is, not the whole region.** Fitted to every downloaded track, a region
            // is a few hundred kilometres across and a hundred metres is a third of a pixel - no thumb
            // can land on a line at all. Ten kilometres is a scale that shows the tracks and lets a tap
            // pick one.
            val here = runCatching { locationSource.currentLocation() }.getOrNull()
            _startBounds.value = here?.let { frameAround(it) } ?: boundsOf(kept.flatMap { it.paths })
            if (tracks.isEmpty()) {
                _message.value = "There are no DOC tracks on the phone yet. Download a region on the " +
                    "Offline page, then come back."
            }
        }
    }

    /**
     * A tap: one more point, and the walk rebuilt from all of them.
     *
     * Rebuilt rather than extended, because the whole point of the builder is that a tap can change the
     * way between the points either side of it - and because a journey is short enough that building it
     * again costs nothing a walker can see.
     */
    fun tap(latitude: Double, longitude: Double) {
        if (_saving.value) return
        _message.value = null
        val point = GeoPoint(lat = latitude, lng = longitude)

        if (_plan.value == null && _picks.value.isEmpty() && tracks.isEmpty()) {
            _message.value = "There are no DOC tracks on the phone yet. Download a region on the Offline " +
                "page, then come back."
            return
        }

        // **Every tap is asked about, the first one included.** A point in a paddock is not a point on
        // DOC's track, and accepting it would only fail at the next tap, which reads as the wrong thing
        // being blamed.
        if (!JourneyBuilder.isOnATrack(point, tracks)) {
            _message.value = "DEBUG ${point.lat}, ${point.lng} nearest=" +
                "${JourneyBuilder.nearestDistanceM(point, tracks).toInt()} m of ${tracks.size} tracks"
            return
        }

        val candidate = _picks.value + point
        if (candidate.size < 2) {
            _picks.value = candidate
            return
        }

        when (val result = JourneyBuilder.build(candidate, tracks)) {
            JourneyResult.NotOnATrack -> {
                _message.value = "DEBUG ${point.lat}, ${point.lng} nearest=" +
                "${JourneyBuilder.nearestDistanceM(point, tracks).toInt()} m of ${tracks.size} tracks"
            }
            JourneyResult.NotConnected -> {
                _message.value = "DOC's tracks don't join between that point and the last one. Tap " +
                    "somewhere else."
            }
            is JourneyResult.Built -> {
                _picks.value = candidate
                _plan.value = result.plan
                sampleHeights(result.plan)
            }
        }
    }

    /** Takes the last point off, and the walk back with it. */
    fun undo() {
        if (_saving.value || _picks.value.isEmpty()) return
        _message.value = null
        heightJob?.cancel()
        _climbM.value = null
        sampledHeights = emptyList()

        val fewer = _picks.value.dropLast(1)
        _picks.value = fewer
        _plan.value = if (fewer.size < 2) {
            null
        } else {
            (JourneyBuilder.build(fewer, tracks) as? JourneyResult.Built)?.plan
        }
        _plan.value?.let { sampleHeights(it) }
    }

    fun startOver() {
        if (_saving.value) return
        heightJob?.cancel()
        _picks.value = emptyList()
        _plan.value = null
        _climbM.value = null
        sampledHeights = emptyList()
        _message.value = null
    }

    /**
     * Keeps the walk as a track, with its heights, and says what it was made from.
     *
     * The heights are written onto the points rather than kept beside them, so everything that already
     * reads a line with heights - the profile, the walking time, GPX, the backup - reads this one too.
     */
    fun keep(name: String) {
        val plan = _plan.value ?: return
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            val typed = name.trim()
            val finalName = typed.ifEmpty { plan.name }
            val note = JourneyNote(
                sections = plan.sections,
                bridgeM = plan.bridgeM,
                climbM = _climbM.value ?: 0.0
            )
            val points = plan.line.mapIndexed { index, point ->
                point.copy(altitudeM = sampledHeights.getOrNull(index))
            }
            val id = runCatching {
                assetRepository.createAsset(
                    name = finalName,
                    geometry = points,
                    journey = note.toJson()
                )
            }.getOrNull()
            _saving.value = false
            if (id == null) {
                _message.value = "The journey could not be kept. Try again."
            } else {
                _saved.value = id
            }
        }
    }

    /** Asks LINZ for a height at each point, and reports the climb when it comes back. */
    private fun sampleHeights(plan: JourneyPlan) {
        heightJob?.cancel()
        _climbM.value = null
        val line = plan.line
        heightJob = viewModelScope.launch {
            val heights = runCatching { heights.along(line) }.getOrDefault(emptyList())
            if (heights.size != line.size) return@launch
            sampledHeights = heights
            val climb = nz.mckenzie.trailday.domain.geo.TerrainRgb.climbM(heights)
            _climbM.value = if (heights.all { it == null }) null else climb
        }
    }

    /** A frame about ten kilometres across, centred on the walker. */
    private fun frameAround(point: GeoPoint) = LatLngBounds(
        minLat = point.lat - 0.025,
        minLng = point.lng - 0.0375,
        maxLat = point.lat + 0.025,
        maxLng = point.lng + 0.0375
    )

    private fun boundsOf(paths: List<List<GeoPoint>>): LatLngBounds? {
        val all = paths.flatten()
        if (all.isEmpty()) return null
        return LatLngBounds(
            minLat = all.minOf { it.lat },
            minLng = all.minOf { it.lng },
            maxLat = all.maxOf { it.lat },
            maxLng = all.maxOf { it.lng }
        )
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** A bridged part is drawn in the app's earth colour: "no line here, but the walk carries on". */
        private const val BRIDGE_COLOR = "#7A4A00"

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val database = TrailDayDatabase.get(appContext)
                    CreateJourneyViewModel(
                        assetRepository = AssetRepository(database),
                        docStore = OfflineDocStore(
                            dao = database.offlineDocItemDao(),
                            downloader = nz.mckenzie.trailday.doc.DocDownloader.online()
                        ),
                        settings = SettingsRepository(appContext),
                        heights = TerrainHeights(),
                        locationSource = FusedLocationSource(appContext)
                    )
                }
            }
        }
    }
}
