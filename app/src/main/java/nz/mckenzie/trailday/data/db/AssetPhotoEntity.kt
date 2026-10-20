package nz.mckenzie.trailday.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * The one photograph the walker attached to a place.
 *
 * A table of its own rather than a column on [AssetEntity]: the place list reads every asset, and a
 * row carrying a photograph would make that list load every picture on the phone. Only a place's own
 * page asks for this, by its id.
 *
 * The photograph goes with the place. The foreign key is cascading, so deleting a place cannot leave
 * a picture behind with nothing to open it. The JPEG is already scaled and compressed before it gets
 * here - see [nz.mckenzie.trailday.photo.WaypointPhotos] - because what is kept is a memory of the
 * spot, not a negative. It stays on this phone: the backup format does not carry it yet.
 */
@Entity(
    tableName = "asset_photos",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AssetPhotoEntity(
    @PrimaryKey val assetId: Long,
    val jpeg: ByteArray,
    val addedAtEpochMs: Long
) {

    // A ByteArray compares by identity, so the generated pair would say two rows holding the very
    // same photograph are different. Nothing needs that answer, and a data class that gives it
    // wrongly is still worth not having.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is AssetPhotoEntity && assetId == other.assetId && jpeg.contentEquals(other.jpeg))

    override fun hashCode(): Int = 31 * assetId.hashCode() + jpeg.contentHashCode()
}
