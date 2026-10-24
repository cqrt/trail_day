package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.doc.DocDownloader
import nz.mckenzie.trailday.doc.DocInventory
import nz.mckenzie.trailday.domain.doc.DocBounds
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.map.AssetGeoJson
import nz.mckenzie.trailday.map.AssetLine
import nz.mckenzie.trailday.offline.LinzTileFetcher
import nz.mckenzie.trailday.offline.OfflineArea
import nz.mckenzie.trailday.offline.OfflineAreaDraft
import nz.mckenzie.trailday.offline.OfflineAreaManager
import nz.mckenzie.trailday.offline.OfflineDocStore
import nz.mckenzie.trailday.offline.OfflinePickerMode
import nz.mckenzie.trailday.offline.OfflineTileStore
import nz.mckenzie.trailday.offline.TileDownloader
import nz.mckenzie.trailday.offline.TileServerHolder
import nz.mckenzie.trailday.offline.docDownloadedMessage
import nz.mckenzie.trailday.offline.groundAround
import nz.mckenzie.trailday.offline.topoDownloadedMessage
import nz.mckenzie.trailday.tracking.FusedLocationSource
import nz.mckenzie.trailday.tracking.LocationSource
import nz.mckenzie.trailday.ui.formatShortDate

/**
 * Choosing an offline area by hand: two taps on the map for the corners, the zoom
 * levels to cache, and a name to find it by later.
 *
 * The screen shows the cost of every choice as it is made - move a slider and the
 * tile count moves with it - because the difference between zoom 16 and 17 is the
 * difference between a minute and a quarter of an hour.
 */
