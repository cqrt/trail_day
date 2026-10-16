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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.doc.ArcGisDoc
import nz.mckenzie.trailday.doc.DocDownloader
import nz.mckenzie.trailday.doc.DocReadResult
import nz.mckenzie.trailday.doc.DocReader
import nz.mckenzie.trailday.domain.doc.DocCache
import nz.mckenzie.trailday.domain.doc.DocFilter
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocQuery
import nz.mckenzie.trailday.domain.doc.DocSort
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.doc.matching
import nz.mckenzie.trailday.domain.doc.showing
import nz.mckenzie.trailday.domain.doc.valuesPresent
import nz.mckenzie.trailday.domain.doc.within
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.offline.OfflineDocStore
import nz.mckenzie.trailday.tracking.DevicePosition

/**
 * Reading what DOC publishes: its tracks, huts, campsites and bike locations.
 *
 * **Nothing here writes anything.** This screen is for finding out what is out there and how far away it
 * is - the search happens at DOC's own service, which holds thousands of these things and is asked for
 * the few the walker is looking at. A DOC thing is not one of the walker's places and never becomes one
 * (see the plan): DOC's material reaches the phone as the map's own layer, downloaded for an area, and
 * this screen is how the walker reads it before going.
 *
 * Which service is being read is the screen's own first question, because the four answer different
 * questions and carry different facts - see [DocSource] and [DocFilter].
 */
class DocBrowserViewModel(
    private val reader: DocReader,
    /** What has been downloaded for offline use, read when DOC cannot be reached. */
    private val cache: DocCache
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _nearMe = MutableStateFlow(false)
    val nearMe: StateFlow<Boolean> = _nearMe

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _results = MutableStateFlow<List<DocItem>>(emptyList())
    val results: StateFlow<List<DocItem>> = _results

    /** Whether a search has been made at all, so the screen can tell "no results" from "not yet". */
    private val _searched = MutableStateFlow(false)
    val searched: StateFlow<Boolean> = _searched

    /** The kilometres around the phone "Near me" looks, as typed; nonsense falls back to the default. */
    private val _radiusKm = MutableStateFlow(DEFAULT_RADIUS_KM.toInt().toString())
    val radiusKm: StateFlow<String> = _radiusKm

    /** The newest fix, so "Near me" searches from where the phone is. Null until there is one. */
    private val fix = MutableStateFlow<GeoPoint?>(null)

    /** The phone's newest fix, for a row that wants to say how far away its thing is. */
    val phoneFix: StateFlow<GeoPoint?> = fix

    /**
     * Which of DOC's four services is being read.
     *
     * One at a time, because that is how DOC publishes them: four services, four sets of fields, four
     * different answers to "what is this and how far away is it".
     */
    private val _source = MutableStateFlow(DocSource.TRACKS)
    val source: StateFlow<DocSource> = _source

    /**
     * What the walker has narrowed the page to, by the field each filter is on.
     *
     * A map rather than one set per fact, because which facts are worth narrowing by is the *source's*
     * answer and differs between the four - see [DocFilter].
     */
    private val _filters = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val filters: StateFlow<Map<String, Set<String>>> = _filters

    private val _sort = MutableStateFlow(DocSort.NEAREST)
    val sort: StateFlow<DocSort> = _sort

    /**
     * What each of this source's filters should offer, and what is ticked, from the page that came back.
     *
     * Built from the results rather than from DOC, so every chip is a fact about something on the screen
     * and a chip that matches nothing cannot be offered.
     */
    val filterChoices: StateFlow<List<Pair<DocFilter, List<String>>>> =
        combine(_results, _source) { results, source ->
            source.filters.map { filter -> filter to results.valuesPresent(filter.field) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * The rows the screen shows: the search's results, narrowed by the filters, ordered by the sort.
     *
     * Derived rather than stored, because the fix moves: standing still and turning "Nearest" on
     * re-orders the page without asking DOC again.
     */
    val visible: StateFlow<List<DocItem>> =
        combine(_results, _filters, _sort, fix) { results, filters, sort, position ->
            results.showing(filters, sort, position)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        viewModelScope.launch {
            DevicePosition.updates(viewModelScope).collect { position -> fix.value = position }
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

    /**
     * Reads another of DOC's services.
     *
     * The last search's rows and filters go with it: they were about a different kind of thing, and a
     * page of huts under a "Difficulty" chip would be a filter about nothing.
     */
    fun chooseSource(source: DocSource) {
        _source.value = source
        _results.value = emptyList()
        _filters.value = emptyMap()
        _searched.value = false
        _message.value = null
    }

    /** Tick or untick one value of one filter; a filter with nothing ticked shows everything. */
    fun toggleFilter(filter: DocFilter, value: String) {
        _filters.update { current ->
            val chosen = current[filter.field].orEmpty()
            val next = if (value in chosen) chosen - value else chosen + value
            if (next.isEmpty()) current - filter.field else current + (filter.field to next)
        }
    }

    fun clearFilters() {
        _filters.value = emptyMap()
    }

    fun setSort(sort: DocSort) {
        _sort.value = sort
    }

    fun search() {
        val nearMe = _nearMe.value
        val position = if (nearMe) fix.value else null
        if (nearMe && position == null) {
            // Near me with no fix is not a name search: the place is the whole question, and dropping it
            // silently is how a hut 80 km away turns up under a 50 km radius.
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
        val wanted = _source.value

        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            when (
                val result = reader.search(
                    DocQuery(source = wanted, nameContains = name, near = position, radiusKm = radius)
                )
            ) {
                is DocReadResult.Found -> {
                    // The service filters by a box, whose corners reach about 1.4x the radius and which
                    // anything clipping it passes through; keep only what is truly within it.
                    _results.value = result.items.within(radius * 1000.0, position)
                    // A new set of results, so a filter from the last search is not carried onto it.
                    _filters.value = emptyMap()
                    _searched.value = true
                    _message.value = when {
                        result.items.isEmpty() -> null
                        // The service hands back a page, not the whole answer; say so rather than
                        // letting a capped search look like a complete one.
                        result.items.size >= DocQuery.DEFAULT_LIMIT ->
                            "Showing the first ${result.items.size} matches. Narrow the search to see the rest."

                        else -> null
                    }
                }

                is DocReadResult.Failed -> {
                    // No service: fall back to what was downloaded for offline use, so what is on the
                    // phone can still be read with no reception. Filtered by name; the download is the
                    // place filter, because the cache only holds the areas asked for.
                    val held = cache.cached(wanted)
                        .matching(name)
                        .within(radius * 1000.0, position)
                    _results.value = held
                    _filters.value = emptyMap()
                    _searched.value = true
                    _message.value = when {
                        held.isNotEmpty() -> "Could not reach DOC. Showing ${held.size} downloaded " +
                            wanted.displayName.lowercase() + "."

                        cache.count() > 0 -> "Could not reach DOC, and nothing downloaded matches."
                        else -> result.message
                    }
                }
            }
            _busy.value = false
        }
    }

    companion object {
        /** How far around the phone a "Near me" search looks, until the walker says otherwise. */
        private const val DEFAULT_RADIUS_KM = 25.0

        /** How long the derived flows stay warm after the screen stops looking. */
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val database = TrailDayDatabase.get(appContext)
                    DocBrowserViewModel(
                        reader = ArcGisDoc(),
                        // The same cache the offline screen downloads into.
                        cache = OfflineDocStore(
                            dao = database.offlineDocItemDao(),
                            downloader = DocDownloader(ArcGisDoc())
                        )
                    )
                }
            }
        }
    }
}
