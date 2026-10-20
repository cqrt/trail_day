package nz.mckenzie.trailday.domain.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape

/**
 * Everything the walker would lose if the phone went in the creek.
 *
 * These records mirror the database rather than the screens, because a backup that
 * cannot be restored exactly is worse than no backup: it looks like safety and is
 * not. Ids are carried through so the links between an asset, its geometry and the
 * recordings made on it survive the round trip.
 *
 * Deliberately absent: the downloaded offline areas. They describe tiles on one
 * device, the tiles themselves are re-downloadable, and a restore that claimed to
 * bring imagery back would be lying.
 *
 * Also absent for now: the photographs attached to the walker's own places. They
 * stay on the phone that took them, which keeps the backup file small enough for the
 * off-site copy; a place restored elsewhere arrives with its words and not its picture.
 */
@Serializable
data class BackupDocument(
    /** Marks the file as ours, so the wrong JSON is refused rather than guessed at. */
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAtEpochMs: Long,
    /** Which build wrote it, for a human reading the file later. */
    val appVersion: String,
    /**
     * Written as `assets` since format 2. [JsonNames] keeps files written before that
     * readable: a v1 file called this list `tracks`, and its assets come back as tracks
     * rather than being refused.
     */
    @JsonNames("tracks")
    val assets: List<AssetRecord> = emptyList(),
    val recordings: List<RecordingRecord> = emptyList(),
    /**
     * The switches, so a restored phone comes back set up rather than factory-fresh.
     *
     * Optional and added without a version bump: a file written before this existed simply
     * has no such key, and the switches stay as the new device has them. The LINZ key and
     * the GitHub token are deliberately not in here - see [BackupSettingsRecord].
     */
    val settings: BackupSettingsRecord? = null
) {
    companion object {
        const val FORMAT = "trail-day-backup"

        /**
         * Raised only when the shape changes in a way an older app cannot read.
         *
         * Version 2 renamed the `tracks` list to `assets`, and added the kind and shape of an
         * asset. Adding fields is not such a change - unknown keys are ignored and missing ones
         * fall back to their defaults - so a v1 file restores into this build.
         */
        const val VERSION = 2
    }
}

/** A planned asset, with the geometry that gives it a length. */
@Serializable
data class AssetRecord(
    val id: Long,
    val name: String,
    /** [AssetKind] name. A pre-2 file has none, and every asset in it was a track. */
    val kind: String = AssetKind.TRACK.name,
    /** [AssetShape] name. A pre-2 file holds lines only. */
    val shape: String = AssetShape.LINE.name,
    val notes: String? = null,
    val active: Boolean = true,
    val createdAtEpochMs: Long,
    val lengthM: Double = 0.0,
    /**
     * Where the asset came from, when it came from a service: `doc:35239046` for a DOC track.
     *
     * Optional and added without a version bump, like the side tracks before it: a file written
     * before this has no such key, and an asset restored from one simply has no source - which is
     * exactly what an asset drawn or imported from a file has. It travels in the backup so that
     * restoring does not lose which DOC tracks are already on the map.
     */
    val sourceRef: String? = null,
    val points: List<LinePointRecord> = emptyList(),
    /**
     * The side tracks hanging off [points], in the order they were drawn.
     *
     * Optional and added without a version bump: a file written before side tracks existed has no
     * such key, and an asset restored from one has one path - which is what a track was then. The
     * first path is [points] and this holds the rest, which is the storage's own shape
     * (`asset_points.pathIndex`), so a file and a database agree about what a track is without
     * either having to explain itself.
     *
     * **A file written by a newer build and read by an older one loses these** - the format's oldest
     * rule is that a key a build does not know is ignored, and this key is one an older build does not
     * know. That is the trade the rule makes.
     */
    val spurs: List<List<LinePointRecord>> = emptyList()
)

/**
 * One vertex of a planned line.
 *
 * [altitudeM] is the file's own height for this point, when it had one: optional and added without a
 * version bump, because a file written before this has no such key and reads as a line with no
 * heights, which is what a drawn line honestly is. See docs/elevation.md.
 */
@Serializable
data class LinePointRecord(val lat: Double, val lng: Double, val altitudeM: Double? = null)

/** A GPS recording, with every accepted fix. */
@Serializable
data class RecordingRecord(
    val id: Long,
    val name: String,
    /** Called `trackId` before format 2. */
    @JsonNames("trackId")
    val assetId: Long? = null,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val status: String,
    val distanceM: Double = 0.0,
    val durationMs: Long = 0L,
    val pointCount: Int = 0,
    val points: List<RecordedPointRecord> = emptyList(),
    /**
     * The stretches the pass was paused for. Optional and added without a version bump: a file
     * written before pauses were kept simply has no such key, and a restored recording with no
     * pauses is read exactly as the app read it before - a gap in its fixes was ground the pass
     * drove, which is what a gap is.
     */
    val breaks: List<RecordedBreakRecord> = emptyList()
)

/**
 * One pause in a recording.
 *
 * [toEpochMs] is null for a pass that was finished while it was paused, which reads as a stop
 * running to the end of the recording.
 */
@Serializable
data class RecordedBreakRecord(
    val fromEpochMs: Long,
    val toEpochMs: Long? = null
)

/** One accepted GPS fix, as it was written. */
@Serializable
data class RecordedPointRecord(
    val sequence: Int,
    val lat: Double,
    val lng: Double,
    val altitudeM: Double? = null,
    val accuracyM: Float? = null,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null,
    val recordedAtEpochMs: Long
)
