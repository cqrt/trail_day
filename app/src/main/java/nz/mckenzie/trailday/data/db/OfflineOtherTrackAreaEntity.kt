package nz.mckenzie.trailday.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One **named download** of somebody else's tracks: the box the walker drew, what they called it, and what
 * came back from it.
 *
 * A record rather than more cache. The tracks themselves are held once in `offline_other_tracks` and keyed
 * by the service's own identifier, so two overlapping boxes end with one copy of a track rather than two -
 * see [OfflineOtherTrackEntity]. What was missing was the walker's own view of what they had asked for:
 * three downloads of three different hillsides read as one number of tracks, and there was no way to tell
 * "the Tararuas" from a box drawn by accident.
 *
 * The rows here are that list, and nothing else. Deleting one forgets that a box was downloaded; it does
 * not take a track off the phone, because the same track may belong to another named area - what reclaims
 * the space is the card's own Clear, which empties both tables.
 */
@Entity(
    tableName = "offline_other_track_areas",
    indices = [Index("downloadedAtEpochMs")]
)
data class OfflineOtherTrackAreaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** What the walker called it: "the Tararuas", "Home", "Nelson Lakes". */
    val name: String,
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
    /** How many tracks the service gave back for this box, as the walker was told at the time. */
    val trackCount: Int,
    val downloadedAtEpochMs: Long
)
