package nz.mckenzie.trailday.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.tracking.DevicePosition
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.doc.ArcGisDoc
import nz.mckenzie.trailday.doc.DocDownloader
import nz.mckenzie.trailday.offline.OfflineDocStore
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetKindFilter
import nz.mckenzie.trailday.domain.doc.DocCache
import nz.mckenzie.trailday.domain.doc.DocHitTest
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.doc.EmptyDocCache

/**
 * The asset library: every planned asset, plus GPX import.
 */
class AssetListViewModel(
    private val assetRepository: AssetRepository,
    private val context: Context,
    /**
     * Where the one-time tidy is remembered. Built from the context by default so a test that has no
     * opinion about it - or no need of one - does not have to hand one over.
     */
    private val settings: SettingsRepository = SettingsRepository(context),
    /**
     * What DOC publishes, kept on the phone: the list's own small group of tracks near the walker reads it,
     * so the group works with no reception and offers nothing the phone cannot draw.
     */
    private val docStore: DocCache = EmptyDocCache
) : ViewModel() {

    val assets: StateFlow<List<AssetEntity>> = assetRepository.observeActiveAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * DOC's tracks near the phone, for the list's own small group: **what is kept on the phone**, nearest
     * first, five of them inside ten kilometres.
     *
     * Not all of DOC's material and not a second list to scroll: the list is the walker's own places, and the
     * group is there so the track at the end of the valley is a tap away without leaving the page. The full
     * catalogue stays in "What DOC publishes".
     */
    val docNearby: StateFlow<List<DocChoice>> = docStore.observeCount()
        .map { docStore.cached(DocSource.TRACKS) }
        .combine(DevicePosition.updates(viewModelScope)) { tracks, position ->
            if (position == null) {
                emptyList()
            } else {
                tracks
                    .mapNotNull { item ->
                        val distance = DocHitTest.distanceTo(item, position) ?: return@mapNotNull null
                        if (distance <= DOC_NEARBY_M) DocChoice(item, distance) else null
                    }
                    .sortedBy { it.distanceM }
                    .take(DOC_NEARBY)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** One row of the nearby group: a track of DOC's, and how far away its line is. */
    data class DocChoice(val item: DocItem, val distanceM: Double)

    /**
     * What the list is showing: everything, or the one kind the chips are asking about.
     *
     * Held here rather than in the screen so it survives the list being rebuilt and a turned phone,
     * and not in a preference because it is a question being asked now rather than a way of working
     * - see [AssetKindFilter]. Nothing is stored, so an install that never taps a chip is unchanged.
     */
    private val _filter = MutableStateFlow(AssetKindFilter.All)
    val filter: StateFlow<AssetKindFilter> = _filter

    /**
     * What the walker has typed into the list's search: the list narrows to the places whose name holds it.
     *
     * **Local and instant**, unlike the browser's search of DOC: this is the walker's own list, which is on
     * the phone already, so a keystroke is a filter rather than a question sent anywhere. The one grouped
     * search over these places and DOC's material is still to come - see the plan - because a DOC result
     * with no page to open would be a name with nowhere to go.
     */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    fun search(text: String) {
        _query.value = text
    }

    /**
     * The rows the list is built from: the whole list, one kind of it, or what the search leaves.
     *
     * Filtered here rather than in the screen so the rows and the "nothing of that kind" line are
     * both reading the same list - and so the rule can be tested without a screen.
     */
    val shown: StateFlow<List<AssetEntity>> = combine(assets, _filter, _query) { all, filter, query ->
        val typed = query.trim()
        all.filter { asset ->
            filter.keeps(asset.kind) &&
                (typed.isEmpty() || asset.name.contains(typed, ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Taps a kind chip, or the *All* chip when [kind] is null.
     *
     * Tapping the chip already showing goes back to everything: the chips are the only way into this
     * and so one of them has to be the way out.
     */
    fun showOnly(kind: AssetKind?) {
        _filter.update { showing -> AssetKindFilter.afterTapping(kind, showing) }
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /**
     * The one-time tidy: the places an earlier build copied out of DOC leave the walker's list.
     *
     * **Here, on the list they are leaving**, rather than at startup somewhere out of sight: the number
     * gone is said on the very screen the walker would have looked for them on. It runs once and then
     * writes that down, whether or not there was anything to drop, so an install does not count DOC's
     * rows on every visit.
     *
     * Below the message it writes to, deliberately: a coroutine launched before that property is built
     * would be writing to a null.
     */
    init {
        viewModelScope.launch {
            if (settings.docPlacesDropped.first()) return@launch
            val dropped = runCatching { assetRepository.dropPlacesFromDoc() }.getOrDefault(0)
            settings.setDocPlacesDropped(true)
            if (dropped > 0) _message.value = docPlacesMovedMessage(dropped)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /**
     * Imports a GPX, KML, KMZ or GeoJSON file chosen through the system file picker.
     *
     * Failures are surfaced as messages rather than crashes: a malformed or
     * empty track file is a normal thing for a walker to pick by mistake.
     */
    fun importTrackFile(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                } ?: throw IllegalStateException("Could not open the selected file")

                val name = displayName(uri)
                    ?.substringBeforeLast('.')
                    ?.takeIf { it.isNotBlank() }
                    ?: "Imported track"

                val imported = assetRepository.importAssetTracks(fileName = name, file = bytes)
                // The points are the reading's own, so the sentence counts what the file held rather
                // than asking the database again for a number the import has just been handed.
                _message.value = if (imported.size == 1) {
                    val track = imported.single()
                    val points = track.reading.paths.sumOf { it.size }
                    when {
                        track.reading.segmentsDidNotMeet ->
                            "Imported \"${track.name}\" with $points points as one line: the file's " +
                                "own track segments do not meet, so they were joined up."
                        track.reading.sideTracks == 1 ->
                            "Imported \"${track.name}\" with $points points: the line and 1 side track."
                        track.reading.sideTracks > 1 ->
                            "Imported \"${track.name}\" with $points points: the line and " +
                                "${track.reading.sideTracks} side tracks."
                        else -> "Imported \"${track.name}\" with $points points"
                    }
                } else {
                    // A file of named tracks - a GeoJSON collection - arrives as that many assets, so
                    // the sentence says how many rather than pretending there was only ever one.
                    val points = imported.sumOf { track -> track.reading.paths.sumOf { it.size } }
                    "Imported ${imported.size} tracks, $points points between them"
                }
            } catch (failure: Throwable) {
                _message.value = failure.message ?: "Import failed"
            } finally {
                _busy.value = false
            }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

    /** How far around the phone the nearby group looks: ten kilometres, and five tracks of DOC's. */
    private const val DOC_NEARBY_M = 10_000.0
    private const val DOC_NEARBY = 5

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    AssetListViewModel(
                        assetRepository = AssetRepository(TrailDayDatabase.get(appContext)),
                        context = appContext,
                        // The tracks DOC has published for this area, for the nearby group.
                        docStore = OfflineDocStore(
                            dao = TrailDayDatabase.get(appContext).offlineDocItemDao(),
                            downloader = DocDownloader(ArcGisDoc())
                        )
                    )
                }
            }
        }
    }
}
/**
 * What the walker is told when places that came from DOC leave their list.
 *
 * The number is the point of the sentence: a list that quietly gets shorter is a list somebody stops
 * trusting. What follows says where they went and why they can be had again - DOC's material is the
 * map's own layer now, drawn under the walker's work, and it is a download away rather than lost.
 */
internal fun docPlacesMovedMessage(count: Int): String {
    val what = if (count == 1) "1 place" else "$count places"
    val verb = if (count == 1) "is" else "are"
    return "$what that came from DOC $verb no longer in your list. DOC's own tracks, huts, campsites " +
        "and bike locations are drawn on the map as its own layer, and can be fetched again whenever " +
        "you like."
}