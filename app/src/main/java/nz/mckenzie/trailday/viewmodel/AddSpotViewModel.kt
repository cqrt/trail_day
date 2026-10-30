package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetPhrase
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.look.Look
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.map.AssetGeoJson
import nz.mckenzie.trailday.map.AssetLine
import nz.mckenzie.trailday.tracking.FusedLocationSource
import nz.mckenzie.trailday.tracking.LocationSource
import nz.mckenzie.trailday.tracking.frameOnDevice

/**
 * Adds a spot by tapping the map once.
 *
 * A spot is somewhere worth saving - the water, the lunch stop, a campsite, something found - so it
 * is one coordinate and one tap. The app's own huts and campsites are DOC's reference material now,
 * so this screen makes the walker's plain spot and lets the description say what it is.
 *
 * The draft is held in memory only - nothing is written until Save, so a tap that landed in the
 * wrong place leaves no trace in the database.
 */
class AddSpotViewModel(
    private val assetRepository: AssetRepository,
    settingsRepository: SettingsRepository,
    /**
     * Where the phone is, for the first frame: a spot is nearly always dropped where the walker is
     * standing, and opening on a neutral view of the country would be twenty minutes of panning.
     */
    private val locationSource: LocationSource
) : ViewModel() {

    /** Which map to drop the spot on: the walker's choice in Settings. */
    val basemap: StateFlow<Basemap> = settingsRepository.basemap
        .stateIn(viewModelScope, SharingStarted.Eagerly, Basemap.DEFAULT)

    private val _initialFrame = MutableStateFlow<LatLngBounds?>(null)

    /** The frame for the camera when the map opens: around the phone, if it knows. */
    val initialFrame: StateFlow<LatLngBounds?> = _initialFrame

    /**
     * Where the spot is, or null before the map has been tapped.
     *
     * The last tap wins: a spot is one coordinate, so the tap after a wrong one is the correction
     * rather than a second spot.
     */
    private val _spot = MutableStateFlow<GeoPoint?>(null)

    /**
     * The spot as the map draws it, in the waypoint row's colour and glyph.
     *
     * What is being placed is the thing itself, so the walker sees the marker they are about to
     * keep while they place it. The look is combined in rather than read once, so the preview drawn
     * while placing is in the colour the map will draw it in.
     */
    val draftGeoJson: StateFlow<String> = combine(_spot, Look.current) { spot, look ->
        val draft = spot?.let { point ->
            AssetLine(
                assetId = DRAFT_ID,
                name = AssetPhrase.kind(AssetKind.WAYPOINT),
                colorHex = look.colourOf(AssetKind.WAYPOINT).hex,
                points = listOf(point),
                kind = AssetKind.WAYPOINT,
                shape = AssetShape.POINT
            )
        }
        AssetGeoJson.build(listOfNotNull(draft))
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            AssetGeoJson.build(emptyList())
        )

    /** Whether a spot has been placed, which is also whether there is anything to save. */
    val placed: StateFlow<Boolean> = _spot
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    private val _savedAssetId = MutableStateFlow<Long?>(null)

    /**
     * The place just saved, or null when there is nothing to act on.
     *
     * This is a **one-shot signal**, not state: the screen navigates away and then calls
     * [consumeSaveResult]. Without consuming it, a view model that outlives the screen (which is
     * exactly what a ViewModel does) would fire the navigation again the next time the screen is
     * opened, which is what once made this screen flash and close on its second use.
     */
    val savedAssetId: StateFlow<Long?> = _savedAssetId

    /** Call once the save has been acted on, so it cannot fire twice. */
    fun consumeSaveResult() {
        _savedAssetId.value = null
    }

    private val _message = MutableStateFlow<String?>(null)

    /** Something that went wrong, or null. The screen has its own words for everything else. */
    val message: StateFlow<String?> = _message

    /** Opens the map where the walker is, once the location is known. */
    init {
        viewModelScope.launch {
            _initialFrame.value = locationSource.frameOnDevice()
        }
    }

    /** Drops the spot where the map was tapped, moving it if there was one already. */
    fun dropSpot(latitude: Double, longitude: Double) {
        _spot.value = GeoPoint(lat = latitude, lng = longitude)
        _message.value = null
    }

    /**
     * Keeps the spot under [name], with [description] and an optional [photo].
     *
     * The description is the place's existing notes field: one place for the walker's words about
     * a spot, whether they arrived here or through the editor. The photo is stored after the place
     * has an id; a picture that cannot be stored must not turn a saved place into a second one.
     */
    fun save(name: String, description: String = "", photo: ByteArray? = null) {
        val spot = _spot.value
        if (spot == null) {
            _message.value = "Tap the map where it is, then save"
            return
        }
        viewModelScope.launch {
            runCatching {
                assetRepository.createAsset(
                    name = name.trim().ifBlank { "New ${AssetPhrase.kind(AssetKind.WAYPOINT)}" },
                    geometry = AssetGeometry(listOf(listOf(spot))),
                    kind = AssetKind.WAYPOINT,
                    shape = AssetShape.POINT,
                    notes = description.trim().ifBlank { null }
                )
            }.onSuccess { assetId ->
                if (photo != null) {
                    runCatching { assetRepository.saveAssetPhoto(assetId, photo) }
                }
                _savedAssetId.value = assetId
            }.onFailure { failure ->
                _message.value = failure.message ?: "Could not save it"
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** The id the not-yet-saved spot is drawn under; nothing is stored with it. */
        private const val DRAFT_ID = -1L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    AddSpotViewModel(
                        assetRepository = AssetRepository(TrailDayDatabase.get(appContext)),
                        settingsRepository = SettingsRepository(appContext),
                        locationSource = FusedLocationSource(appContext)
                    )
                }
            }
        }
    }
}
