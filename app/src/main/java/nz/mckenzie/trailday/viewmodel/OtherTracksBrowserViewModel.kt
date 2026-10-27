package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.other.OTHER_TRACK_FILTERS
import nz.mckenzie.trailday.domain.other.OTHER_TRACK_REF_PREFIX
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.domain.other.OtherTracksCache
import nz.mckenzie.trailday.domain.other.OtherTracksQuery
import nz.mckenzie.trailday.domain.other.matching
import nz.mckenzie.trailday.domain.other.otherTrackSourceRef
import nz.mckenzie.trailday.domain.other.showing
import nz.mckenzie.trailday.domain.other.valuesPresent
import nz.mckenzie.trailday.domain.other.within
import nz.mckenzie.trailday.domain.reference.ReferenceFilter
import nz.mckenzie.trailday.domain.reference.ReferenceSort
import nz.mckenzie.trailday.domain.tiles.boxAround
import nz.mckenzie.trailday.offline.OfflineOtherTrackStore
import nz.mckenzie.trailday.other.ArcGisOtherTracks
import nz.mckenzie.trailday.other.OtherTracksReadResult
import nz.mckenzie.trailday.other.OtherTracksReader
import nz.mckenzie.trailday.tracking.DevicePosition

/**
 * Reading the tracks other people publish: the national tracks dataset, searched and narrowed.
 *
 * The same page DOC's browser is, over a second publisher: the search happens at the service, which holds
 * seventeen thousand of these and is asked for the few the walker is looking at, and the filters and the
 * order are applied to the page already here rather than asking again.
 *
 * **When the service cannot be reached, what has been downloaded is read instead** - filtered by name,
 * because the download is the place filter and the cache only holds the areas asked for. That is the same
 * bargain DOC's browser makes, and it is what the offline area download is for.
 *
 * A row can also be **taken on as the walker's own place**, which is the one thing this screen writes: see
 * [import]. DOC's material is read and never kept, and this is the difference between the two - the plan's
 * decision, not an accident.
 */
