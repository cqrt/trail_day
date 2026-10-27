package nz.mckenzie.trailday.domain.other

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The national tracks dataset's rows kept on the phone, so a walk in the hills needs nothing from it.
 *
 * The same split DOC's cache makes, and for the same reason: the download is one act and the reading of
 * it another, which is why this is an interface - the phone downloads a box of somebody's tracks once,
 * on purpose, and what a screen shows afterwards is read from here rather than over the network.
 *
 * **Keyed by the service's `GlobalID`.** The dataset publishes an `ID` field that is null on every row
 * looked at and an `OBJECTID` that a monthly rebuild may renumber, so neither can be what a row is held
 * under - see [OtherTrack], which is also where what happens if the UUID changes is written down.
 */
interface OtherTracksCache {

    /** Everything held. This is a set, not a history. */
    suspend fun cached(): List<OtherTrack>

    /** How many rows are held: the number the Offline screen shows. */
    suspend fun count(): Int

    /**
     * The same number, as it changes.
     *
     * For the offline screen's count, and for a map that has to draw tracks downloaded while it is open:
     * an area kept for the walk ahead is expected on the map before the walk starts, not after the app
     * is opened again.
     */
    fun observeCount(): Flow<Int>

    /**
     * When the newest download of this source happened, or null when nothing is held.
     *
     * The licence asks for the moment the data was acquired to be stated, and this is the only place the
     * app knows it: every row carries the moment it was read, and the newest of them is what the credit
     * says. Null for a cache with nothing in it, which has no date to state.
     */
    suspend fun newestDownloadAtEpochMs(): Long? = null
}

/** A cache with nothing in it: a test's, or a map with no store behind it. */
object EmptyOtherTracksCache : OtherTracksCache {
    override suspend fun cached(): List<OtherTrack> = emptyList()
    override suspend fun count(): Int = 0
    override fun observeCount(): Flow<Int> = flowOf(0)
}
