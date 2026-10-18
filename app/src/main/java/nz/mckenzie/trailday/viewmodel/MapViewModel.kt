package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nz.mckenzie.trailday.data.RecordingRepository
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetLayer
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.doc.ArcGisDoc
import nz.mckenzie.trailday.doc.DocDownloader
import nz.mckenzie.trailday.domain.doc.DocCache
import nz.mckenzie.trailday.domain.doc.DocHitTest
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.doc.EmptyDocCache
import nz.mckenzie.trailday.domain.doc.DocPick
import nz.mckenzie.trailday.map.AssetGeoJson
import nz.mckenzie.trailday.map.DocGeoJson
import nz.mckenzie.trailday.map.drawingKind
import nz.mckenzie.trailday.offline.OfflineDocStore
import nz.mckenzie.trailday.map.AssetHitTest
import nz.mckenzie.trailday.map.AssetLine
import nz.mckenzie.trailday.tracking.DevicePosition
import nz.mckenzie.trailday.tracking.FusedLocationSource
import nz.mckenzie.trailday.tracking.LocationSource
import nz.mckenzie.trailday.tracking.LocationUnavailable
import nz.mckenzie.trailday.tracking.frameOnDevice

/**
 * A camera move the walker asked for, carrying a counter.
 *
 * The counter is what makes a second tap do anything at all: two requests to put the camera
 * in the same place are an equal value, and a state flow whose value has not changed does not
 * emit - so tapping "where am I" twice in a row would otherwise move the map once.
 */
data class RecentreRequest(val bounds: LatLngBounds, val count: Int)

/**
 * Feeds the map: every active place, and the GeoJSON MapLibre draws.
 */