class OtherTracksBrowserViewModel(
    private val reader: OtherTracksReader,
    /** What has been downloaded for offline use, read when the service cannot be reached. */
    private val cache: OtherTracksCache,
    private val assetRepository: AssetRepository
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _nearMe = MutableStateFlow(false)
    val nearMe: StateFlow<Boolean> = _nearMe

    /** The kilometres around the phone "Near me" looks, as chosen; nonsense falls back to the default. */
    private val _radiusKm = MutableStateFlow(DEFAULT_RADIUS_KM.toInt().toString())
    val radiusKm: StateFlow<String> = _radiusKm

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _results = MutableStateFlow<List<OtherTrack>>(emptyList())
    val results: StateFlow<List<OtherTrack>> = _results

    /** Whether a search has been made at all, so the screen can tell "no results" from "not yet". */
    private val _searched = MutableStateFlow(false)
    val searched: StateFlow<Boolean> = _searched

    /** The newest fix, so "Near me" searches from where the phone is. Null until there is one. */
    private val fix = MutableStateFlow<GeoPoint?>(null)

    /** The phone's newest fix, for a row that wants to say how far away its track is. */
    val phoneFix: StateFlow<GeoPoint?> = fix

    /** What the walker has narrowed the page to, by the field each filter is on. */
    private val _filters = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val filters: StateFlow<Map<String, Set<String>>> = _filters

    private val _sort = MutableStateFlow(ReferenceSort.NEAREST)
    val sort: StateFlow<ReferenceSort> = _sort

    /**
     * What each filter should offer, from the page that came back.
     *
     * Built from the results rather than from the dataset's own domain, so every chip is a fact about
     * something on the screen and a chip that matches nothing cannot be offered.
     */
    val filterChoices: StateFlow<List<Pair<ReferenceFilter, List<String>>>> = _results
        .map { page ->
            OTHER_TRACK_FILTERS.map { filter -> filter to page.valuesPresent(filter.field) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Whether the page is narrowed to the tracks a bike may be ridden on. */
    private val _bikeOnly = MutableStateFlow(false)
    val bikeOnly: StateFlow<Boolean> = _bikeOnly

    /** Whether the page is narrowed to the tracks a wheelchair or pushchair can use. */
    private val _wheelchairOnly = MutableStateFlow(false)
    val wheelchairOnly: StateFlow<Boolean> = _wheelchairOnly

    /**
     * The rows the screen shows: the search's results, narrowed by the filters and the two chips, ordered
     * by the sort.
     *
     * Derived rather than stored, because the fix moves: standing still and turning "Nearest" on re-orders
     * the page without asking the service again.
     *
     * The two chips are not entries in the filter menu, and that is the same choice DOC's browser makes
     * with its own: what a chip is about - may I ride this, can a wheelchair use it - is a *question* about
     * a row rather than a value the dataset published, so it has no field for a filter to be over.
     */
    val visible: StateFlow<List<OtherTrack>> =
        combine(
            _results,
            _filters,
            _sort,
            fix,
            combine(_bikeOnly, _wheelchairOnly) { bike, wheelchair -> bike to wheelchair }
        ) { results, filters, sort, position, chips ->
            val (bikeOnly, wheelchairOnly) = chips
            results
                .showing(filters, sort, position)
                .filter { track -> !bikeOnly || track.isRideable }
                .filter { track -> !wheelchairOnly || track.suitsWheelchair }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** The tracks of this layer that are already the walker's own places, by source reference. */
    private val _imported = MutableStateFlow<Set<String>>(emptySet())
    val imported: StateFlow<Set<String>> = _imported

    init {
        viewModelScope.launch {
            DevicePosition.updates(viewModelScope).collect { position -> fix.value = position }
        }
        viewModelScope.launch {
            // Read once rather than per row: a page is a few hundred candidates and each asks the same
            // question - is this one already here - which is not a query per row.
            _imported.value = assetRepository.existingSourceRefs()
                .filterTo(mutableSetOf()) { ref -> ref.startsWith(OTHER_TRACK_REF_PREFIX) }
        }
    }

    fun onNameChange(value: String) {
        _name.value = value
    }

    fun onNearMeChange(value: Boolean) {
        _nearMe.value = value
    }

    fun onRadiusChange(value: String) {
        _radiusKm.value = value
    }

    /** Tick or untick one value of one filter; a filter with nothing ticked shows everything. */
    fun toggleFilter(filter: ReferenceFilter, value: String) {
        _filters.update { current ->
            val chosen = current[filter.field].orEmpty()
            val next = if (value in chosen) chosen - value else chosen + value
            if (next.isEmpty()) current - filter.field else current + (filter.field to next)
        }
    }

    fun clearFilters() {
        _filters.value = emptyMap()
    }

    fun setSort(sort: ReferenceSort) {
        _sort.value = sort
    }

    /** Narrows the page to the tracks a bike may be ridden on. */
    fun setBikeOnly(value: Boolean) {
        _bikeOnly.value = value
    }

    /** Narrows the page to the tracks a wheelchair or pushchair can use. */
    fun setWheelchairOnly(value: Boolean) {
        _wheelchairOnly.value = value
    }

    fun search() {
        val nearMe = _nearMe.value
        val position = if (nearMe) fix.value else null
        if (nearMe && position == null) {
            // Near me with no fix is not a name search: the place is the whole question, and dropping it
            // silently is how a track 80 km away turns up under a 50 km radius.
            _message.value = "Waiting for a location fix. Try again in a moment, or turn off Near me " +
                "to search by name."
            return
        }
        val name = _name.value.trim()
        if (name.isEmpty() && position == null) {
            _message.value = "Type part of a name, or turn on Near me."
            return
        }
        val radius = _radiusKm.value.trim().toDoubleOrNull()?.takeIf { it > 0.0 } ?: DEFAULT_RADIUS_KM

        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            val query = OtherTracksQuery(
                nameContains = name,
                // The service takes a box, and a circle is what was meant: the box is the quick filter
                // and the corners are trimmed below, as DOC's search does.
                bounds = position?.let { at -> boxAround(at, radius) }
            )

            when (val result = reader.read(query)) {
                is OtherTracksReadResult.Found -> {
                    val page = result.tracks.within(radius * 1000.0, position)
                    _results.value = page
                    // A new set of results, so a filter from the last search is not carried onto it.
                    _filters.value = emptyMap()
                    _searched.value = true
                    _message.value = referencePageMessage(
                        sent = result.tracks.size,
                        shown = page.size,
                        limit = OtherTracksQuery.DEFAULT_LIMIT
                    )
                }

                is OtherTracksReadResult.Failed -> {
                    // No service: fall back to what was downloaded for offline use, so what is on the
                    // phone can still be read with no reception.
                    val held = cache.cached()
                        .matching(name)
                        .within(radius * 1000.0, position)
                    _results.value = held
                    _filters.value = emptyMap()
                    _searched.value = true
                    _message.value = when {
                        held.isNotEmpty() -> "Could not reach the tracks service. Showing " +
                            "${held.size} downloaded ${if (held.size == 1) "track" else "tracks"}."

                        cache.count() > 0 -> "Could not reach the tracks service, and nothing " +
                            "downloaded matches."

                        else -> result.message
                    }
                }
            }
            _busy.value = false
        }
    }

    /**
     * Takes one of these tracks on as the walker's own place, and says whether it did.
     *
     * **The one thing on this screen that writes.** A track somebody else publishes is read from the
     * service and drawn as a reference; a track the walker takes on becomes a place of their own - named,
     * drawn, editable, sprayed, blocked, exported - which is what "I want this one" means.
     *
     * It records an **exact source reference**, the same way a DOC track imported by the desk does, so the
     * same track cannot be taken on twice: the row says *already yours*, the button is off, and this
     * refuses it at the door that writes as well as in the list.
     */
    suspend fun import(track: OtherTrack): Boolean {
        val ref = otherTrackSourceRef(track.globalId)
        if (ref in _imported.value) return false
        assetRepository.createAsset(
            name = track.name,
            // The row's own paths, kept apart: a track published in pieces is drawn in pieces, and
            // joining them would draw a line across whatever is between them.
            geometry = AssetGeometry(track.paths),
            kind = AssetKind.TRACK,
            shape = AssetShape.LINE,
            sourceRef = ref
        )
        _imported.value = _imported.value + ref
        return true
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** How far around the phone "Near me" looks until the walker says otherwise. */
        const val DEFAULT_RADIUS_KM = 25.0

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val database = TrailDayDatabase.get(appContext)
                    OtherTracksBrowserViewModel(
                        reader = ArcGisOtherTracks(),
                        cache = OfflineOtherTrackStore(dao = database.offlineOtherTrackDao()),
                        assetRepository = AssetRepository(database)
                    )
                }
            }
        }
    }
}
