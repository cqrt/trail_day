package nz.mckenzie.trailday.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.data.RecordingRepository
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.RecordedSessionEntity
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.LatLngBounds

/**
 * Everything about one place: its line on the map, and GPX export.
 *
 * It is also where an edited place is saved, and where the place itself can be deleted.
 */
class AssetDetailViewModel(
    private val assetId: Long,
    private val assetRepository: AssetRepository,
    private val recordingsRepository: RecordingRepository,
    settingsRepository: SettingsRepository,
    private val context: Context
) : ViewModel() {

    val apiKey: StateFlow<String> = settingsRepository.linzApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /**
     * Which map to draw under the work: the walker's choice in Settings.
     *
     * Read here rather than handed in per screen, so a map added later cannot quietly ignore it.
     */
    val basemap: StateFlow<Basemap> = settingsRepository.basemap
        .stateIn(viewModelScope, SharingStarted.Eagerly, Basemap.DEFAULT)

    val track: StateFlow<AssetEntity?> = assetRepository.observeAsset(assetId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The asset, ready for the edit form. Null until it is known. */
    val editDraft: StateFlow<AssetEntity?> = assetRepository.observeAsset(assetId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val geometry: StateFlow<AssetGeometry> = assetRepository.observeAssetGeometry(assetId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AssetGeometry.NONE)

    val bounds: StateFlow<LatLngBounds?> = geometry
        .map { assetGeometry ->
            val points = assetGeometry.points
            if (points.isEmpty()) {
                null
            } else {
                // The asset's own box, exactly: a place's is a single coordinate with no size at
                // all. What keeps that sane is the page's own limit on how close its map may sit
                // (see ASSET_PAGE_MAX_ZOOM) rather than a box inflated here, because a camera told
                // to fit a box with no size goes past the imagery and comes back green.
                LatLngBounds(
                    minLat = points.minOf { it.lat },
                    minLng = points.minOf { it.lng },
                    maxLat = points.maxOf { it.lat },
                    maxLng = points.maxOf { it.lng }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Every GPS recording made for this asset. The evidence of the walks and rides it has
     * been followed on.
     */
    val recordings: StateFlow<List<RecordedSessionEntity>> =
        recordingsRepository.observeSessionsForTrack(assetId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun exportGpx(uri: Uri) {
        viewModelScope.launch {
            try {
                val gpx = assetRepository.exportAssetGpx(assetId)
                    ?: error("That track no longer exists")
                context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    output.write(gpx.toByteArray(Charsets.UTF_8))
                } ?: error("Could not open the destination file")
                _message.value = "Exported ${track.value?.name ?: "track"} as GPX"
            } catch (failure: Throwable) {
                _message.value = failure.message ?: "Export failed"
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching { assetRepository.deleteAsset(assetId) }
                .onFailure { _message.value = it.message ?: "Could not delete the track" }
        }
    }

    /**
     * Stores edits made on this screen.
     *
     * The fields were checked by [nz.mckenzie.trailday.ui.AssetEdits] before this is
     * called, so anything arriving here is already fit to store.
     */
    fun save(asset: AssetEntity) {
        viewModelScope.launch {
            runCatching { assetRepository.saveAssetEdits(asset) }
                .onSuccess { _message.value = "Saved" }
                .onFailure { _message.value = it.message ?: "Could not save the track" }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(context: Context, assetId: Long): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val database = TrailDayDatabase.get(appContext)
                    AssetDetailViewModel(
                        assetId = assetId,
                        assetRepository = AssetRepository(database),
                        recordingsRepository = RecordingRepository(database),
                        settingsRepository = SettingsRepository(appContext),
                        context = appContext
                    )
                }
            }
        }
    }
}
