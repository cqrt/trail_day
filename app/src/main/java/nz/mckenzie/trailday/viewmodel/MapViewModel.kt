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
import kotlinx.coroutines.flow.first
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
import nz.mckenzie.trailday.domain.asset.YourPlaceLayer
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.splitAtBreaks
import nz.mckenzie.trailday.domain.recording.RecordingStatus
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.domain.tiles.withMinimumSpan
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.doc.DocDetailReader
import nz.mckenzie.trailday.doc.DocDownloader
import nz.mckenzie.trailday.doc.DocProxyDetails
import nz.mckenzie.trailday.domain.doc.DocAlertsState
import nz.mckenzie.trailday.domain.doc.DocBikeLinks
import nz.mckenzie.trailday.domain.doc.DocCache
import nz.mckenzie.trailday.domain.doc.DocHitTest
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.other.EmptyOtherTracksCache
import nz.mckenzie.trailday.domain.other.OtherTracksCache
import nz.mckenzie.trailday.domain.doc.EmptyDocCache
import nz.mckenzie.trailday.domain.doc.DocPick
import nz.mckenzie.trailday.map.AssetGeoJson
import nz.mckenzie.trailday.map.AssetStretch
import nz.mckenzie.trailday.map.DocGeoJson
import nz.mckenzie.trailday.map.TeAraroaGeoJson
import nz.mckenzie.trailday.map.OtherTracksGeoJson
import nz.mckenzie.trailday.map.DocTrackVisibility
import nz.mckenzie.trailday.map.drawingKind
import nz.mckenzie.trailday.offline.KeyCheck
import nz.mckenzie.trailday.offline.LinzKeyProbe
import nz.mckenzie.trailday.offline.OfflineDocStore
import nz.mckenzie.trailday.offline.OfflineOtherTrackStore
import nz.mckenzie.trailday.offline.ProxyHealth
import nz.mckenzie.trailday.offline.ProxyHealthCheck
import nz.mckenzie.trailday.offline.TileTrouble
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
 * Where the map was last looking.
 *
 * Held by the view model rather than by the map view because the map is rebuilt when the walker
 * leaves the tab: without this, coming back from Settings - or from changing the basemap - opens on
 * the work or the phone instead of the ground they were reading.
 */
data class MapCamera(val lat: Double, val lng: Double, val zoom: Double)

