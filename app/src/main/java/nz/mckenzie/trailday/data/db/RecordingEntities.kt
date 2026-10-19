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
     * DOC's own record this pass followed, when the line was DOC's rather than the walker's -
     * `doc-tracks:35237557`, the same reference the walker's places carry for DOC's material.
     *
     * A DOC line never becomes a place, so this is how the trip itself remembers what was followed.
     */
    val docRef: String? = null,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    /** [nz.mckenzie.trailday.domain.recording.RecordingStatus] name. */
    val status: String,
    val distanceM: Double = 0.0,
    val durationMs: Long = 0L,
    val pointCount: Int = 0
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
