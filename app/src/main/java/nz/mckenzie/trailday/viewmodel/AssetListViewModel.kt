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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.ImportedTrack
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.track.FileTracks
import nz.mckenzie.trailday.ui.ImportWords
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetKindFilter

/**
 * The place library: every planned asset, plus GPX import.
 *
 * **The walker's own places and nothing else.** This used to carry a small group of DOC's tracks near the
 * phone, so a track at the end of the valley was a tap away; DOC's material is read in **Explore** now,
 * beside the other publisher's, and a page of your own places is not the place to mix in somebody
 * else's. Removing it took the DOC cache, the phone's position and the distance test out of this class
 * with it.
 */
class AssetListViewModel(
    private val assetRepository: AssetRepository,
    private val context: Context,
    /**
     * Where the one-time tidy is remembered. Built from the context by default so a test that has no
     * opinion about it - or no need of one - does not have to hand one over.
     */
    private val settings: SettingsRepository = SettingsRepository(context)
) : ViewModel() {

    val assets: StateFlow<List<AssetEntity>> = assetRepository.observeActiveAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

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
            // **Journeys are the page's own group, not rows in it.** A journey is a track, so without
            // this it would appear twice: once as a place and once as a journey, the same walk under two
            // headings, which reads as two walks.
            asset.journey == null &&
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
     * Imports GPX, KML, KMZ or GeoJSON files chosen through the system file picker.
     *
     * **Several at once**, because a walker with a week of files has a week of files: each is read, each
     * track in each becomes a place, and the walker is given one sentence about the lot at the end.
     * A file that will not read is named in that sentence rather than stopping the rest.
     *
     * Failures are messages rather than crashes: a malformed or empty track file is a normal thing for
     * a walker to pick by mistake.
     */
    fun importTrackFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val picked = uris.map { uri -> pickFile(context, uri) }
                val written = mutableListOf<ImportedTrack>()
                val failures = mutableListOf<String>()

                for (one in picked) {
                    when (one) {
                        is Picked.Unreadable -> failures += one.fileName
                        is Picked.Read -> {
                            // The reading is the same one Activities uses: one file, one meaning,
                            // whichever button sent it here.
                            val read = runCatching {
                                FileTracks.read(fileName = one.file.baseName, bytes = one.file.bytes)
                            }
                            if (read.isFailure) {
                                failures += one.file.fileName
                                continue
                            }
                            written += assetRepository.createPlacesFrom(read.getOrThrow())
                        }
                    }
                }

                _message.value = placesMessage(
                    written = written,
                    files = picked.size,
                    failures = failures
                )
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * What the walker is told after a Places import.
     *
     * One file of one track keeps the sentence Places has always said, points and side tracks and all,
     * because that is the common case and it carries detail a count cannot. Anything more is counted.
     */
    private fun placesMessage(
        written: List<ImportedTrack>,
        files: Int,
        failures: List<String>
    ): String {
        val one = written.singleOrNull()
        if (one != null && files == 1 && failures.isEmpty()) {
            return ImportWords.onePlace(
                trackName = one.name,
                points = one.reading.paths.sumOf { it.size },
                sideTracks = one.reading.sideTracks,
                segmentsDidNotMeet = one.reading.segmentsDidNotMeet
            )
        }
        return ImportWords.summary(
            walks = 0,
            rides = 0,
            tracks = written.size,
            files = files,
            failures = failures
        )
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    AssetListViewModel(
                        assetRepository = AssetRepository(TrailDayDatabase.get(appContext)),
                        context = appContext
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
        "and bike trails are drawn on the map as its own layer, and can be fetched again whenever " +
        "you like."
}