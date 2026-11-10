package nz.mckenzie.trailday.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import nz.mckenzie.trailday.data.db.RecordedBreakEntity
import nz.mckenzie.trailday.data.db.RecordedPointEntity
import nz.mckenzie.trailday.data.db.RecordedSessionEntity
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.RecordingBreak
import nz.mckenzie.trailday.domain.geo.polylineLengthMeters
import nz.mckenzie.trailday.domain.recording.ActivityMode
import nz.mckenzie.trailday.domain.recording.DOC_REFS_SEPARATOR
import nz.mckenzie.trailday.domain.recording.RecordingStatus

/**
 * What a recording already holds, for whatever picks it back up.
 *
 * Read from the fixes themselves, so a screen that is re-created mid-walk says how far
 * the pass has really got rather than starting again from nothing.
 */
data class RecordingProgress(
    val pointCount: Int = 0,
    val distanceM: Double = 0.0,
    /** The last fix recorded, which is the ground the next one is compared against. */
    val lastPoint: GeoPoint? = null
) {
    companion object {
        val EMPTY = RecordingProgress()

        fun of(points: List<GeoPoint>) = RecordingProgress(
            pointCount = points.size,
            distanceM = polylineLengthMeters(points),
            lastPoint = points.lastOrNull()
        )
    }
}

/**
 * GPS recordings.
 *
 * Every accepted fix is written as it arrives, so a crash, process kill or flat
 * battery cannot lose more than a single point.
 */
class RecordingRepository(private val db: TrailDayDatabase) {

    private val recordingDao = db.recordingDao()

    suspend fun startRecording(
        name: String,
        assetId: Long? = null,
        /** The published line this pass followed, if any: see [RecordedSessionEntity.docRef]. */
        docRef: String? = null,
        startedAtEpochMs: Long = System.currentTimeMillis()
    ): Long = recordingDao.insertSession(
        RecordedSessionEntity(
            name = name,
            assetId = assetId,
            docRef = docRef,
            docRefs = docRef,
            startedAtEpochMs = startedAtEpochMs,
            status = RecordingStatus.RECORDING.name
        )
    )

    /** Appends one accepted fix, assigning the next sequence number. */
    suspend fun appendPoint(sessionId: Long, point: GeoPoint): Long {
        val sequence = (recordingDao.lastSequence(sessionId) ?: -1) + 1
        return recordingDao.insertPoint(
            RecordedPointEntity(
                sessionId = sessionId,
                sequence = sequence,
                lat = point.lat,
                lng = point.lng,
                altitudeM = point.altitudeM,
                accuracyM = point.accuracyM,
                speedMps = point.speedMps,
                bearingDeg = point.bearingDeg,
                recordedAtEpochMs = point.timeMs
            )
        )
    }

    suspend fun setStatus(sessionId: Long, status: RecordingStatus) =
        recordingDao.setStatus(sessionId, status.name)

    /** Records which planned track a session is for, if chosen after starting. */
    suspend fun setSessionAsset(sessionId: Long, assetId: Long) =
        recordingDao.setSessionAsset(sessionId, assetId)

    /**
     * Remembers which published line a pass followed, or, with an empty list, that it followed none.
     *
     * The list is the whole of what a walk followed, in the order it followed it, and `docRef` is written
     * as its last entry so everything that already reads one reference still reads the right one. Stored as
     * a newline-separated string rather than a second table: the list is a handful of references at most,
     * and it belongs to the session as much as its name does.
     */
    suspend fun setSessionDocRefs(sessionId: Long, refs: List<String>) =
        recordingDao.setSessionDocRefs(
            sessionId = sessionId,
            docRef = refs.lastOrNull(),
            docRefs = refs.takeIf { it.isNotEmpty() }?.joinToString(DOC_REFS_SEPARATOR)
        )

    /** Renames a session, so the recordings list matches the track it became. */
    suspend fun renameSession(sessionId: Long, name: String) =
        recordingDao.renameSession(sessionId, name)

    /**
     * Says what a saved trip was, walk or ride.
     *
     * Called from the trip's own page, which is the one place the app offers to correct the recorder's
     * guess - see [nz.mckenzie.trailday.domain.recording.ActivityMode.fromSpeed].
     */
    suspend fun setMode(sessionId: Long, mode: ActivityMode) =
        recordingDao.setMode(sessionId, mode.name)

    /**
     * Closes a session, storing the distance, point count and mode actually recorded.
     *
     * [mode] is what the pass was decided to be - by its own average at the moment Save was pressed -
     * and it is written with the closing figures because it is the same fact about the same row. A pass
     * left as a walk is one whose mode nobody decided, which is what an old recording is.
     */
    suspend fun finishRecording(
        sessionId: Long,
        distanceM: Double,
        endedAtEpochMs: Long = System.currentTimeMillis(),
        mode: ActivityMode = ActivityMode.WALK
    ) {
        val count = recordingDao.pointCount(sessionId)
        recordingDao.closeSession(
            sessionId = sessionId,
            status = RecordingStatus.FINISHED.name,
            endedAtEpochMs = endedAtEpochMs,
            distanceM = distanceM,
            pointCount = count,
            mode = mode.name
        )
    }

