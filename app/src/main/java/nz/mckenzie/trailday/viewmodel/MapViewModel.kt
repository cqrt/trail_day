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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetLayer
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.map.AssetGeoJson
import nz.mckenzie.trailday.map.AssetHitTest
import nz.mckenzie.trailday.map.AssetLine
import nz.mckenzie.trailday.tracking.FusedLocationSource
import nz.mckenzie.trailday.tracking.LocationSource
import nz.mckenzie.trailday.tracking.LocationUnavailable
import nz.mckenzie.trailday.tracking.frameOnDevice

/**
 * A camera move the operator asked for, carrying a counter.
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
    /** Injected so a test can count how often a line is re-read from the database. */
    private val loadGeometry: suspend (Long) -> AssetGeometry = assetRepository::getAssetGeometry
) : ViewModel() {

    val linzApiKey: StateFlow<String> = settingsRepository.linzApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    /** Which map to draw under the work: the operator's choice in Settings. */
    val basemap: StateFlow<Basemap> = settingsRepository.basemap
        .stateIn(viewModelScope, SharingStarted.Eagerly, Basemap.DEFAULT)

    /**
     * The layers of the work this map is not drawing.
     *
     * A stored choice rather than a screen's, because it is the same map every time it is opened:
     * an operator who has cleared the roads off it once does not want them back tomorrow. What is
     * held here is what is *hidden*, so the map an install has never touched draws everything -
     * see [AssetLayer].
     */
    val hiddenLayers: StateFlow<Set<AssetLayer>> = settingsRepository.hiddenMapLayers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val assets: StateFlow<List<AssetEntity>> = assetRepository.observeActiveAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val geometryByTrack = MutableStateFlow<Map<Long, AssetGeometry>>(emptyMap())

    /**
     * The frame for the camera when the map opens: the operator's own tracks, else
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
     * The camera move the operator asks for with the locate button.
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
     * Why there is no dot, for the case where the operator asks and the phone cannot say.
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
     * Puts the camera back on the operator.
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
        geometryByTrack
    ) { tracks, geometry ->
        AssetGeoJson.build(
            tracks.map { asset ->
                val assetGeometry = geometry[asset.id] ?: AssetGeometry.NONE
                val shape = AssetShape.fromStorage(asset.shape)
                AssetLine(
                    assetId = asset.id,
                    name = asset.name,
                    colorHex = AssetColors.forKind(AssetKind.fromStorage(asset.kind)),
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

    /** The track under a tap on the map, or null when the tap was not on one. */
    fun assetAt(lat: Double, lng: Double, radiusM: Double = AssetHitTest.DEFAULT_TOLERANCE_M): Long? =
        AssetHitTest.nearest(geometryByTrack.value, lat, lng, radiusM)

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
                        locationSource = FusedLocationSource(appContext)
                    )
                }
            }
        }
    }
}
