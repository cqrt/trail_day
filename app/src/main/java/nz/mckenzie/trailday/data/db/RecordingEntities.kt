package nz.mckenzie.trailday.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A raw GPS recording.
 *
 * Deliberately not foreign-keyed to [AssetEntity]: deleting a planned place must
 * never destroy the recording of what was actually walked or ridden. [assetId] is a
 * soft link.
 */
@Entity(
    tableName = "recorded_sessions",
    indices = [Index("assetId"), Index("startedAtEpochMs")]
)
data class RecordedSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val assetId: Long? = null,
    /**
     * The published line this pass followed, when it followed one rather than the walker's own: DOC's
     * `doc-tracks:35237557`, or a track from the national dataset as `other:…` - the same references the
     * walker's own places carry for those publishers.
     *
     * A published line never becomes a place, so this is how the trip itself remembers what was followed.
     * Which publisher it was is read back off the prefix, in `followedLineWords`.
     *
     * The **last** line it followed, for a walk that followed more than one. [docRefs] is the list.
     */
    val docRef: String? = null,
    /**
     * Every published line this pass followed, in the order it followed them, newline separated.
     *
     * A walk that wanders onto a second track is still one activity, and its name is made of both - see
     * `joinedWalkName`. Keeping only the last reference would leave the walk's own page claiming it followed
     * one line, which for a walk that changed over halfway is a fact about the last half hour.
     *
     * Null on every row written before the column existed; the migration fills it from [docRef], so a walk
     * already saved reads as a walk of one line.
     */
    val docRefs: String? = null,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    /** [nz.mckenzie.trailday.domain.recording.RecordingStatus] name. */
    val status: String,
    val distanceM: Double = 0.0,
    val durationMs: Long = 0L,
    val pointCount: Int = 0,
    /**
     * Whether this walk came from a file rather than from the recorder.
     *
     * An imported activity is written as a finished recording, so that the list, the page, the map and
     * the export read it exactly like one that was walked with the phone - and one column is what
     * lets the list say where it came from instead of calling it a walk of nothing. It is not a
     * second kind of recording: nothing else about the row changes.
     */
    val imported: Boolean = false
)

/**
 * One accepted GPS fix. Written as it arrives so a crash, kill or flat battery
 * cannot lose a recording.
 */
@Entity(
    tableName = "recorded_points",
    foreignKeys = [
        ForeignKey(
            entity = RecordedSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "sequence"], unique = true)
    ]
)
data class RecordedPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val sequence: Int,
    val lat: Double,
    val lng: Double,
    val altitudeM: Double? = null,
    val accuracyM: Float? = null,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null,
    val recordedAtEpochMs: Long
)

/**
 * A stretch of a pass the walker was paused for.
 *
 * Pausing stops the fixes, so a pass that carries on jumps from the last fix before the pause
 * to the first one after it. That jump is the one kind of gap in a recording that is not
 * ground the pass crossed - the walker had stopped - so it is written down rather than
 * inferred later from the fixes, which cannot tell a pause from a dropped signal.
 * The recorded line reads it: see [nz.mckenzie.trailday.domain.geo.RecordingBreak].
 *
 * [toEpochMs] is null while the pass is still paused, and for a pass that was finished while
 * paused - which reads as a stop that runs to the end of the recording, as it should.
 */
@Entity(
    tableName = "recorded_breaks",
    foreignKeys = [
        ForeignKey(
            entity = RecordedSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class RecordedBreakEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val fromEpochMs: Long,
    val toEpochMs: Long? = null
)
