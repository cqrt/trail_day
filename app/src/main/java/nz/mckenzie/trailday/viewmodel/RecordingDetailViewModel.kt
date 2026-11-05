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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.data.RecordingRepository
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.RecordingBreak
import nz.mckenzie.trailday.domain.geo.polylineLengthMeters
import nz.mckenzie.trailday.domain.geo.splitAtBreaks
import nz.mckenzie.trailday.domain.other.EmptyOtherTracksCache
import nz.mckenzie.trailday.domain.other.OtherTracksCache
import nz.mckenzie.trailday.domain.reference.FollowedRef
import nz.mckenzie.trailday.domain.reference.followedPublishers
import nz.mckenzie.trailday.domain.recording.RecordingStatus
import nz.mckenzie.trailday.domain.recording.followedRefs
import nz.mckenzie.trailday.domain.recording.placeMadeByWalk
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.map.AssetGeoJson
import nz.mckenzie.trailday.map.AssetLine
import nz.mckenzie.trailday.map.AssetStretch
import nz.mckenzie.trailday.offline.OfflineOtherTrackStore

/** One recording, in full: what was walked or ridden, and for which place. */
data class RecordingDetail(
    val id: Long,
    val name: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val status: RecordingStatus,
    /** Distance as recorded by the service at the time. */
    val recordedDistanceM: Double,
    /** Distance computed from the stored geometry, which is the same thing re-derived. */
    val geometryDistanceM: Double,
    val points: List<GeoPoint>,
    /** The stretches of the pass that were paused for. */
    val breaks: List<RecordingBreak> = emptyList(),
    val assetId: Long?,
    val assetName: String?,
    /** The reference of the published line this pass followed, or null when it followed none. */
    val docRef: String? = null,
    /**
     * Who publishes that line, when it was one from the national dataset and the phone still holds its
     * row: the page names them rather than saying "another publisher".
     */
    val publisher: String? = null,
    /** Every published line this walk followed, in the order it followed them: see [RecordingRow.followed]. */
    val followed: List<FollowedRef> = emptyList(),
    /** Whether this walk came in a file rather than being recorded with the phone. */
    val imported: Boolean = false
) {
    /**
     * How long it took, or null when the file gave no times and there is nothing to claim.
     *
     * Null is the same distinction [nz.mckenzie.trailday.viewmodel.RecordingRow.durationMs] makes: a
     * finished walk with no end says nothing rather than "0s", where a walk still being recorded has
     * been going for 0 so far and says so.
     */
    val durationMs: Long?
        get() = endedAtEpochMs?.let { (it - startedAtEpochMs).coerceAtLeast(0L) }
            ?: if (isFinished) null else 0L

    val isFinished: Boolean get() = status == RecordingStatus.FINISHED
}

/**
 * Reviews one recording: the line that was walked or ridden, and which place it followed.
 */
