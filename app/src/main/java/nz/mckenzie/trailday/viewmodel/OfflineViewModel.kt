package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.doc.DocDownloader
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.offline.OfflineArea
import nz.mckenzie.trailday.offline.OfflineAreaManager
import nz.mckenzie.trailday.offline.OfflineDocStore
import nz.mckenzie.trailday.offline.OfflineTileStore
import nz.mckenzie.trailday.offline.TileServerHolder
import nz.mckenzie.trailday.offline.TileStoreSummary

/**
 * The offline screen: the two caches, and what can be done with what is in them.
 *
 * Choosing an area is **not** done here any more - it is the map picker, reached from either tab -
 * so this manages only what is already stored: the imagery areas (resume, delete, clear) and the DOC
 * tracks (count, clear). The two caches are independent, so they are managed independently, and the
 * screen shows one tab each.
 */
class OfflineViewModel(
    private val manager: OfflineAreaManager,
    private val settings: SettingsRepository,
    private val docStore: OfflineDocStore,
    /** The drawn map's tiles, and the heights its hillshade is drawn from, so the screen can count them. */
    private val topographyStore: OfflineTileStore,
    private val elevationStore: OfflineTileStore
) : ViewModel() {

    val apiKey: StateFlow<String> = settings.linzApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** The imagery areas on the device. Chosen elsewhere; managed here. */
    val stored: StateFlow<List<OfflineArea>> = manager.observeAreas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working

    private val _summary = MutableStateFlow(TileStoreSummary(0, 0))
    val summary: StateFlow<TileStoreSummary> = _summary

    private val _topoSummary = MutableStateFlow(TileStoreSummary(0, 0))

    /**
     * What the drawn Topographic map holds: its tiles, and the heights its shade is drawn from.
     *
     * Counted together because they are one thing to a walker - the map either draws this ground or it
     * does not - and because a box with one and not the other is a map with no relief.
     */
    val topoSummary: StateFlow<TileStoreSummary> = _topoSummary

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** How many things from DOC are kept for offline use, live from the database. All four services. */
    val docDataCount: StateFlow<Int> = docStore.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    private val _docBounds = MutableStateFlow<LatLngBounds?>(null)

    /** The ground the downloaded DOC data covers, for the tab's map. Null when nothing is downloaded. */
    val docBounds: StateFlow<LatLngBounds?> = _docBounds

    private val _docCounts = MutableStateFlow<Map<DocSource, Int>>(emptyMap())

    /**
     * How many are held of each of DOC's services, for the tab's own list: "412 tracks, 38 huts" says
     * what is on the phone in a way "543 things" does not, and it is the shape a walker checks their own
     * area against.
     */
    val docCounts: StateFlow<Map<DocSource, Int>> = _docCounts

    /**
     * How many pictures of DOC's material are kept: they arrive with the material and go with it.
     *
     * Live from the database rather than refreshed with the material's count, because the two move at
     * different moments - the records land first and the pictures follow.
     */
    val docPictures: StateFlow<Int> = docStore.observePictureCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    init {
        refreshSummary()
        refreshTopography()

        // Keep "what is on the device" honest when an area is downloaded from the picker: its
        // download left this figure stale. Refreshed when the set of areas changes or one finishes -
        // not on every progress write, which would walk the tile directory once a second.
        viewModelScope.launch {
            var previous = emptyList<OfflineArea>()
            manager.observeAreas().collect { areas ->
                val somethingFinished = areas.any { area ->
                    area.isComplete && previous.none { it.id == area.id && it.isComplete }
                }
                val setChanged = areas.size != previous.size
                previous = areas
                if (somethingFinished || setChanged) refreshSummary()
            }
        }

        // The DOC cache's own box and breakdown, refreshed when the count changes rather than on every
        // frame: working out the box reads and parses every cached thing to find the extent.
        viewModelScope.launch {
            docStore.observeCount().collect { count ->
                _docBounds.value = if (count == 0) {
                    null
                } else {
                    runCatching { docStore.bounds() }.getOrNull()
                }
                _docCounts.value = if (count == 0) {
                    emptyMap()
                } else {
                    runCatching { docStore.counts() }.getOrDefault(emptyMap())
                }
            }
        }
    }

    /** Continues an imagery area that stopped short; tiles already held are skipped. */
    fun resume(areaId: Long) {
        if (_working.value) return
        viewModelScope.launch {
            _working.value = true
            _error.value = null
            try {
                // Read the key now rather than from a cached flow: the button can be tapped before
                // the first read has landed.
                val key = settings.linzApiKey.first()
                if (key.isBlank()) {
                    _error.value = "Add a LINZ Basemaps key before downloading an area."
                    return@launch
                }
                manager.download(areaId, key)
            } catch (cancelled: CancellationException) {
                // Leaving the screen is not a failure; the download resumes later.
                throw cancelled
            } catch (failure: Throwable) {
                _error.value = failure.message ?: "Download failed"
            } finally {
                _working.value = false
                refreshSummary()
            }
        }
    }

    /** Forgets an imagery area. The tiles stay: they are shared, and this frees no space. */
    fun delete(areaId: Long) {
        viewModelScope.launch {
            runCatching { manager.deleteArea(areaId) }
            refreshSummary()
        }
    }

    /** Reclaims the disk space held by every downloaded tile. */
    fun clearTiles() {
        viewModelScope.launch {
            runCatching { manager.clearTiles() }
            refreshSummary()
        }
    }

    /** Forgets everything kept from DOC. Costs nothing but the download. */
    fun clearDocData() {
        viewModelScope.launch {
            runCatching { docStore.clear() }
        }
    }

    private fun refreshSummary() {
        viewModelScope.launch {
            _summary.value = runCatching { manager.summary() }.getOrDefault(TileStoreSummary(0, 0))
        }
    }

    /**
     * Counts what the drawn map holds.
     *
     * Asked for by the screen as it appears rather than collected, because there is nothing to collect:
     * the map's tiles are one cache with no rows to watch, so the honest moment to count them is when
     * the walker is looking at the count - which is also just after a download from the picker.
     */
    fun refreshTopography() {
        viewModelScope.launch {
            _topoSummary.value = runCatching {
                TileStoreSummary(
                    tiles = topographyStore.storedTileCount() + elevationStore.storedTileCount(),
                    bytes = topographyStore.storedBytes() + elevationStore.storedBytes()
                )
            }.getOrDefault(TileStoreSummary(0, 0))
        }
    }

    /** Reclaims the space the drawn map holds. It is fetched again as the map is looked at. */
    fun clearTopography() {
        viewModelScope.launch {
            runCatching {
                topographyStore.deleteAll()
                elevationStore.deleteAll()
            }
            refreshTopography()
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val database = TrailDayDatabase.get(appContext)
                    OfflineViewModel(
                        manager = OfflineAreaManager(
                            // The same store the tile server serves from, so what
                            // is downloaded here is what the map reads offline.
                            store = TileServerHolder.imageryStore(appContext),
                            dao = database.offlineAreaDao()
                        ),
                        settings = SettingsRepository(appContext),
                        // DOC's tracks, downloaded by the picker into the same cache the browsers
                        // fall back to when the service cannot be reached.
                        docStore = OfflineDocStore(
                            dao = database.offlineDocItemDao(),
                            downloader = DocDownloader.online()
                        ),
                        // The drawn map's two stores, counted by the screen's own card.
                        topographyStore = TileServerHolder.topographyStore(appContext),
                        elevationStore = TileServerHolder.elevationStore(appContext)
                    )
                }
            }
        }
    }
}
