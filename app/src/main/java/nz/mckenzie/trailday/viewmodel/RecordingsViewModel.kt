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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.data.RecordingRepository
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.RecordedSessionEntity
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.other.EmptyOtherTracksCache
import nz.mckenzie.trailday.domain.other.OtherTracksCache
import nz.mckenzie.trailday.domain.reference.FollowedRef
import nz.mckenzie.trailday.domain.reference.followedPublishers
import nz.mckenzie.trailday.domain.recording.RecordingStatus
import nz.mckenzie.trailday.domain.recording.followedRefs
import nz.mckenzie.trailday.domain.track.ActivityDate
import nz.mckenzie.trailday.domain.track.ActivityDates
import nz.mckenzie.trailday.domain.track.FileTracks
import nz.mckenzie.trailday.domain.track.ImportDecision
import nz.mckenzie.trailday.domain.track.ImportDestination
import nz.mckenzie.trailday.domain.track.NamedTrack
import nz.mckenzie.trailday.offline.OfflineOtherTrackStore
import nz.mckenzie.trailday.ui.ImportWords

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
    /**
     * How long it took, or null when there is no answer to give.
     *
     * Null is a walk that came from a file with no times in it: the file says when it was brought in
     * but not when it was walked, so nothing is claimed and the row leaves the duration out rather
     * than writing "0s" beside a distance.
     */
    val durationMs: Long?,
    val pointCount: Int,
    val status: RecordingStatus,
    val assetId: Long?,
    val assetName: String?,
    /** The reference of the published line this trip followed, or null when it followed none. */
    val docRef: String? = null,
    /**
     * Who publishes that line, when the trip followed one from the national dataset and its row is still
     * downloaded: the activity names them rather than saying "another publisher".
     */
    val publisher: String? = null,
    /**
     * Every published line this walk followed, in the order it followed them, with whoever publishes each.
     *
     * A walk in pieces, or a walk that wandered onto a second track, followed more than one line, and the
     * words under its name name all of them - see [nz.mckenzie.trailday.domain.reference.followedLineWords].
     * [docRef] is the last of these, kept for the readers that predate the list.
     */
    val followed: List<FollowedRef> = emptyList(),
    /** Whether this activity was brought in from a file rather than walked with the phone. */
    val imported: Boolean = false
) {
    val isFinished: Boolean get() = status == RecordingStatus.FINISHED

    val assetWasDeleted: Boolean get() = assetId != null && assetName == null
}

/**
 * The recordings browser: every GPS recording on the device, what it was for, and
 * how far it went - the way back to the line it followed.
 *
 * **And the way a walk recorded somewhere else comes in.** Import reads a file once and writes what
 * it holds as finished recordings, dated from the file's own times - see [importFiles]. A file of
 * several tracks is a question rather than a guess: it could be a week of walks or a set of places,
 * and only the walker knows which.
 */
