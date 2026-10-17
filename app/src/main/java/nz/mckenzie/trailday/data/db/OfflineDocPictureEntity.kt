package nz.mckenzie.trailday.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The picture DOC publishes for one of its things, kept on the phone for offline use.
 *
 * **Keyed by the picture's own address**, because that is what identifies it: DOC addresses a hut's
 * picture with a hash of its own, and two things can share one address. Keyed on the address, keeping the
 * same area twice does not keep the same picture twice, and a thing DOC re-addresses is fetched again
 * rather than being mistaken for the picture it replaced.
 *
 * The bytes are held whole, in the same cache as the material they are pictures of: cleared with it,
 * backed up with nothing, and costing nothing but the download. See
 * [nz.mckenzie.trailday.offline.DocPictureCache].
 */
@Entity(tableName = "offline_doc_pictures")
data class OfflineDocPictureEntity(
    /** DOC's own address for the picture, exactly as it arrived. */
    @PrimaryKey val url: String,
    /** The picture itself, as DOC served it. */
    val bytes: ByteArray,
    val fetchedAtEpochMs: Long
) {

    // A ByteArray compares by identity, so the generated pair would say two rows holding the very same
    // picture are different. Nothing compares these rows - they are read and drawn - but a data class
    // that answers a question wrongly is worth not having.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is OfflineDocPictureEntity && url == other.url && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * url.hashCode() + bytes.contentHashCode()
}