/** What the four **Your Places** tiles count, shown under their names. */
data class YourPlaceCounts(
    val spots: Int = 0,
    val tracks: Int = 0,
    val bikeTracks: Int = 0,
    val recordings: Int = 0
) {
    val total: Int get() = spots + tracks + bikeTracks + recordings
}

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
     * The tracks other people publish, kept on the phone: the map's second reference layer, beside DOC's.
     *
     * Read here rather than fetched, for the reason DOC's is: the layer is what has been kept for offline
     * use, so it draws in the hills with no reception at all. See
     * [nz.mckenzie.trailday.map.OtherTracksGeoJson].
     */
    private val otherTracksStore: OtherTracksCache = EmptyOtherTracksCache,
    /**
     * The Te Araroa trail, bundled: the map's other reference layer, under DOC's.
     *
     * Read from the app's own assets rather than fetched, because the trail is a feature of the app -
     * there with no reception and with nothing to download first - and read **lazily**, so a walker who
     * has switched the layer off never pays for it. Injected so a test can build a map without assets;
     * see [nz.mckenzie.trailday.map.TeAraroaGeoJson].
     */
    private val trailGeoJson: () -> String = { TeAraroaGeoJson.EMPTY },
    /**
     * The live DOC alert check for a card.
     *
     * DOC's alerts are current by definition, so a card asks when it is shown rather than reading a
     * copy kept with the area. The reader is injected so a test can answer without a network.
     */
    private val detailReader: DocDetailReader = DocProxyDetails(),
    /**
     * The places a walk or ride has been recorded for, as they change.
     *
     * A place with a recorded pass is a line that was actually walked, and it is drawn in the recorded green
     * with the gold edge rather than in its kind's colour - see [AssetColors.forPlace]. Fed from the trips
     * rather than stored on the place, so a trip deleted puts the line back to what it was.
     */
    private val recordedFor: Flow<Set<Long>> = flowOf(emptySet()),
    /**
     * The walks and rides themselves, for the "Recordings" tile under Your Places.
     *
     * The map's other lines are places and DOC's material. A recording is evidence of ground covered
     * and is kept in its own table, so this is a separate read rather than an asset query.
     */
    private val recordingRepository: RecordingRepository? = null,
    /** Injected so a test can count how often a line is re-read from the database. */
    private val loadGeometry: suspend (Long) -> AssetGeometry = assetRepository::getAssetGeometry,
    /** Injected so a test can say what the app's own proxy answers without reaching it. */
    private val proxyHealth: ProxyHealthCheck = ProxyHealthCheck(),
    /**
     * Injected so a test can say what LINZ answers about the walker's own key without reaching it.
     *
     * Used only when there *is* one: see [askWhatIsWrong], and [LinzAccess] for the two modes.
     */
    private val keyProbe: LinzKeyProbe = LinzKeyProbe()
) : ViewModel() {

    /** Which map to draw under the work: the walker's choice in Settings. */
    val basemap: StateFlow<Basemap> = settingsRepository.basemap
        .stateIn(viewModelScope, SharingStarted.Eagerly, Basemap.DEFAULT)

    /**
     * What to say when the basemap has stopped answering, or null when there is nothing to say.
     *
     * A run of tiles has to fail first - see [TileTrouble] - and only then is anything asked, so a map
     * that is working never makes the call. What is shown is the sentence the thing that knows gives back,
     * rather than a guess made here: this phone cannot tell a dead key from a service that is down, and
     * whichever of the two is fetching the tiles can.
     *
     * **Which of the two depends on the walker's own key** - see [LinzAccess]. With one in force the app
     * talks to LINZ directly, so the proxy's `/health` would be a sentence about somebody else's key; with
     * none it is the proxy's business, and the proxy's answer is the one worth repeating.
     */
    val basemapTrouble: StateFlow<String?> = TileTrouble.showing
        .map { showing -> if (showing) askWhatIsWrong() else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

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
     * Which parts of the walker's own work are left off the map: the **Your Places** tiles.
     *
     * Empty means every part is shown; all four means the master is off. The old single "Your
     * Tracks" switch is read by the settings store as the nearest pair of these.
     */
    val hiddenYourPlaces: StateFlow<Set<YourPlaceLayer>> = settingsRepository.hiddenYourPlaces
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val assets: StateFlow<List<AssetEntity>> = assetRepository.observeActiveAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * The recorded walks and rides as the map draws them: the finished ones, in the recorded green
     * with their gold edge.
     *
     * **A pass linked to one of the walker's line places is not drawn here.** The place already
     * represents that ground, and drawing both produced two green-and-gold lines on the same
     * track - one the planned line, one the raw pass - which read as a duplicate. The pass is
     * still in Activities, still on the place's own page, and appears here again if the place is
     * deleted. A standalone pass, a pass following DOC, and an older pass linked to a point place
     * have no line place to stand for them, so they are drawn.
     *
     * A recording in progress is left out rather than drawn from its last stored fix: the layer is a
     * record of what is done, and a line that grew only when the database happened to be read would
     * be a still photograph of a walk still happening.
     */
    val yourTracksGeoJson: StateFlow<String> =
        combine(
            recordingRepository?.observeSessions() ?: flowOf(emptyList()),
            assets
        ) { sessions, places ->
            val recordings = recordingRepository
                ?: return@combine AssetGeoJson.build(emptyList())
            val lineAssetIds = places
                .filter { AssetShape.fromStorage(it.shape) == AssetShape.LINE }
                .mapTo(mutableSetOf()) { it.id }
            val lines = sessions
                .filter {
                    it.status == RecordingStatus.FINISHED.name &&
                        it.pointCount >= 2 &&
                        (it.assetId == null || it.assetId !in lineAssetIds)
                }
                .mapNotNull { session ->
                    val points = runCatching { recordings.getPoints(session.id) }.getOrNull()
                        ?.takeIf { points -> points.size >= 2 }
                        ?: return@mapNotNull null
                    // A pause is a gap in the ground covered, not a line across it: the recorded
                    // line is broken where the walker stopped, as it is on the recording's page.
                    val breaks = runCatching { recordings.getBreaks(session.id) }.getOrDefault(emptyList())
                    AssetLine(
                        assetId = 0L,
                        name = session.name,
                        colorHex = AssetColors.RECORDED,
                        points = points,
                        kind = AssetKind.TRACK,
                        shape = AssetShape.LINE,
                        stretches = points.splitAtBreaks(breaks).map { piece ->
                            AssetStretch(colorHex = AssetColors.RECORDED, points = piece)
                        }
                    )
                }
            AssetGeoJson.build(lines)
        }
            .flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                AssetGeoJson.build(emptyList())
            )

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

    /**
     * DOC's bike points, paired with the tracks they sit on.
     *
     * Derived from the two lists on the phone rather than stored: a bike point is a note about a track,
     * and a re-download of either service should change the pairing without a migration.
     */
    private val bikeLinks: StateFlow<DocBikeLinks> = docItems
        .map { items ->
            DocBikeLinks.of(
                tracks = items.filter { it.source == DocSource.TRACKS },
                bikes = items.filter { it.source == DocSource.BIKES }
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            DocBikeLinks.EMPTY
        )

    /**
     * A live DOC row the browser asked to see, which may not be in the downloaded cache.
     *
     * The map is normally the cache drawn; a browser result can be somewhere the phone has not kept.
     * While its card is up, the thing itself is added to the reference drawing and taken away when
     * the card is, so the walker sees the line they asked for without it becoming a kept place.
     */
    private val _focusedDoc = MutableStateFlow<DocItem?>(null)
    private val _focusedBike = MutableStateFlow<DocItem?>(null)

    val docGeoJson: StateFlow<String> =
        combine(docItems, bikeLinks, _focusedDoc, _focusedBike) { items, links, focus, focusBike ->
            // Every bike point is a bike trail badge now, not a waypoint marker.
            val drawn = items.filterNot { it.source == DocSource.BIKES }.toMutableList()
            if (focus != null && focus.source != DocSource.BIKES &&
                drawn.none { it.sourceRef == focus.sourceRef }
            ) {
                // A live track the phone has not downloaded is drawn while its card is up.
                drawn += focus
            }
            val bikeFriendly = links.linkedTrackRefs + listOfNotNull(
                focus?.takeIf { focusBike != null }?.sourceRef
            )
            DocGeoJson.build(drawn, bikeFriendly)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            AssetGeoJson.build(emptyList())
        )

    /**
     * Every bike point DOC publishes, as the map's bicycle badges.
     *
     * A linked point sits on its track; an orphan one is still a bike trail, not a waypoint, and is
     * drawn with the same bicycle. Both go with the Bike tracks switch.
     */
    val docBikeBadgesGeoJson: StateFlow<String> =
        combine(docItems, _focusedDoc, _focusedBike) { items, focus, focusBike ->
            val bikes = items.filter { it.source == DocSource.BIKES }.toMutableList()
            if (focus?.source == DocSource.BIKES &&
                bikes.none { it.sourceRef == focus.sourceRef }
            ) {
                bikes += focus
            }
            if (focusBike != null && bikes.none { it.sourceRef == focusBike.sourceRef }) {
                bikes += focusBike
            }
            DocGeoJson.buildBadges(bikes)
        }.stateIn(
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

    /**
     * The raw recorded lines the Recordings tile actually draws: finished passes not represented by
     * one of the walker's line places. A pass linked to a track is counted with that track.
     */
    private val rawRecordingCount: StateFlow<Int> =
        combine(
            recordingRepository?.observeSessions() ?: flowOf(emptyList()),
            assets
        ) { sessions, places ->
            val lineAssetIds = places
                .filter { AssetShape.fromStorage(it.shape) == AssetShape.LINE }
                .mapTo(mutableSetOf()) { it.id }
            sessions.count {
                it.status == RecordingStatus.FINISHED.name &&
                    it.pointCount >= 2 &&
                    (it.assetId == null || it.assetId !in lineAssetIds)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    /**
     * The four **Your Places** tiles' counts.
     *
     * Counted from the same lists the map draws rather than from the style: a count on a switch
     * is about the walker's work, not about how many features happen to be visible at this zoom.
     */
    val yourPlaceCounts: StateFlow<YourPlaceCounts> =
        combine(assets, rawRecordingCount) { places, recordings ->
            YourPlaceCounts(
                spots = places.count { AssetShape.fromStorage(it.shape) == AssetShape.POINT },
                tracks = places.count {
                    AssetKind.fromStorage(it.kind) == AssetKind.TRACK &&
                        AssetShape.fromStorage(it.shape) == AssetShape.LINE
                },
                bikeTracks = places.count {
                    AssetKind.fromStorage(it.kind) == AssetKind.BIKE_TRACK &&
                        AssetShape.fromStorage(it.shape) == AssetShape.LINE
                },
                recordings = recordings
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            YourPlaceCounts()
        )

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
     * DOC's material is not the walker's to change, so a tap on it is a question - "what is that?" - and
     * the answer starts with the card at the bottom of the map before its own page opens. It is held here
     * rather than in the screen so that it survives the map being redrawn, and the next tap that lands on
     * nothing lets it go.
     */
    private val _pickedDoc = MutableStateFlow<DocItem?>(null)
    val pickedDoc: StateFlow<DocItem?> = _pickedDoc

    /**
     * What DOC's alert service said about [pickedDoc], or null when no DOC card is up.
     *
     * A fresh check each time a thing is picked: an alert cached with an area would be a claim about
     * today made from an old answer. Null while none is being shown, and [DocAlertsState.NotAvailable]
     * when the thing has no page to ask about.
     */
    private val _pickedDocAlerts = MutableStateFlow<DocAlertsState?>(null)
    val pickedDocAlerts: StateFlow<DocAlertsState?> = _pickedDocAlerts

    /**
     * The walker's own place the card is about, or null.
     *
     * Held here rather than in the screen for the same reason as [pickedDoc]: a walker who taps a track
     * and then visits Places or Record comes back to the map with the same card, not to an empty map.
     */
    private val _pickedPlaceId = MutableStateFlow<Long?>(null)
    val pickedPlaceId: StateFlow<Long?> = _pickedPlaceId

    /**
     * The frame the map should open on while a card is up.
     *
     * The camera is asked to move when a list sends the walker here; this is what makes it stay there
     * when the map composition is re-created by a tab change. It is cleared with the card, not with the
     * tab, so the last selected line is still in view when the walker comes back.
     */
    private val _selectionFrame = MutableStateFlow<LatLngBounds?>(null)
    val selectionFrame: StateFlow<LatLngBounds?> = _selectionFrame

    /** The picture of the picked thing, when the phone kept one: read once, when the thing is picked. */
    private val _pickedPicture = MutableStateFlow<ByteArray?>(null)

    /**
     * A bike point handed over by the browser for a live track, before that track is downloaded.
     *
     * A map tap has only the cache to go on; a row the browser has just read has the live pair, so
     * the card can say "Bike-friendly" without waiting for a download.
     */
    private val _pickedBike = MutableStateFlow<DocItem?>(null)

    /**
     * The picked thing with how far the phone is from it, measured again on every fix - the card says where
     * the thing is now, not where it was when it was tapped.
     *
     * Declared after [here], which it reads in its initialiser.
     */
    val docPick: StateFlow<DocPick?> = combine(
        _pickedDoc,
        _pickedPicture,
        here,
        bikeLinks,
        _pickedBike
    ) { item, picture, position, links, handedOver ->
        item?.let {
            DocPick(
                item = it,
                distanceM = position?.let { at -> DocHitTest.distanceTo(it, at) },
                picture = picture,
                // A live pair the browser handed over wins; otherwise the cache's own pair.
                bike = handedOver ?: links.bikeFor(it)
            )
        }
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
     *
     * **[raiseCard] is false for a caller that opens the thing's own page.** The page is the card at full
     * size, so the map's own card is not raised at all: raising it and putting it away again would read a
     * picture and ask DOC about alerts for a card nobody ever sees, and leaving it raised is what put the old
     * card back on the map when the walker pressed back from the page. The frame is set either way, so coming
     * back lands on the line that was tapped.
     */
    suspend fun pickDocAt(
        lat: Double,
        lng: Double,
        radiusM: Double,
        raiseCard: Boolean = true
    ): DocItem? {
        // What is switched off is not tappable either: DOC's huts go with the huts. A linked bike point
        // is not a place under the finger: its track is, and the track's card carries the bike facts.
        val hidden = hiddenLayers.value
        val held = withContext(Dispatchers.Default) {
            val items = cached()
            val links = DocBikeLinks.of(
                tracks = items.filter { it.source == DocSource.TRACKS },
                bikes = items.filter { it.source == DocSource.BIKES }
            )
            items.filter { item ->
                val shown = if (item.source == DocSource.TRACKS) {
                    // A dual-use track is walkable too: Tracks draws every track, and Bike trails
                    // draws the rideable subset on its own when Tracks is off.
                    DocTrackVisibility.lineShown(
                        tracksShown = AssetLayer.TRACKS !in hidden,
                        bikeTrailsShown = AssetLayer.BIKE_TRACKS !in hidden,
                        bikeFriendly = item.sourceRef in links.linkedTrackRefs
                    )
                } else {
                    layerOf(item.source.drawingKind()) !in hidden
                }
                shown && !(item.source == DocSource.BIKES && item.sourceRef in links.linkedBikeRefs)
            }
        }
        val item = withContext(Dispatchers.Default) { DocHitTest.nearest(held, lat, lng, radiusM) }
        // A tap answers from the cache: no handed-over pair or live line from a list survives it.
        _pickedBike.value = null
        _focusedDoc.value = null
        _focusedBike.value = null
        _pickedPlaceId.value = null
        _selectionFrame.value = item?.let { boundsOf(it.paths.flatten()) }
        if (raiseCard) {
            // The picture is part of the answer, so it is read here rather than by the card: the card draws
            // what it is given, and a phone without pictures answers null without failing the pick.
            _pickedPicture.value =
                item?.pictureUrl?.let { url -> runCatching { docStore.picture(url) }.getOrNull() }
            _pickedDoc.value = item
            checkAlerts(item)
        } else {
            _pickedPicture.value = null
            _pickedDoc.value = null
            _pickedDocAlerts.value = null
        }
        return item
    }

    /** The bicycle note paired with [item], when one of DOC's bike points sits on it. */
    fun bikeFor(item: DocItem): DocItem? = bikeLinks.value.bikeFor(item)

    /**
     * Asks DOC for the current alerts for the card's subject.
     *
     * The answer is tied back to what is still picked: a slow reply for a card the walker has already
     * closed must not put an alert under something else.
     */
    private fun checkAlerts(item: DocItem?) {
        if (item == null) {
            _pickedDocAlerts.value = null
            return
        }
        if (item.link == null) {
            _pickedDocAlerts.value = DocAlertsState.NotAvailable
            return
        }
        _pickedDocAlerts.value = DocAlertsState.Loading
        viewModelScope.launch {
            val state = detailReader.alertsFor(item)
            if (_pickedDoc.value?.sourceRef == item.sourceRef) {
                _pickedDocAlerts.value = state
            }
        }
    }

    /** Lets go of a picked thing, so the card is about what is nearest again. */
    fun clearPickedDoc() {
        _pickedPicture.value = null
        _pickedBike.value = null
        _pickedDoc.value = null
        _pickedDocAlerts.value = null
        _focusedDoc.value = null
        _focusedBike.value = null
        _pickedPlaceId.value = null
        _selectionFrame.value = null
    }

    /** Shows one of the walker's own places on the map; the camera does not move on a tap. */
    fun pickPlace(assetId: Long) {
        clearPickedDoc()
        _pickedPlaceId.value = assetId
        val points = geometryByTrack.value[assetId]?.paths?.flatten()
        if (points != null) {
            _selectionFrame.value = boundsOf(points)
        } else {
            viewModelScope.launch {
                val geometry = runCatching { loadGeometry(assetId) }.getOrNull() ?: return@launch
                if (_pickedPlaceId.value == assetId) {
                    _selectionFrame.value = boundsOf(geometry.paths.flatten())
                }
            }
        }
    }

    /** Closes the place card; the camera stays where it is. */
    fun clearPickedPlace() {
        _pickedPlaceId.value = null
        _selectionFrame.value = null
    }

    /**
     * Frames one of the walker's own places and brings the card for it up.
     *
     * Asked for from the places list, where a track is a row rather than ground: the map is what
     * answers "where is it". A layer the walker had switched off is switched back on for the visit,
     * because a frame around something that is not drawn is not an answer.
     */
    fun focusPlace(assetId: Long) {
        viewModelScope.launch {
            val asset = assetRepository.getAsset(assetId) ?: return@launch
            val geometry = runCatching { assetRepository.getAssetGeometry(assetId) }.getOrNull()
                ?: return@launch
            val points = geometry.paths.flatten()
            if (points.size < 2) return@launch
            clearPickedDoc()
            _pickedPlaceId.value = assetId
            _selectionFrame.value = boundsOf(points)
            val kind = AssetKind.fromStorage(asset.kind)
            // Asking to see one of the walker's own places turns its own tile back on, and says
            // nothing about DOC's material underneath it.
            val yourLayer = YourPlaceLayer.forKind(kind)
            if (yourLayer in settingsRepository.hiddenYourPlaces.first()) {
                settingsRepository.setYourPlaceHidden(yourLayer, hidden = false)
            }
            frame(points)
        }
    }

    /**
     * Frames one of DOC's things and brings its card up, as a tap on the map itself would.
     *
     * The row in DOC's browser is a name and a length; the map is where the line is read, and where a
     * hut or a camp is seen against the ground around it.
     */
    fun focusDoc(item: DocItem, bike: DocItem? = null) {
        viewModelScope.launch {
            val picture = item.pictureUrl?.let { url ->
                runCatching { docStore.picture(url) }.getOrNull()
            }
            _pickedPicture.value = picture
            _pickedBike.value = bike
            _focusedDoc.value = item
            _focusedBike.value = bike
            _pickedPlaceId.value = null
            _pickedDoc.value = item
            _selectionFrame.value = boundsOf(item.paths.flatten())
            checkAlerts(item)
            layerOf(item.source.drawingKind())?.let { layer ->
                if (layer in settingsRepository.hiddenMapLayers.first()) {
                    settingsRepository.hideMapLayer(layer, false)
                }
            }
            // A place is a path of one point, and [frame] gives it the smallest frame a map can open
            // on; a line is framed by its own extent.
            item.paths.flatten().takeIf { points -> points.isNotEmpty() }?.let { points -> frame(points) }
        }
    }

    /** The box around [points], with the smallest frame a map can usefully open on. */
    private fun boundsOf(points: List<GeoPoint>): LatLngBounds? =
        if (points.isEmpty()) {
            null
        } else {
            LatLngBounds(
                minLat = points.minOf { it.lat },
                minLng = points.minOf { it.lng },
                maxLat = points.maxOf { it.lat },
                maxLng = points.maxOf { it.lng }
            ).withMinimumSpan()
        }

    /** Asks the camera to fit [points], with the smallest frame a map can usefully open on. */
    private fun frame(points: List<GeoPoint>) {
        boundsOf(points)?.let { bounds ->
            _recentre.value = RecentreRequest(bounds, (_recentre.value?.count ?: 0) + 1)
        }
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

    /**
     * Where the map was last looking, so leaving the tab and coming back - or changing the basemap -
     * opens on the same ground rather than on the work or the phone.
     */
    private val _camera = MutableStateFlow<MapCamera?>(null)
    val camera: StateFlow<MapCamera?> = _camera

    /** The map has settled; keep where it settled. */
    fun rememberCamera(lat: Double, lng: Double, zoom: Double) {
        _camera.value = MapCamera(lat = lat, lng = lng, zoom = zoom)
    }

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

    /**
     * The map has moved to the recentre it was given, so there is nothing left to ask for.
     *
     * **A request is a moment, not a state.** Left standing it was re-applied every time the map was
     * rebuilt, which a tab change and a basemap change both do - so a walker who had once tapped "where am
     * I" was put back on the phone every time they returned to the map, however far they had panned since.
     */
    fun consumeRecentre() {
        _recentre.value = null
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
        val hidden = hiddenYourPlaces.value
        val drawn = assets.value
            .filter { place ->
                // Every place of the walker's is governed by its own tile: spots together, tracks
                // and bike tracks by their kind, and the DOC switches say nothing about any of them.
                YourPlaceLayer.forKind(AssetKind.fromStorage(place.kind)) !in hidden
            }
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

    /** Shows or hides one part of the walker's own work, written straight through like the DOC tiles. */
    fun setYourPlaceHidden(layer: YourPlaceLayer, hidden: Boolean) {
        viewModelScope.launch { settingsRepository.setYourPlaceHidden(layer, hidden) }
    }

    /** The master **Your Places** switch: all four on the map, or all four off. */
    fun setAllYourPlacesHidden(hidden: Boolean) {
        viewModelScope.launch { settingsRepository.setAllYourPlacesHidden(hidden) }
    }

    /** Puts every layer back on the map: one switch to undo the lot. */
    fun showEveryLayer() {
        viewModelScope.launch {
            settingsRepository.setHiddenMapLayers(emptySet())
            settingsRepository.setAllYourPlacesHidden(false)
        }
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

    /**
     * Whether the Te Araroa trail is drawn - the map's own "Te Araroa trail" switch.
     *
     * Eagerly, like the basemap: a switch read after the first draw would show a map with no trail on it
     * and then grow one. On when unset, which is the setting's own default.
     */
    val teAraroa: StateFlow<Boolean> = settingsRepository.teAraroa
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * The trail's own GeoJSON, or the empty collection while the layer is switched off.
     *
     * **Read the first time the layer is switched on**, off the main thread: 700 KB of assets is not
     * something a map's first frame should wait for, and a walker who never wants the trail should never
     * pay for the read. The reader holds what it read, so switching the layer on and off again is free.
     */
    val teAraroaGeoJson: StateFlow<String> = teAraroa
        .map { shown ->
            if (shown) withContext(Dispatchers.Default) { trailGeoJson() } else TeAraroaGeoJson.EMPTY
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeAraroaGeoJson.EMPTY)

    /** Shows or hides the trail, written straight through like the layer switches. */
    fun setTeAraroa(shown: Boolean) {
        viewModelScope.launch { settingsRepository.setTeAraroa(shown) }
    }

    /**
     * Whether the tracks other people publish are drawn - the map's own "Other tracks" switch.
     *
     * Eagerly, like the trail: a switch read after the first draw would show a map without the layer and
     * then grow one. On when unset, which is the setting's own default.
     */
    val otherTracks: StateFlow<Boolean> = settingsRepository.otherTracks
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * Those tracks as the map's own document, or the empty collection while the layer is switched off.
     *
     * **Read from the cache, and read again whenever the cache changes**, which is the difference between
     * this layer and the trail: the trail is bundled and read once, while this one grows as areas are
     * downloaded - and an area kept for the walk ahead is expected on the map before the walk starts, not
     * after the app is opened again. Built off the main thread: an area can hold thousands of lines, and
     * a map's first frame is not the place to build them.
     */
    val otherTracksGeoJson: StateFlow<String> =
        combine(otherTracks, otherTracksStore.observeCount()) { shown, _ -> shown }
            .map { shown ->
                if (!shown) {
                    OtherTracksGeoJson.EMPTY
                } else {
                    withContext(Dispatchers.Default) {
                        OtherTracksGeoJson.build(otherTracksStore.cached())
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OtherTracksGeoJson.EMPTY)

    /** Shows or hides somebody else's tracks, written straight through like the trail's own switch. */
    fun setOtherTracks(shown: Boolean) {
        viewModelScope.launch { settingsRepository.setOtherTracks(shown) }
    }

    /**
     * When the tracks other people publish were last downloaded, or null when none are held.
     *
     * For the one thing the dataset's licence asks of a reader that this app did not already do: state
     * **when the data was acquired**. Read again whenever the cache changes, because a download while the
     * map is open changes the answer.
     */
    val otherTracksDownloadedAt: StateFlow<Long?> = otherTracksStore.observeCount()
        .map { otherTracksStore.newestDownloadAtEpochMs() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Switches the basemap from the map itself, which is what the trouble notice offers.
     *
     * Written to the preference rather than held here, so it is the same choice the Settings screen
     * offers and the walker can switch back from there. The trouble counted about the source being left
     * is forgotten with it: a map that never asks LINZ anything cannot be told it is not answering.
     */
    fun useBasemap(choice: Basemap) {
        TileTrouble.reset()
        viewModelScope.launch { settingsRepository.setBasemap(choice) }
    }

    /** The walker has read the trouble notice. It stays away until tiles start arriving again. */
    fun answerTrouble() {
        TileTrouble.answered()
    }

    /**
     * Asks whichever service is fetching the tiles what it makes of the key.
     *
     * Best-effort in both modes: a phone with no reception cannot ask, and "could not be reached" is then
     * the honest answer rather than a claim about a key.
     */
    private suspend fun askWhatIsWrong(): String {
        val own = settingsRepository.developerLinzKey.first()
        if (own.isBlank()) {
            return when (val answer = proxyHealth.check()) {
                is ProxyHealth.Answered -> answer.message
                ProxyHealth.Unreachable -> "The map service could not be reached."
            }
        }

        return when (val answer = keyProbe.check(own)) {
            // The key is fine, so the trouble is something else - and saying so is more use than
            // repeating a sentence about a key that works.
            KeyCheck.Works -> "Your LINZ key was accepted, so the trouble is elsewhere."
            is KeyCheck.Failed -> answer.message
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val recordings = RecordingRepository(TrailDayDatabase.get(appContext))
                    MapViewModel(
                        assetRepository = AssetRepository(TrailDayDatabase.get(appContext)),
                        settingsRepository = SettingsRepository(appContext),
                        locationSource = FusedLocationSource(appContext),
                        docStore = OfflineDocStore(
                            dao = TrailDayDatabase.get(appContext).offlineDocItemDao(),
                            downloader = DocDownloader.online()
                        ),
                        otherTracksStore = OfflineOtherTrackStore(
                            dao = TrailDayDatabase.get(appContext).offlineOtherTrackDao()
                        ),
                        // The places a walk has been made of, read from the trips themselves: a trip deleted
                        // puts its line back to what it was, with nothing to remember on the place.
                        recordedFor = recordings.observeSessions()
                            .map { sessions -> sessions.mapNotNull { it.assetId }.toSet() },
                        recordingRepository = recordings,
                        trailGeoJson = TeAraroaGeoJson.reader(appContext)
                    )
                }
            }
        }
    }
}
