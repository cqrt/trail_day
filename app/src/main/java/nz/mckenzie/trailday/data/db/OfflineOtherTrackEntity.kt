package nz.mckenzie.trailday.data.db

import androidx.room.Entity
import androidx.room.Index

/**
 * One track from the national tracks dataset, kept on the phone for offline use.
 *
 * A **cache**, beside the offline imagery and beside DOC's material: something somebody else publishes,
 * held so the map can draw it with no reception. Not backed up and not joined to anything - clearing it
 * costs nothing but the download.
 *
 * **Keyed by the service's `GlobalID`**, and that is a decision rather than a convenience. The dataset
 * publishes an `ID` field too, and it is null on every row looked at; `OBJECTID` is the service's own
 * counter, which a monthly rebuild may renumber - and a cache keyed by a number that changes grows a
 * second copy of every track every month. The UUID is the one identifier that might survive a rebuild;
 * that it does is unverified, and [nz.mckenzie.trailday.domain.other.OtherTrack] says what catches it if
 * it does not.
 *
 * The shape travels in one column of text and so do the row's own details, because nothing ever joins to a
 * saved row - see [nz.mckenzie.trailday.domain.cache.PathsJson] and
 * [nz.mckenzie.trailday.domain.cache.FactsJson].
 */
@Entity(
    tableName = "offline_other_tracks",
    primaryKeys = ["globalId"],
    indices = [Index("downloadedAtEpochMs")]
)
data class OfflineOtherTrackEntity(
    /** The service's UUID for the row. */
    val globalId: String,
    val name: String,
    /** The line's length in metres. Kept rather than worked out on every read. */
    val lengthM: Double,
    /** The paths as `[lat, lng]` pairs, JSON: written once, read whole. */
    val pathsJson: String,
    /** The row's own fields, exactly as they arrived, JSON. */
    val factsJson: String,
    /**
     * When this row was last read from the service.
     *
     * Kept per row rather than as one date for the cache because a download only ever *adds* an area:
     * the newest row is the moment the credit has to state, and the licence asks for exactly that.
     */
    val downloadedAtEpochMs: Long
)