class MapViewModel(
    private val assetRepository: AssetRepository,
    private val settingsRepository: SettingsRepository,
    private val locationSource: LocationSource,
    /**
     * DOC's material kept on the phone: the map's reference layer.
     *
     * Read here rather than fetched, because the layer is what has been kept for offline use - which is
     * why it draws in the hills with no reception at all. See [DocGeoJson].
     */
    private val docStore: DocCache = EmptyDocCache,
    /**
     * The places a walk or ride has been recorded for, as they change.
     *
     * A place with a recorded pass is a line that was actually walked, and it is drawn in the recorded green
     * with the gold edge rather than in its kind's colour - see [AssetColors.forPlace]. Fed from the trips
     * rather than stored on the place, so a trip deleted puts the line back to what it was.
     */
    private val recordedFor: Flow<Set<Long>> = flowOf(emptySet()),
    /** Injected so a test can count how often a line is re-read from the database. */
    private val loadGeometry: suspend (Long) -> AssetGeometry = assetRepository::getAssetGeometry
) : ViewModel() {

    val linzApiKey: StateFlow<String> = settingsRepository.linzApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    /** Which map to draw under the work: the walker's choice in Settings. */
    val basemap: StateFlow<Basemap> = settingsRepository.basemap
        .stateIn(viewModelScope, SharingStarted.Eagerly, Basemap.DEFAULT)

    /**
     * The layers of the work this map is not drawing.
     *
     * A stored choice rather than a screen's, because it is the same map every time it is opened:
     * a walker who has cleared the roads off it once does not want them back tomorrow. What is
     * held here is what is *hidden*, so the map an install has never touched draws everything -
     * see [AssetLayer].
     */
    val hiddenLayers: StateFlow<Set<AssetLayer>> = settingsRepository.hiddenMapLayers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * DOC's material, as the map's own document.
     *
     * Re-read whenever the cache changes, so an area downloaded while the app is open appears on the map
     * without a restart - and read from the cache rather than from DOC, because the reference layer is
     * what is on the phone: that is what makes it draw where reception does not.
     */
    private val docItems: StateFlow<List<DocItem>> = docStore.observeCount()
        .map { cached() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val docGeoJson: StateFlow<String> = docItems
        .map { items -> DocGeoJson.build(items) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            AssetGeoJson.build(emptyList())
        )

    /**
     * Everything DOC's cache holds, of every source, or nothing when it cannot be read.
     *
     * A cache that will not read is an empty reference layer rather than a map that will not open: the
     * walker's own work is what this screen is for, and the layer under it is the ground.
     */
    private suspend fun cached(): List<DocItem> =
        runCatching { DocSource.entries.flatMap { source -> docStore.cached(source) } }
            .getOrDefault(emptyList())

    val assets: StateFlow<List<AssetEntity>> = assetRepository.observeActiveAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Where the phone is, for the card. One subscriber to the fixes, and no marker: the marker belongs to
     * the map, which draws one wherever a map is drawn - see `DevicePosition`.
     */
    private val here: StateFlow<GeoPoint?> = DevicePosition.updates(viewModelScope)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val geometryByTrack = MutableStateFlow<Map<Long, AssetGeometry>>(emptyMap())

    /**
     * The places a walk or ride has been made of: the ones the map draws in the recorded green, and the
     * ones whose card says so.
     *
     * Read once from the trips and shared, so the map's colours and the card's words cannot disagree.
     */
    val recordedPlaces: StateFlow<Set<Long>> = recordedFor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())


    /**
     * The thing DOC publishes the walker last tapped, or null.
     *
     * DOC's material is not the walker's to change and has no page of its own, so a tap on it is a question -
     * "what is that?" - and the answer is the card at the bottom of the map. It is held here rather than in
     * the screen so that it survives the map being redrawn, and the next tap that lands on nothing lets it go.
     */
    private val _pickedDoc = MutableStateFlow<DocItem?>(null)
    val pickedDoc: StateFlow<DocItem?> = _pickedDoc

    /** The picture of the picked thing, when the phone kept one: read once, when the thing is picked. */
    private val _pickedPicture = MutableStateFlow<ByteArray?>(null)

    /**
     * The picked thing with how far the phone is from it, measured again on every fix - the card says where
     * the thing is now, not where it was when it was tapped.
     *
     * Declared after [here], which it reads in its initialiser.
     */
    val docPick: StateFlow<DocPick?> = combine(_pickedDoc, _pickedPicture, here) { item, picture, position ->
        item?.let { DocPick(it, position?.let { at -> DocHitTest.distanceTo(it, at) }, picture) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Makes the thing DOC publishes under a tap the card's subject, or puts what is nearest back when the tap
     * landed on nothing.
     *
     * Read from the cache rather than from the map's own flow, so a tap answers from what the phone holds even
     * if nothing is watching the reference layer at that moment - and worked out off the main thread, because
     * DOC's cache can hold thousands of things and a tap must not stop the map drawing.
     */
    suspend fun pickDocAt(lat: Double, lng: Double, radiusM: Double) {
        // What is switched off is not tappable either: DOC's huts go with the huts.
        val hidden = hiddenLayers.value
        val held = withContext(Dispatchers.Default) {
            cached().filter { item -> layerOf(item.source.drawingKind()) !in hidden }
        }
        val item = withContext(Dispatchers.Default) { DocHitTest.nearest(held, lat, lng, radiusM) }
        // The picture is part of the answer, so it is read here rather than by the card: the card draws
        // what it is given, and a phone without pictures answers null without failing the pick.
        val picture = item?.pictureUrl?.let { url -> runCatching { docStore.picture(url) }.getOrNull() }
        _pickedPicture.value = picture
        _pickedDoc.value = item
    }

    /** Lets go of a picked thing, so the card is about what is nearest again. */
    fun clearPickedDoc() {
        _pickedPicture.value = null
        _pickedDoc.value = null
    }



    /**
     * The frame for the camera when the map opens: the walker's own tracks, else
     * where the device is, else nothing (the map keeps its neutral country-wide view).
     *
     * Decided once, by asking the database for the tracks' bounds and only falling back
     * to a fix if there are none. It used to be a combination of live flows, which lost
     * a race: the tracks flow starts empty, so a location fix arriving first won the
     * one-and-only frame and the map stayed on the phone instead of the work.
     */
    private val _initialFrame = MutableStateFlow<LatLngBounds?>(null)
    val initialFrame: StateFlow<LatLngBounds?> = _initialFrame

    /**
     * The camera move the walker asks for with the locate button.
     *
     * Where the phone *is* is not this view model's business any more: the marker belongs to
     * the map, so that every map in the app draws one rather than only the screens that thought
     * to collect a stream. See [nz.mckenzie.trailday.tracking.DevicePosition] and
     * [nz.mckenzie.trailday.map.BasemapView]. What is left here is what this screen alone asks
     * of the location: the frame for a first look, and this - the move that answers a tap.
     */
    private val _recentre = MutableStateFlow<RecentreRequest?>(null)
    val recentre: StateFlow<RecentreRequest?> = _recentre

    private val _locationNotice = MutableStateFlow<String?>(null)

    /**
     * Why there is no dot, for the case where the walker asks and the phone cannot say.
     *
     * The alternative is a button that does nothing visible, which reads as a broken button
     * rather than as an app that is not allowed to know where it is.
     */
    val locationNotice: StateFlow<String?> = _locationNotice

    fun clearLocationNotice() {
        _locationNotice.value = null
    }

    /** The permission was refused, so there will be no marker: the screen says why, once. */
    fun onLocationRefused() {
        _locationNotice.value = LocationUnavailable.MESSAGE
    }

    /**
     * Puts the camera back on the walker.
     *
     * Asked for rather than followed, because this map is also for reading the work: a camera
     * that keeps swinging back to the phone is a camera you cannot pan across a block.
     */
    fun recentreOnDevice() {
        viewModelScope.launch {
            val frame = locationSource.frameOnDevice()
            if (frame == null) {
                _locationNotice.value = LocationUnavailable.MESSAGE
                return@launch
            }
            _locationNotice.value = null
            _recentre.value = RecentreRequest(frame, (_recentre.value?.count ?: 0) + 1)
        }
    }

    val assetGeoJson: StateFlow<String> = combine(
        assets,
        geometryByTrack,
        recordedPlaces
    ) { tracks, geometry, recorded ->
        AssetGeoJson.build(
            tracks.map { asset ->
                val assetGeometry = geometry[asset.id] ?: AssetGeometry.NONE
                val shape = AssetShape.fromStorage(asset.shape)
                AssetLine(
                    assetId = asset.id,
                    name = asset.name,
                    // A line a walk has been made of wears the recorded green; the rest wear their kind's
                    // colour, as they always have.
                    colorHex = AssetColors.forPlace(
                        kind = AssetKind.fromStorage(asset.kind),
                        recorded = asset.id in recorded
                    ),
                    points = assetGeometry.line,
                    sideTracks = assetGeometry.sideTracks,
                    kind = AssetKind.fromStorage(asset.kind),
                    shape = shape
                )
            }
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        AssetGeoJson.build(emptyList())
    )

    init {
        viewModelScope.launch {
            // One decision, in order: the work if there is any, otherwise the phone.
            val fromTracks = runCatching { assetRepository.assetBounds() }.getOrNull()
            _initialFrame.value = fromTracks ?: locationSource.frameOnDevice()
        }

        viewModelScope.launch {
            // The walker's own lines, keyed on what would actually change the drawn lines:
            // which places exist, and how long each line is. Re-reading every line from the
            // database on any change was work on a map that had not changed.
            assets
                .map { places ->
                    places.map {
                        TrackKey(
                            assetId = it.id,
                            lengthM = it.lengthM
                        )
                    }.sortedBy { it.assetId }
                }
                .distinctUntilChanged()
                .collect { keys ->
                    val geometries = keys.associate { it.assetId to loadGeometry(it.assetId) }
                    geometryByTrack.value = geometries
                }
        }
    }

    /**
     * What makes the map re-read from the database: which places exist, and how long each line is.
     */
    private data class TrackKey(
        val assetId: Long,
        val lengthM: Double
    )

    /**
     * The place under a tap on the map, or null when the tap was not on one.
     *
     * **A layer the walker has switched off is not tappable.** A tap where nothing is drawn must not open
     * something they cannot see - the same rule the card follows when it says nothing is there.
     */
    fun assetAt(lat: Double, lng: Double, radiusM: Double = AssetHitTest.DEFAULT_TOLERANCE_M): Long? {
        val hidden = hiddenLayers.value
        val drawn = assets.value
            .filter { place -> layerOf(AssetKind.fromStorage(place.kind)) !in hidden }
            .mapNotNull { place -> geometryByTrack.value[place.id]?.let { place.id to it } }
            .toMap()
        return AssetHitTest.nearest(drawn, lat, lng, radiusM)
    }

    /** The switch that governs [kind]: one layer of the work per kind. */
    private fun layerOf(kind: AssetKind): AssetLayer? =
        AssetLayer.ALL.firstOrNull { it.kind == kind }

    /**
     * Hides or shows one layer of the work, leaving the others as they are.
     *
     * Written straight through to the preference rather than held here and saved later: a map has
     * no Save button, and a choice that is forgotten when the app is closed is worse than no
     * choice at all. The repository does the read and the write together, so two taps in a row
     * accumulate rather than the second undoing the first.
     */
    fun setLayerHidden(layer: AssetLayer, hidden: Boolean) {
        viewModelScope.launch { settingsRepository.hideMapLayer(layer, hidden) }
    }

    /** Puts every layer back on the map: one switch to undo the lot. */
    fun showEveryLayer() {
        viewModelScope.launch { settingsRepository.setHiddenMapLayers(emptySet()) }
    }

    /**
     * Whether LINZ's place names are drawn over the imagery.
     *
     * Eagerly, like the basemap: the map's first draw is built from this, and a style that arrives
     * without names and then grows them is a map that flickers for no reason.
     */
    val placeNames: StateFlow<Boolean> = settingsRepository.placeNames
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Shows or hides the place names, written straight through like the layer switches.
     *
     * This one changes what the style is rather than a property of a layer, so the map reloads its
     * style when it changes - see [nz.mckenzie.trailday.map.BasemapView]. It is a preference, once
     * set, rather than a switch flipped while reading.
     */
    fun setPlaceNames(shown: Boolean) {
        viewModelScope.launch { settingsRepository.setPlaceNames(shown) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    MapViewModel(
                        assetRepository = AssetRepository(TrailDayDatabase.get(appContext)),
                        settingsRepository = SettingsRepository(appContext),
                        locationSource = FusedLocationSource(appContext),
                        docStore = OfflineDocStore(
                            dao = TrailDayDatabase.get(appContext).offlineDocItemDao(),
                            downloader = DocDownloader(ArcGisDoc())
                        ),
                        // The places a walk has been made of, read from the trips themselves: a trip deleted
                        // puts its line back to what it was, with nothing to remember on the place.
                        recordedFor = RecordingRepository(TrailDayDatabase.get(appContext))
                            .observeSessions()
                            .map { sessions -> sessions.mapNotNull { it.assetId }.toSet() }
                    )
                }
            }
        }
    }
}
