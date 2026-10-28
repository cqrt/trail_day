package nz.mckenzie.trailday.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One **named download** of DOC's material: the ground the walker chose, what they called it, and how much
 * came back.
 *
 * A record rather than more cache, exactly as somebody else's tracks have one. DOC's material is held once
 * and keyed by DOC's own numbers, so two overlapping areas end with one copy of a hut rather than two - but
 * three downloads of three hillsides read as one number of records, and there was no way to tell "Otago"
 * from a box drawn by accident.
 *
 * Deleting one forgets that the ground was downloaded; it does not take a hut off the phone, because the
 * same hut may belong to another named area. What reclaims the space is the card's own Clear, which empties
 * both tables.
 */
@Entity(
    tableName = "offline_doc_areas",
    indices = [Index("downloadedAtEpochMs")]
)
data class OfflineDocAreaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** What the walker called it: "Otago", "the Tararuas". */
    val name: String,
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
    /** How many records the download kept, as the walker was told at the time. */
    val itemCount: Int,
    val downloadedAtEpochMs: Long
)
