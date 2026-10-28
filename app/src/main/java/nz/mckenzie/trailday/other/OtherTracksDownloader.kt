package nz.mckenzie.trailday.other

import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.domain.other.OtherTracksQuery
import nz.mckenzie.trailday.domain.other.OtherTracksUrl
import nz.mckenzie.trailday.domain.tiles.LatLngBounds

/** What reading a whole area produced: the rows, and whether they are all of them. */
sealed interface OtherTracksDownloadResult {

    /**
     * What was read, however the read ended.
     *
     * On the interface rather than on each case, because a caller almost always wants exactly this - a
     * download that stopped short still has rows worth keeping - and a `when` over two cases to get at
     * the same field is a `when` that will be written once per caller.
     */
    val tracks: List<OtherTrack>

    data class Done(override val tracks: List<OtherTrack>) : OtherTracksDownloadResult

    /**
     * The service stopped answering part-way, **with what did arrive**.
     *
     * A download that got 1,200 rows of a box before the network went is worth keeping and worth saying
     * so about: the alternative is a walker with no tracks offline and no idea why.
     */
    data class Failed(
        val message: String,
        override val tracks: List<OtherTrack>
    ) : OtherTracksDownloadResult
}

/**
 * Reading every track in a box, a page at a time.
 *
 * **A page is read until one comes back short**, which is the only reliable signal this service gives.
 * It sets `exceededTransferLimit` on answers of three rows, so the flag cannot be believed, and a page
 * that is short is the service saying it has nothing more behind it.
 */
class OtherTracksDownloader(
    private val reader: OtherTracksReader = ArcGisOtherTracks(),
    private val pageSize: Int = PAGE,
    private val maxTracks: Int = MAX_TRACKS
) {

    suspend fun download(
        selection: OtherTracksQuery = OtherTracksQuery(),
        /** How many have been read so far, called after each page. */
        onProgress: (read: Int) -> Unit = {}
    ): OtherTracksDownloadResult {
        val all = mutableListOf<OtherTrack>()
        var offset = 0
        while (true) {
            val page = reader.read(selection.copy(limit = pageSize, offset = offset))
            when (page) {
                is OtherTracksReadResult.Failed -> return OtherTracksDownloadResult.Failed(
                    page.message,
                    all
                )
                is OtherTracksReadResult.Found -> {
                    all += page.tracks
                    onProgress(all.size)
                    if (page.tracks.size < pageSize || all.size >= maxTracks) break
                    offset += page.tracks.size
                }
            }
        }
        // **What is kept is what the service gave, pieces and all.** The joining of a walkway the dataset
        // cut up happens when the cache is read, not when it is written - see [OtherTracksCache.cached] -
        // so the number the picker promised, the number the download reports and the number the offline
        // card keeps all describe the same thing. Joining here as well made a region promised at 3,112
        // tracks arrive saying 1,098, which reads as a download that lost two thirds of itself.
        return OtherTracksDownloadResult.Done(all)
    }

    /**
     * How many tracks the service says match [selection], for a walker deciding whether to take the area
     * onto a phone. Nothing is read and nothing is written.
     *
     * Null when it could not be counted, which is not the same as nothing being there: a walker told "0"
     * who then finds tracks on the hill was told something untrue.
     */
    suspend fun count(selection: OtherTracksQuery): Int? =
        when (val answer = reader.count(selection)) {
            is OtherTracksCountResult.Done -> answer.count
            is OtherTracksCountResult.Failed -> null
        }

    companion object {

        /** The service's own ceiling per request, so one page is one request. */
        const val PAGE = 2000

        /**
         * Where a download stops, whatever the box or the region.
         *
         * An area is a walk's worth of ground, and a box drawn around the whole South Island is 17,000 rows
         * and a very long wait on a phone. What arrives is kept and the walker is told how many, so a stop
         * is visible rather than silent.
         *
         * **Five thousand rather than four**, because a region is the biggest thing a walker can ask for by
         * name and the biggest of the sixteen holds **3,848** rows: at four thousand the largest region is
         * a hair under the ceiling, and a month in which Wellington's councils publish a few hundred more
         * would cut the region short without anybody asking for that.
         */
        const val MAX_TRACKS = 5000
    }
}
