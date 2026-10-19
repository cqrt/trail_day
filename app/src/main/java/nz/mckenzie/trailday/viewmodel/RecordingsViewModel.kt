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
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.data.RecordingRepository
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.RecordedSessionEntity
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.recording.RecordingStatus

/**
 * One recording, as the browser lists it.
 *
 * [assetName] is null when the session was never tied to a track *or* when the
 * track has since been deleted - recordings deliberately outlive the plans they
 * were recorded against, so the browser says "track deleted" rather than
 * pretending the session had no track.
 */
data class RecordingRow(
    val id: Long,
    val name: String,
    val startedAtEpochMs: Long,
    val distanceM: Double,
    val durationMs: Long,
    val pointCount: Int,
    val status: RecordingStatus,
    val assetId: Long?,
    val assetName: String?,
    /** DOC's own reference for the line this trip followed, or null when it followed none of DOC's. */
    val docRef: String? = null
) {
    val isFinished: Boolean get() = status == RecordingStatus.FINISHED

    val assetWasDeleted: Boolean get() = assetId != null && assetName == null
}

/**
 * The recordings browser: every GPS recording on the device, what it was for, and
 * how far it went - the way back to the line it followed.
 */
class RecordingsViewModel(
    private val recordings: RecordingRepository,
    assetRepository: AssetRepository
) : ViewModel() {

    val sessions: StateFlow<List<RecordingRow>> =
        combine(recordings.observeSessions(), assetRepository.observeActiveAssets()) { sessions, assetList ->
            val names = assetList.associate { asset -> asset.id to asset.name }
            sessions.map { session -> session.toRow(names) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * What the walker has typed into the list's search: the trips whose name holds it.
     *
     * Local and instant, like the places list's: these trips are on the phone already.
     */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    fun search(text: String) {
        _query.value = text
    }

    /**
     * The trips the list is built from: all of them, or the ones the search leaves.
     *
     * The delete is not here: a trip is removed from its own page, where the walker can see the line and its
     * numbers before it goes - see [nz.mckenzie.trailday.ui.screens.RecordingsScreen].
     */
    val shown: StateFlow<List<RecordingRow>> = combine(sessions, _query) { all, query ->
        val typed = query.trim()
        all.filter { row -> typed.isEmpty() || row.name.contains(typed, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val database = TrailDayDatabase.get(appContext)
                    RecordingsViewModel(
                        recordings = RecordingRepository(database),
                        assetRepository = AssetRepository(database)
                    )
                }
            }
        }
    }
}

internal fun RecordedSessionEntity.toRow(assetNames: Map<Long, String>) = RecordingRow(
    id = id,
    name = name,
    startedAtEpochMs = startedAtEpochMs,
    distanceM = distanceM,
    durationMs = endedAtEpochMs?.let { it - startedAtEpochMs }?.coerceAtLeast(0L) ?: 0L,
    pointCount = pointCount,
    status = runCatching { RecordingStatus.valueOf(status) }.getOrDefault(RecordingStatus.FINISHED),
    assetId = assetId,
    assetName = assetId?.let { assetNames[it] },
    docRef = docRef
)