class RecordingDetailViewModel(
    private val sessionId: Long,
    private val recordings: RecordingRepository,
    private val assetRepository: AssetRepository,
    settingsRepository: SettingsRepository,
    /**
     * Where the publisher of a followed track is written down: a trip keeps the line's reference, so this
     * is what turns it back into a name for the page.
     */
    private val otherTracks: OtherTracksCache = EmptyOtherTracksCache
) : ViewModel() {

    /** Which map to draw the pass on: the walker's choice in Settings. */
    val basemap: StateFlow<Basemap> = settingsRepository.basemap
        .stateIn(viewModelScope, SharingStarted.Eagerly, Basemap.DEFAULT)

    private val _detail = MutableStateFlow<RecordingDetail?>(null)
    val detail: StateFlow<RecordingDetail?> = _detail

    /**
     * The recorded line, broken where the pass was paused and drawn straight through the gaps
     * where the fixes only went missing: the recording is the evidence, and what the walker
     * stopped for is part of it.
     */
    val geoJson: StateFlow<String> = _detail
        .map { detail ->
            val points = detail?.points.orEmpty()
            val breaks = detail?.breaks.orEmpty()
            AssetGeoJson.build(
                listOf(
                    AssetLine(
                        assetId = RECORDED_ID,
                        name = detail?.name ?: "Activity",
                        colorHex = AssetColors.RECORDED,
                        points = points,
                        stretches = points.splitAtBreaks(breaks).map { piece ->
                            AssetStretch(colorHex = AssetColors.RECORDED, points = piece)
                        }
                    )
                )
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            AssetGeoJson.build(emptyList())
        )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /**
     * Whether the place this trip was made for goes with it: true only when the walk made the place and no
     * other walk has been made of it - see [placeMadeByWalk].
     */
    private val _placeGoesToo = MutableStateFlow(false)
    val placeGoesToo: StateFlow<Boolean> = _placeGoesToo

    /** The place that would go with the trip, kept so the delete does not have to work it out twice. */
    private var placeMadeByThisWalk: Long? = null

    private val _deleted = MutableStateFlow(false)

    /**
     * True once the recording has been deleted. A one-shot signal: the screen
     * consumes it as it navigates away, otherwise a reused view model would bounce
     * the walker straight back out on their next visit.
     */
    val deleted: StateFlow<Boolean> = _deleted

    fun consumeDeleted() {
        _deleted.value = false
    }

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val session = recordings.getSession(sessionId)
            if (session == null) {
                _message.value = "That activity is no longer on the device"
                return@launch
            }

            val points = recordings.getPoints(sessionId)
            val breaks = recordings.getBreaks(sessionId)
            val assetName = session.assetId?.let { assetId ->
                runCatching { assetRepository.getAsset(assetId)?.name }.getOrNull()
            }

            // Whether deleting this trip would take a place with it, decided once, when the trip is read:
            // the confirmation has to say what goes before the walker presses anything.
            placeMadeByThisWalk = runCatching { placeMadeBy(session.id, session.name, session.assetId) }
                .getOrNull()
            _placeGoesToo.value = placeMadeByThisWalk != null

            // Who published the lines this walk followed: one read of the cache for all of them, because a
            // walk that changed track three times would otherwise parse the whole cache three times.
            val refs = followedRefs(session.docRef, session.docRefs)
            val publishers = followedPublishers(refs, otherTracks)

            _detail.value = RecordingDetail(
                id = session.id,
                name = session.name,
                startedAtEpochMs = session.startedAtEpochMs,
                endedAtEpochMs = session.endedAtEpochMs,
                status = runCatching { RecordingStatus.valueOf(session.status) }
                    .getOrDefault(RecordingStatus.FINISHED),
                recordedDistanceM = session.distanceM,
                geometryDistanceM = polylineLengthMeters(points),
                points = points,
                breaks = breaks,
                assetId = session.assetId,
                assetName = assetName,
                docRef = session.docRef,
                publisher = publishers.lastOrNull(),
                followed = refs.zip(publishers).map { (ref, publisher) -> FollowedRef(ref, publisher) },
                imported = session.imported
            )
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching {
                // The place a walk made goes with the walk. Both or neither: a failure on either leaves the
                // trip, so a second press still knows what it is deleting.
                val place = placeMadeByThisWalk
                if (place != null) assetRepository.deleteAsset(place)
                recordings.deleteRecording(sessionId)
            }
                .onSuccess { _deleted.value = true }
                .onFailure { failure ->
                    _message.value = failure.message ?: "Could not delete the activity"
                }
        }
    }

    /**
     * The place this trip made, or null when the place was the walker's before the walk.
     *
     * Read from the place itself - its name, its mark and how many walks have been made of it - rather than
     * remembered at the time: a place recorded for, or a place walked twice, is not one walk's to delete.
     */
    private suspend fun placeMadeBy(sessionId: Long, sessionName: String, placeId: Long?): Long? {
        val id = placeId ?: return null
        val place = runCatching { assetRepository.getAsset(id) }.getOrNull() ?: return null
        val walks = runCatching { recordings.countForAsset(id) }.getOrDefault(0)
        return id.takeIf {
            placeMadeByWalk(
                placeName = place.name,
                sessionName = sessionName,
                placeSourceRef = place.sourceRef,
                walksOfPlace = walks
            )
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val RECORDED_ID = -2L

        fun factory(context: Context, sessionId: Long): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val database = TrailDayDatabase.get(appContext)
                    RecordingDetailViewModel(
                        sessionId = sessionId,
                        recordings = RecordingRepository(database),
                        assetRepository = AssetRepository(database),
                        settingsRepository = SettingsRepository(appContext),
                        otherTracks = OfflineOtherTrackStore(dao = database.offlineOtherTrackDao())
                    )
                }
            }
        }
    }
}