class RecordingsViewModel(
    private val context: Context,
    private val recordings: RecordingRepository,
    private val assetRepository: AssetRepository,
    /**
     * Where the publisher of a followed track is written down.
     *
     * A trip keeps the reference of the line it followed and nothing else, so the name of whoever
     * published it comes from the dataset's own row, read once per trip that followed one.
     */
    private val otherTracks: OtherTracksCache = EmptyOtherTracksCache,
    /** The phone's clock, read once per imported track so a batch taken on one day is dated the same. */
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {

    val sessions: StateFlow<List<RecordingRow>> =
        combine(recordings.observeSessions(), assetRepository.observeActiveAssets()) { sessions, assetList ->
            val names = assetList.associate { asset -> asset.id to asset.name }
            sessions.map { session -> session.toRow(names) }
        }.map { rows ->
            val refs = rows.flatMap { row -> row.followed }.map { it.ref }.distinct()
            val publishers = refs.zip(followedPublishers(refs, otherTracks)).toMap()
            rows.map { row ->
                row.copy(
                    followed = row.followed.map { FollowedRef(it.ref, publishers[it.ref]) },
                    publisher = publishers[row.docRef]
                )
            }
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

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /**
     * The question a file of several tracks is asking, or null when nothing is being asked.
     *
     * One at a time: a batch of three such files asks three times, in the order they were picked, and
     * nothing is written until every one has an answer.
     */
    private val _question = MutableStateFlow<ImportWords.Question?>(null)
    val question: StateFlow<ImportWords.Question?> = _question

    /** The file the question on screen is about. */
    private var asking: BatchFile? = null

    /** The batch being read, asked about and written. */
    private var batch: Batch? = null

    /** One file of a batch, read and named, waiting to be written. */
    private data class BatchFile(
        val fileName: String,
        val tracks: List<NamedTrack>,
        /** Where the walker said this file's tracks go, for a file that had to be asked about. */
        var chosen: ImportDestination? = null
    )

    /** A batch of picked files: what was read, what would not read, and how many were picked. */
    private data class Batch(
        val files: List<BatchFile>,
        val failures: MutableList<String>,
        val picked: Int
    )

    /** An activity written, kept only for the single-file sentence, which names it. */
    private data class ImportedOne(val track: NamedTrack, val date: ActivityDate)

    /**
     * Imports GPX, KML, KMZ or GeoJSON files as activities.
     *
     * **Read first, write second.** Every file is read before anything is written, so a batch can be
     * asked about as a whole and answered with one message at the end. A file of several tracks asks
     * first - see [answerImport] - because four tracks could be a week of walks or four places, and
     * nothing in the file says which.
     */
    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val picked = uris.map { uri -> pickFile(context, uri) }
                val files = mutableListOf<BatchFile>()
                val failures = mutableListOf<String>()

                for (one in picked) {
                    when (one) {
                        is Picked.Unreadable -> failures += one.fileName
                        is Picked.Read -> {
                            val read = runCatching {
                                FileTracks.read(fileName = one.file.baseName, bytes = one.file.bytes)
                            }
                            if (read.isFailure) {
                                failures += one.file.fileName
                                continue
                            }
                            files += BatchFile(
                                fileName = one.file.baseName,
                                tracks = read.getOrThrow()
                            )
                        }
                    }
                }

                val pending = Batch(files = files, failures = failures, picked = picked.size)
                batch = pending
                askNext(pending)
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * The walker's answer to the question on screen: every track in that file as activities, or as
     * places. The file is written with the rest only once no question is left.
     */
    fun answerImport(destination: ImportDestination) {
        val pending = batch ?: return
        val file = asking ?: return
        file.chosen = destination
        askNext(pending)
    }

    /**
     * Dismissing the question: nothing in this batch is written.
     *
     * The whole batch goes rather than the file being asked about, because the walker has said they do
     * not want it done - and a message that names no import is better than four activities arriving
     * after somebody closed the dialog to stop it.
     */
    fun cancelImport() {
        batch = null
        asking = null
        _question.value = null
        _message.value = "Nothing was imported."
    }

    /** Puts up the next file's question, or writes the batch when none is left. */
    private fun askNext(pending: Batch) {
        val file = pending.files.firstOrNull { one ->
            ImportDecision.asks(
                tracks = one.tracks.size,
                destination = ImportDestination.ACTIVITIES,
                answered = one.chosen
            )
        }
        asking = file
        if (file == null) {
            _question.value = null
            viewModelScope.launch { writeBatch(pending) }
            return
        }
        _question.value = ImportWords.Question(fileName = file.fileName, tracks = file.tracks.size)
    }

    /**
     * Writes every file in the batch, and says what happened in one sentence.
     *
     * An activity is the file's line, dated from its own times: the side tracks a file may hang off
     * that line are counted and left out, because a walk is one line. Places are the file's tracks
     * whole, side tracks and all, exactly as the Places button writes them.
     */
    private suspend fun writeBatch(pending: Batch) {
        _busy.value = true
        var activities = 0
        var places = 0
        var sideTracks = 0
        var single: ImportedOne? = null

        try {
            for (file in pending.files) {
                val destination = file.chosen ?: ImportDestination.ACTIVITIES
                if (destination == ImportDestination.PLACES) {
                    val written = runCatching { assetRepository.createPlacesFrom(file.tracks) }
                    if (written.isFailure) {
                        pending.failures += file.fileName
                        continue
                    }
                    places += file.tracks.size
                    continue
                }

                for (track in file.tracks) {
                    val date = ActivityDates.of(track.line, now())
                    val written = runCatching {
                        recordings.importActivity(
                            name = track.name,
                            points = track.line,
                            startedAtEpochMs = date.startEpochMs,
                            endedAtEpochMs = date.endEpochMs
                        )
                    }
                    if (written.isFailure) {
                        pending.failures += file.fileName
                        continue
                    }
                    activities++
                    sideTracks += track.reading.sideTracks
                    // The single-file sentence names the walk, which is only possible when there is
                    // one of them: a count is the honest answer to a batch.
                    if (pending.files.size == 1 && file.tracks.size == 1) {
                        single = ImportedOne(track = track, date = date)
                    }
                }
            }

            val failures = pending.failures.distinct()
            val one = single
            _message.value = if (one != null && failures.isEmpty()) {
                ImportWords.oneActivity(
                    name = one.track.name,
                    dateEpochMs = one.date.startEpochMs,
                    points = one.track.line.size,
                    sideTracks = sideTracks
                )
            } else {
                ImportWords.summary(
                    activities = activities,
                    tracks = places,
                    files = pending.picked,
                    sideTracks = sideTracks,
                    failures = failures
                )
            }
            batch = null
        } finally {
            _busy.value = false
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val database = TrailDayDatabase.get(appContext)
                    RecordingsViewModel(
                        context = appContext,
                        recordings = RecordingRepository(database),
                        assetRepository = AssetRepository(database),
                        otherTracks = OfflineOtherTrackStore(dao = database.offlineOtherTrackDao())
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
    // A finished walk with no end is one that came from a file with no times in it: there is no
    // duration to give, and saying "0s" would be claiming one. A walk still being recorded says 0
    // because it has not been going nowhere - the row's own status line is what explains it.
    durationMs = when {
        endedAtEpochMs != null -> (endedAtEpochMs - startedAtEpochMs).coerceAtLeast(0L)
        runCatching { RecordingStatus.valueOf(status) }.getOrDefault(RecordingStatus.FINISHED) ==
            RecordingStatus.FINISHED -> null
        else -> 0L
    },
    pointCount = pointCount,
    status = runCatching { RecordingStatus.valueOf(status) }.getOrDefault(RecordingStatus.FINISHED),
    assetId = assetId,
    assetName = assetId?.let { assetNames[it] },
    docRef = docRef,
    followed = followedRefs(docRef, docRefs).map { FollowedRef(it) },
    imported = imported
)
