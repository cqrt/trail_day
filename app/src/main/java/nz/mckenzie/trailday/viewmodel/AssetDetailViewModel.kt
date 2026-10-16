package nz.mckenzie.trailday.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.data.RecordingRepository
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.SprayRepository
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.ProductQuantityLine
import nz.mckenzie.trailday.data.db.RecordedSessionEntity
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.data.db.SprayEventEntity
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.domain.due.DueInfo
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.estimatedAreaSqm
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.LatLngBounds

/** One spray in an asset's history, with the amounts that went out. */
data class SprayHistoryEntry(
    val event: SprayEventEntity,
    val lines: List<ProductQuantityLine>
)

/**
 * Everything about one asset: its line on the map, when it is next due, what it
 * has been given, and GPX export.
 *
 * It is also where the spray history can be corrected - one entry at a time, or the
 * whole record cleared - which is the same act as setting the asset's colour, since
 * the colour is worked out from that history.
 */
class AssetDetailViewModel(
    private val assetId: Long,
    private val assetRepository: AssetRepository,
    private val sprays: SprayRepository,
    private val recordingsRepository: RecordingRepository,
    settingsRepository: SettingsRepository,
    private val context: Context
) : ViewModel() {

    val apiKey: StateFlow<String> = settingsRepository.linzApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /**
     * Which map to draw under the work: the operator's choice in Settings.
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

    val due: StateFlow<DueInfo?> = assetRepository.observeAssetsWithDue()
        .map { list -> list.firstOrNull { it.asset.id == assetId }?.due }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<List<SprayHistoryEntry>> = sprays.observeSprayEvents(assetId)
        .mapLatest { events ->
            events.map { event -> SprayHistoryEntry(event, sprays.getSprayEventProductLines(event.id)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Every GPS recording made for this asset. The evidence behind the sprays, and the
     * way to check a coverage figure after the fact.
     */
    val recordings: StateFlow<List<RecordedSessionEntity>> =
        recordingsRepository.observeSessionsForTrack(assetId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /**
     * The ground the asset's own spray covers: a ring's measured area, or a line's estimate.
     *
     * Two different claims, and the screen's words say which one it is (see
     * [nz.mckenzie.trailday.domain.asset.AssetPhrase.areaPhrase]). Null when neither is known - a line
     * with no swath width cannot have its area estimated, and nothing is claimed for it.
     */
    val areaSqm: Double?
        get() = track.value?.let { entity ->
            if (entity.groundSqm != null) {
                // Measured from the ring's own corners when it was written, and cached on the row
                // beside the length: an area the operator can take to a client, not a guess.
                entity.groundSqm
            } else {
                entity.swathWidthM?.let { width ->
                    // Both passes of a line sprayed twice go on the ground, so the area counts them:
                    // see estimatedAreaSqm.
                    estimatedAreaSqm(entity.lengthM, width, entity.passesRequired)
                }
            }
        }

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

    /**
     * Takes one spray off the asset's history.
     *
     * The repository puts the asset's last-sprayed date back to whatever is left, so this is
     * also what re-colours the line: remove the only spray and the asset reads red again.
     */
    fun deleteSpray(eventId: Long) {
        viewModelScope.launch {
            runCatching { sprays.deleteSprayEvent(eventId) }
                .onSuccess { removed ->
                    _message.value = if (removed) "Spray removed" else "That spray was already gone"
                }
                .onFailure { _message.value = it.message ?: "Could not remove the spray" }
        }
    }

    /**
     * Clears the whole spray history, for starting an asset's record again.
     *
     * The asset, its line and its recordings are left alone: what goes is every spray recorded
     * against it, and with them the colour the line was earning from them.
     */
    fun clearSprayHistory() {
        viewModelScope.launch {
            runCatching { sprays.deleteSprayHistory(assetId) }
                .onSuccess { removed ->
                    _message.value = when (removed) {
                        0 -> "There was no spray history to clear"
                        1 -> "Spray history cleared \u00b7 1 spray removed"
                        else -> "Spray history cleared \u00b7 $removed sprays removed"
                    }
                }
                .onFailure { _message.value = it.message ?: "Could not clear the spray history" }
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
     * called, so anything arriving here is already fit to store - which also means the
     * interval set here is what the traffic light uses from now on.
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
                        sprays = SprayRepository(database),
                        recordingsRepository = RecordingRepository(database),
                        settingsRepository = SettingsRepository(appContext),
                        context = appContext
                    )
                }
            }
        }
    }
}