    /**
     * Writes a walk that came in a file as a finished recording, its points and all.
     *
     * **One transaction for the row and its fixes**, because a walk is the pair: a half-written
     * activity is a row saying 1,240 points with nothing under it, and it would sit in the list
     * looking like a walk whose line had gone missing. Nothing here goes through the recorder's own
     * append-per-fix path either - that is a walk arriving two seconds at a time, and this is one that
     * has already happened.
     *
     * [startedAtEpochMs] and [endedAtEpochMs] are the file's own times, decided by
     * [nz.mckenzie.trailday.domain.track.ActivityDates]; a null end is a file with no times, which
     * stores no length rather than a zero. The distance is measured along the points, the same way the
     * recorder measures its own, so the list's totals add up across both kinds.
     */
    suspend fun importActivity(
        name: String,
        points: List<GeoPoint>,
        startedAtEpochMs: Long,
        endedAtEpochMs: Long?,
        /** What the file said it was, or the walker's answer when it said nothing. */
        mode: ActivityMode = ActivityMode.WALK
    ): Long = db.withTransaction {
        val sessionId = recordingDao.insertSession(
            RecordedSessionEntity(
                name = name,
                startedAtEpochMs = startedAtEpochMs,
                endedAtEpochMs = endedAtEpochMs,
                status = RecordingStatus.FINISHED.name,
                distanceM = polylineLengthMeters(points),
                durationMs = endedAtEpochMs?.let { (it - startedAtEpochMs).coerceAtLeast(0L) } ?: 0L,
                pointCount = points.size,
                imported = true,
                mode = mode.name
            )
        )
        recordingDao.insertPoints(
            points.mapIndexed { index, point ->
                RecordedPointEntity(
                    sessionId = sessionId,
                    sequence = index,
                    lat = point.lat,
                    lng = point.lng,
                    altitudeM = point.altitudeM,
                    accuracyM = point.accuracyM,
                    speedMps = point.speedMps,
                    bearingDeg = point.bearingDeg,
                    recordedAtEpochMs = point.timeMs
                )
            }
        )
        sessionId
    }

    fun observeSessions(): Flow<List<RecordedSessionEntity>> = recordingDao.observeSessions()

    /**
     * Recordings made for one planned track.
     *
     * The link is a soft one (a recording outlives the plan it was made for), so this
     * filters rather than joins.
     */
    fun observeSessionsForTrack(assetId: Long): Flow<List<RecordedSessionEntity>> =
        recordingDao.observeSessions().map { sessions -> sessions.filter { it.assetId == assetId } }

    suspend fun getSession(sessionId: Long): RecordedSessionEntity? = recordingDao.getSession(sessionId)

    /**
     * The session to reattach to on launch: one that was left recording or
     * paused, e.g. because the app was killed mid-walk. Returns null when
     * everything is finished.
     */
    suspend fun findUnfinishedSession(): RecordedSessionEntity? =
        recordingDao.findUnfinishedSession(RecordingStatus.FINISHED.name)

    fun observePoints(sessionId: Long): Flow<List<GeoPoint>> =
        recordingDao.observePoints(sessionId).map { rows -> rows.map { it.toGeoPoint() } }

    suspend fun getPoints(sessionId: Long): List<GeoPoint> =
        recordingDao.getPoints(sessionId).map { it.toGeoPoint() }

    /**
     * Opens a break: the walker has paused, so nothing between here and the moment they
     * carry on is claimed as driven.
     *
     * Written the instant the button is pressed rather than when the pass carries on,
     * because a pass can be finished - or the phone put away - while it is paused, and the
     * stop still happened.
     */
    suspend fun beginBreak(sessionId: Long, atEpochMs: Long = System.currentTimeMillis()) {
        recordingDao.insertBreak(RecordedBreakEntity(sessionId = sessionId, fromEpochMs = atEpochMs))
    }

    /** Closes the break, at the moment the pass carried on. */
    suspend fun endBreak(sessionId: Long, atEpochMs: Long = System.currentTimeMillis()) {
        recordingDao.closeOpenBreaks(sessionId, atEpochMs)
    }

    fun observeBreaks(sessionId: Long): Flow<List<RecordingBreak>> =
        recordingDao.observeBreaks(sessionId).map { rows -> rows.map { it.toRecordingBreak() } }

    suspend fun getBreaks(sessionId: Long): List<RecordingBreak> =
        recordingDao.getBreaks(sessionId).map { it.toRecordingBreak() }

    suspend fun pointCount(sessionId: Long): Int = recordingDao.pointCount(sessionId)

    /**
     * What a session has recorded so far.
     *
     * Computed from the fixes rather than remembered, so a recording that outlived the
     * process that started it comes back with the distance and point count it really
     * has - and the last of them, which is what the next fix has to be a plausible
     * distance and speed from.
     */
    suspend fun recordingProgress(sessionId: Long): RecordingProgress =
        RecordingProgress.of(getPoints(sessionId))

    /** How many walks or rides were made of one place: 0 for a place no walk has been made of. */
    suspend fun countForAsset(assetId: Long): Int = recordingDao.countForAsset(assetId)

    suspend fun deleteRecording(sessionId: Long) = db.withTransaction {
        recordingDao.deleteSession(sessionId)
    }
}

internal fun RecordedPointEntity.toGeoPoint() = GeoPoint(
    lat = lat,
    lng = lng,
    altitudeM = altitudeM,
    accuracyM = accuracyM,
    speedMps = speedMps,
    bearingDeg = bearingDeg,
    timeMs = recordedAtEpochMs
)

internal fun RecordedBreakEntity.toRecordingBreak() = RecordingBreak(
    fromEpochMs = fromEpochMs,
    toEpochMs = toEpochMs
)
