package nz.mckenzie.trailday.data.db

import androidx.room.Entity
import androidx.room.Index

/**
 * One thing DOC published, kept on the phone for offline use.
 *
 * A **cache**, beside the offline imagery: something DOC publishes, held so the browser can offer it
 * with no reception. Not backed up and not joined to anything - clearing it costs nothing but the
 * download.
 *
 * **Keyed by the source as well as by DOC's number.** DOC numbers its features per service, so a hut
 * and a track really can both be 123; keyed on the number alone, the second of them would overwrite the
 * first and the walker would lose a hut they had downloaded without being told. The geometry travels in
 * one JSON column, and so do DOC's own facts, because nothing ever joins to a cache row - see
 * [nz.mckenzie.trailday.domain.doc.DocPathsJson] and
 * [nz.mckenzie.trailday.domain.doc.DocFactsJson].
 */
@Entity(
    tableName = "offline_doc_items",
    primaryKeys = ["source", "objectId"],
    indices = [Index("downloadedAtEpochMs")]
)
data class OfflineDocItemEntity(
    /** Which of DOC's services it came from: one of `DocSource.id`, never renamed. */
    val source: String,
    /** DOC's own number for it, unique within that service. */
    val objectId: Long,
    val name: String,
    /** The line's length in metres, or zero for a place. Kept rather than worked out on every read. */
    val lengthM: Double,
    /** The paths as `[lat, lng]` pairs, JSON: written once, read whole. */
    val pathsJson: String,
    /** What DOC said about it, field by field, JSON: kept exactly as it arrived. */
    val factsJson: String,
    val downloadedAtEpochMs: Long
)