class OfflineAreaPickerViewModel(
    private val manager: OfflineAreaManager,
    private val assetRepository: AssetRepository,
    private val locationSource: LocationSource,
    private val settings: SettingsRepository,
    /** The DOC tracks kept for offline use, downloaded for the box drawn here. */
    private val docStore: OfflineDocStore,
    /** The vector Topographic map's own tiles, downloaded for the box drawn here. */
    private val topographyStore: OfflineTileStore,
    /** The heights its hillshade is shaded from, downloaded for the same box. */
    private val elevationStore: OfflineTileStore,
    /** Which pack this run is choosing an area for. */
    val mode: OfflinePickerMode,
    /**
     * The place whose ground is being kept, when the picker was opened from one: its line gives the box,
     * and its name names the area. Null for a box drawn by hand.
     */
    private val aroundAssetId: Long? = null
) : ViewModel() {

    private val _draft = MutableStateFlow(
        OfflineAreaDraft(
            name = "Area " + formatShortDate(System.currentTimeMillis()),
            maxZoom = minOf(OfflineAreaDraft.DEFAULT_MAX_ZOOM, deepestZoom)
        )
    )
    val draft: StateFlow<OfflineAreaDraft> = _draft

    /**
     * The deepest level worth fetching for this run.
     *
     * The drawn map stops where LINZ publishes it, and the renderer overzooms past that from the tiles it
     * has - so asking deeper is asking for tiles that are not there, and counting them would overstate the
     * download. Every other pack fetches what the sliders say.
     */
    val deepestZoom: Int
        get() = if (mode == OfflinePickerMode.TOPOGRAPHY) {
            Basemap.LINZ_TOPO_LIDAR_HILLSHADE.maxZoom
        } else {
            OfflineAreaDraft.MAX_ZOOM
        }

    val apiKey: StateFlow<String> = settings.linzApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Where the picker's map should open, so nobody starts on the wrong island. */
    private val _startBounds = MutableStateFlow<LatLngBounds?>(null)
    val startBounds: StateFlow<LatLngBounds?> = _startBounds

    /**
     * What the map draws: the box once both corners are picked, or a small marker where
     * the first corner is, so the first tap shows it landed.
     */
    val previewGeoJson: StateFlow<String> = _draft
        .map { draft ->
            val points = when {
                draft.isComplete -> outlineOf(draft.bounds!!)
                draft.firstCorner != null -> outlineOf(boxAround(draft.firstCorner!!))
                else -> emptyList()
            }
            if (points.isEmpty()) {
                AssetGeoJson.build(emptyList())
            } else {
                AssetGeoJson.build(
                    listOf(
                        AssetLine(
                            assetId = OUTLINE_ID,
                            name = "Area",
                            colorHex = OUTLINE_COLOUR,
                            points = points
                        )
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AssetGeoJson.build(emptyList()))

    /** Progress of the download this screen started, once it is under way. */
    private val _activeId = MutableStateFlow<Long?>(null)
    val active: StateFlow<OfflineArea?> = manager.observeAreas()
        .map { areas -> areas.firstOrNull { it.id == _activeId.value } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _saved = MutableStateFlow(false)

    /**
     * True once the area is stored and its download has finished. A one-shot signal:
     * the screen consumes it as it leaves, so re-entering does not bounce straight
     * back out.
     */
    val saved: StateFlow<Boolean> = _saved

    /** How many DOC tracks are downloaded for offline use, live from the database. */
    val docDataCount: StateFlow<Int> = docStore.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    private val _docWorking = MutableStateFlow(false)
    val docWorking: StateFlow<Boolean> = _docWorking

    private val _docMessage = MutableStateFlow<String?>(null)
    val docMessage: StateFlow<String?> = _docMessage

    private val _topoMessage = MutableStateFlow<String?>(null)

    /** What the last Topographic download kept, or why it could not be made. */
    val topoMessage: StateFlow<String?> = _topoMessage

    private val _docInventory = MutableStateFlow<DocInventory?>(null)

    /**
     * What DOC says is in the box drawn, or null while there is no box or the answer is still coming.
     *
     * Asked when the box is drawn rather than when the button is tapped, because the point of it is to be
     * read *before* the download: the size of an area is the one thing a phone in the hills cannot change
     * its mind about afterwards.
     */
    val docInventory: StateFlow<DocInventory?> = _docInventory

    private val _docCounting = MutableStateFlow(false)

    /** True while DOC is being asked what is in the drawn box, so the screen can say so. */
    val docCounting: StateFlow<Boolean> = _docCounting

    fun consumeSaved() {
        _saved.value = false
    }

    init {
        viewModelScope.launch {
            // Open over the walker: their position, else the middle of their tracks.
            val fix = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                runCatching { locationSource.currentLocation() }.getOrNull()
            }
            val centre = fix?.let { GeoPoint(it.lat, it.lng) }
                ?: assetRepository.assetBounds()?.let { bounds ->
                    GeoPoint(
                        lat = (bounds.minLat + bounds.maxLat) / 2.0,
                        lng = (bounds.minLng + bounds.maxLng) / 2.0
                    )
                }
            centre?.let { _startBounds.value = boxAround(it, degrees = START_FRAME_DEGREES) }
        }

        // Opened from a track: the ground around it, already drawn, so the walker is looking at the walk
        // they are about to do rather than at wherever the phone happens to be. The corners can still be
        // re-drawn by tapping, and the area is named after the track, so the offline list reads as walks.
        if (aroundAssetId != null) {
            viewModelScope.launch {
                val asset = assetRepository.getAsset(aroundAssetId)
                val around = asset?.let {
                    groundAround(name = it.name, geometry = assetRepository.getAssetGeometry(aroundAssetId))
                }
                if (around != null) {
                    _startBounds.value = around.bounds
                    _draft.value = _draft.value
                        .withCorner(GeoPoint(around.bounds.minLat, around.bounds.minLng))
                        .withCorner(GeoPoint(around.bounds.maxLat, around.bounds.maxLng))
                        .copy(name = around.name)
                }
            }
        }

        // What the drawn box would take on, asked as soon as it is a box and asked again when it changes.
        // `collectLatest` with a short pause is the debounce: dragging a corner asks once at the end
        // rather than once a frame, and a box that is still being drawn is never asked about.
        viewModelScope.launch {
            _draft
                .map { draft -> draft.plan(name = draft.name, fallbackName = "") }
                .distinctUntilChanged()
                .collectLatest { plan ->
                    if (mode == OfflinePickerMode.IMAGERY || plan == null) {
                        _docInventory.value = null
                        _docCounting.value = false
                        return@collectLatest
                    }
                    _docCounting.value = true
                    delay(INVENTORY_PAUSE_MS)
                    _docInventory.value = runCatching {
                        docStore.inventory(plan.bounds.toDocBounds())
                    }.getOrNull()
                    _docCounting.value = false
                }
        }
    }

    /** A tap on the map: the first corner, then the opposite one. */
    fun tapAt(latitude: Double, longitude: Double) {
        _error.value = null
        _draft.value = _draft.value.withCorner(GeoPoint(latitude, longitude))
    }

    fun clearCorners() {
        _error.value = null
        _draft.value = _draft.value.withoutCorners()
    }

    fun setName(name: String) {
        _draft.value = _draft.value.copy(name = name)
    }

    fun setZoomRange(minZoom: Int, maxZoom: Int) {
        // Capped at what this run will actually fetch, so the count the walker reads is the count they
        // will get: see [deepestZoom].
        _draft.value = _draft.value.withZoomRange(
            minimum = minZoom.coerceAtMost(deepestZoom),
            maximum = maxZoom.coerceAtMost(deepestZoom)
        )
    }

    /**
     * Stores the area and downloads it. Progress lands in the database as it goes, so
     * the card tracks it, and a download that is interrupted resumes from the tiles
     * already on disk.
     */
    fun download() {
        when (mode) {
            OfflinePickerMode.IMAGERY -> downloadImagery()
            OfflinePickerMode.DOC_DATA -> downloadDocData()
            OfflinePickerMode.TOPOGRAPHY -> downloadTopography()
            OfflinePickerMode.AROUND_TRACK -> downloadGround()
        }
    }

    private fun downloadImagery() {
        if (_working.value) return

        val current = _draft.value
        val plan = current.plan(name = current.name, fallbackName = defaultName())
        if (plan == null) {
            _error.value = "Tap two opposite corners on the map to choose the area."
            return
        }
        if (current.isTooBig) {
            _error.value = "That is ${current.tileCount} tiles, more than the " +
                "${OfflineAreaManager.MAX_TILES_PER_AREA} limit. Narrow the zoom range " +
                "or the area."
            return
        }

        viewModelScope.launch {
            _working.value = true
            _error.value = null
            try {
                // Read the key from settings now rather than trusting a cached flow:
                // tapping Download the instant the screen opens must not claim there
                // is no key.
                val key = settings.linzApiKey.first()
                if (key.isBlank()) {
                    _error.value = "Add a LINZ Basemaps key before downloading an area."
                    return@launch
                }

                val area = manager.createArea(plan)
                _activeId.value = area.id
                manager.download(area.id, key)
                _saved.value = true
            } catch (cancelled: CancellationException) {
                // Leaving the screen is not a failure: the tiles already fetched stay,
                // and the area can be resumed from the offline areas list.
                throw cancelled
            } catch (failure: Throwable) {
                _error.value = failure.message ?: "Download failed"
            } finally {
                _working.value = false
            }
        }
    }

    /**
     * Keeps the ground around a track in one act: the imagery that draws it, and everything DOC publishes
     * inside the same box.
     *
     * **The imagery first, then DOC's material**, because the two fail differently: tiles need a LINZ key
     * and are the long download, DOC's records arrive in seconds. Either half arriving is worth keeping on
     * its own - a walk with the map and no huts beats no walk - so a failure in the second is said and the
     * first is already saved.
     */
    private fun downloadGround() {
        if (_working.value || _docWorking.value) return

        val current = _draft.value
        val plan = current.plan(name = current.name, fallbackName = defaultName())
        if (plan == null) {
            _error.value = "There is no track to keep the ground around."
            return
        }
        if (current.isTooBig) {
            _error.value = "That is ${current.tileCount} tiles, more than the " +
                "${OfflineAreaManager.MAX_TILES_PER_AREA} limit. Narrow the zoom range " +
                "or the area."
            return
        }

        viewModelScope.launch {
            _working.value = true
            _error.value = null
            _docMessage.value = null
            try {
                val key = settings.linzApiKey.first()
                if (key.isBlank()) {
                    _error.value = "Add a LINZ Basemaps key before downloading an area."
                    return@launch
                }

                val area = manager.createArea(plan)
                _activeId.value = area.id
                manager.download(area.id, key)

                // The map's own ground is on the phone; now what DOC publishes on it. A failure here is
                // said rather than thrown, and does not take the tiles away.
                _docWorking.value = true
                val download = docStore.download(plan.bounds.toDocBounds())
                _docMessage.value = docDownloadedMessage(download)
                // Leave for the offline screen when there is nothing to answer for; stay when a service
                // could not be read, so the walker reads which one and why.
                _saved.value = download.failed.isEmpty()
            } catch (cancelled: CancellationException) {
                // Leaving the screen is not a failure: the tiles already fetched stay, and the area can be
                // resumed from the offline areas list.
                throw cancelled
            } catch (failure: Throwable) {
                _error.value = failure.message ?: "Download failed"
            } finally {
                _docWorking.value = false
                _working.value = false
            }
        }
    }

    private fun defaultName(): String = "Area " + formatShortDate(System.currentTimeMillis())

    /**
     * Keeps LINZ's vector Topographic map for the box drawn here: its tiles, and the heights its
     * hillshade is shaded from.
     *
     * **Both halves, or the map does not draw.** The tiles say where the roads, the contours and the
     * names are; the heights say what shape the ground is, and the style draws its shade from them. A
     * box with one and not the other is a map with no relief - which on a hillside is the half a walker
     * is looking at.
     *
     * No area record and no name, unlike imagery: these tiles are one cache the whole app reads, wherever
     * the map is drawn, so there is nothing per-area to list or to resume. What is held is counted by the
     * offline screen's own card, which reads the store.
     */
    private fun downloadTopography() {
        if (_working.value) return

        val current = _draft.value
        val plan = current.plan(name = current.name, fallbackName = defaultName())
        if (plan == null) {
            _topoMessage.value = "Tap two opposite corners on the map to choose the area."
            return
        }
        // The draft is already capped at what this pack fetches - see [deepestZoom] - so the plan's own
        // range is the range to ask for.
        val deepest = plan.maxZoom

        viewModelScope.launch {
            _working.value = true
            _topoMessage.value = null
            try {
                val key = settings.linzApiKey.first()
                if (key.isBlank()) {
                    _topoMessage.value = "Add a LINZ Basemaps key before downloading an area."
                    return@launch
                }

                val map = TileDownloader(
                    store = topographyStore,
                    fetcher = LinzTileFetcher(
                        key,
                        urlFor = { zoom, x, y ->
                            Basemap.LINZ_TOPO_LIDAR_HILLSHADE.tileUrl(key, zoom, x, y)
                        },
                        accept = "application/x-protobuf"
                    )
                ).download(bounds = plan.bounds, minZoom = plan.minZoom, maxZoom = deepest)

                val heights = TileDownloader(
                    store = elevationStore,
                    fetcher = LinzTileFetcher(
                        key,
                        urlFor = { zoom, x, y -> Basemap.elevationUrl(key, zoom, x, y) },
                        // The DEM endpoint answers PNG, and the renderer decodes it as PNG.
                        accept = "image/png"
                    )
                ).download(bounds = plan.bounds, minZoom = plan.minZoom, maxZoom = deepest)

                _topoMessage.value = topoDownloadedMessage(map, heights)
                // Leave for the offline screen when the box is as covered as it can be; stay when tiles
                // failed, so the walker reads what went wrong rather than finding out on the hill.
                _saved.value = map.failed == 0 && heights.failed == 0
            } catch (cancelled: CancellationException) {
                // Leaving the screen is not a failure; what already arrived is kept, and a second run
                // picks up from it rather than starting over.
                throw cancelled
            } catch (failure: Throwable) {
                _topoMessage.value = failure.message ?: "Download failed"
            } finally {
                _working.value = false
            }
        }
    }

    /**
     * Downloads everything DOC publishes in the box drawn here, so the browser and the map's reference
     * layer work with no reception.
     *
     * **All four services in one act** - the tracks to walk, the huts and campsites to stop at, the bike
     * locations to ride - because that is what a walker keeps for an area, and asking for them one at a
     * time is the walker doing the app's bookkeeping. Independent of the imagery: no LINZ key is needed,
     * and nothing here is an area record - DOC's cache is the walker's own, held once.
     */
    private fun downloadDocData() {
        if (_docWorking.value) return
        val current = _draft.value
        val plan = current.plan(name = current.name, fallbackName = defaultName())
        if (plan == null) {
            _docMessage.value = "Tap two opposite corners on the map to choose the area."
            return
        }

        viewModelScope.launch {
            _docWorking.value = true
            _docMessage.value = null
            try {
                val download = docStore.download(plan.bounds.toDocBounds())
                // One sentence for the whole act: what arrived, and which of the four did not answer. A
                // download that stopped short still kept what arrived, so the walker stays here and can
                // read it rather than being sent back with half an answer.
                _docMessage.value = docDownloadedMessage(download)
                _saved.value = download.items.isNotEmpty()
            } catch (cancelled: CancellationException) {
                // Leaving the screen is not a failure; what already arrived is kept.
                throw cancelled
            } catch (failure: Throwable) {
                _docMessage.value = failure.message ?: "Download failed"
            } finally {
                _docWorking.value = false
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val LOCATION_TIMEOUT_MS = 5_000L

        /** How much of the world the picker opens on: about 4 km across. */
        private const val START_FRAME_DEGREES = 0.02

        /**
         * How long a drawn box waits before DOC is asked what is in it.
         *
         * A box is drawn corner by corner, and the second corner is the one that makes it a box; this is
         * long enough that a corner being dragged does not ask once a frame.
         */
        private const val INVENTORY_PAUSE_MS = 400L

        private const val OUTLINE_ID = -1L
        private const val OUTLINE_COLOUR = "#FFFFFF"

        /** How large the marker is for a single tapped corner: roughly 60 m across. */
        private const val MARKER_DEGREES = 0.0003

        private fun boxAround(point: GeoPoint, degrees: Double = MARKER_DEGREES) = LatLngBounds(
            minLat = point.lat - degrees,
            minLng = point.lng - degrees,
            maxLat = point.lat + degrees,
            maxLng = point.lng + degrees
        )

        /** A box as a closed loop, which is all the map's line layer needs. */
        private fun outlineOf(bounds: LatLngBounds): List<GeoPoint> = listOf(
            GeoPoint(bounds.minLat, bounds.minLng),
            GeoPoint(bounds.maxLat, bounds.minLng),
            GeoPoint(bounds.maxLat, bounds.maxLng),
            GeoPoint(bounds.minLat, bounds.maxLng),
            GeoPoint(bounds.minLat, bounds.minLng)
        )

        fun factory(
            context: Context,
            mode: OfflinePickerMode,
            aroundAssetId: Long? = null
        ): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val database = TrailDayDatabase.get(appContext)
                    OfflineAreaPickerViewModel(
                        manager = OfflineAreaManager(
                            store = TileServerHolder.imageryStore(appContext),
                            dao = database.offlineAreaDao()
                        ),
                        assetRepository = AssetRepository(database),
                        locationSource = FusedLocationSource(appContext),
                        settings = SettingsRepository(appContext),
                        // The tracks the DOC browser falls back to when the service cannot be reached.
                        docStore = OfflineDocStore(
                            dao = database.offlineDocItemDao(),
                            downloader = DocDownloader.online()
                        ),
                        // The vector map's tiles and the heights its shade is drawn from: the same stores
                        // the tile server serves from, so what is downloaded here is what the map reads.
                        topographyStore = TileServerHolder.topographyStore(appContext),
                        elevationStore = TileServerHolder.elevationStore(appContext),
                        mode = mode,
                        aroundAssetId = aroundAssetId
                    )
                }
            }
        }
    }
}

/** The picker's box as the DOC query's own box: one rectangle, two names. */
private fun LatLngBounds.toDocBounds() = DocBounds(
    minLat = minLat,
    minLng = minLng,
    maxLat = maxLat,
    maxLng = maxLng
)
