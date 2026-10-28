package nz.mckenzie.trailday.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The named downloads of somebody else's tracks: the offline screen's list of areas.
 *
 * Its own DAO rather than a corner of [OfflineOtherTrackDao]'s, because the two answer different questions
 * about the same material - what is on the phone, and where it came from - and the second is a list the
 * walker reads while the first is a count.
 */
@Dao
abstract class OfflineOtherTrackAreaDao {

    /** Newest first, because the most recent download is the one being looked for. */
    @Query("SELECT * FROM offline_other_track_areas ORDER BY downloadedAtEpochMs DESC")
    abstract fun observeAll(): Flow<List<OfflineOtherTrackAreaEntity>>

    @Query("SELECT * FROM offline_other_track_areas ORDER BY downloadedAtEpochMs DESC")
    abstract suspend fun getAll(): List<OfflineOtherTrackAreaEntity>

    /** The row for this name, when it is already here: downloading a region twice updates it. */
    @Query("SELECT * FROM offline_other_track_areas WHERE name = :name LIMIT 1")
    abstract suspend fun find(name: String): OfflineOtherTrackAreaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(area: OfflineOtherTrackAreaEntity): Long

    /** Forgets one named download. The tracks it brought stay until the card's Clear. */
    @Query("DELETE FROM offline_other_track_areas WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM offline_other_track_areas")
    abstract suspend fun deleteAll()
}
