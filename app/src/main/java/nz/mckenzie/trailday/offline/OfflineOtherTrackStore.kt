package nz.mckenzie.trailday.offline

import kotlinx.coroutines.flow.Flow
import nz.mckenzie.trailday.data.db.OfflineOtherTrackDao
import nz.mckenzie.trailday.data.db.OfflineOtherTrackEntity
import nz.mckenzie.trailday.domain.doc.DocFactsJson
import nz.mckenzie.trailday.domain.doc.DocPathsJson
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.domain.other.OtherTracksCache
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.other.OtherTracksDownloadResult
import nz.mckenzie.trailday.other.OtherTracksDownloader

/**
 * The national tracks dataset's rows kept on the phone, beside the offline imagery and DOC's material.
 *
 * The same shape as [OfflineDocStore]: a download pulls what the service has for a box, and this records
 * it so the map can draw it with no reception. It is a cache rather than the record of anything -
 * clearing it costs nothing but the download.
 *
 * **Its own store rather than a second half of DOC's**, because the two are different publishers: a
 * walker who clears DOC's material keeps the tracks their council publishes, and the two are counted and
 * cleared apart on the offline screen. Stored globally rather than per area, though: a track is the same
 * track wherever it was downloaded from, so two overlapping areas end with one copy and not two, which
 * is what keying by the service's `GlobalID` is for.
 */
class OfflineOtherTrackStore(
    private val dao: OfflineOtherTrackDao,
    private val downloader: OtherTracksDownloader = OtherTracksDownloader(),
    private val now: () -> Long = System::currentTimeMillis
) : OtherTracksCache {

    /** Everything held, for the map to draw and the browser to list. */
    override suspend fun cached(): List<OtherTrack> = dao.getAll().mapNotNull { it.toTrack() }

    override suspend fun count(): Int = dao.count()

    /** A live count, for the offline screen to say what is stored, and for the map to redraw on. */
    override fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun clear() = dao.deleteAll()

    /**
     * When the newest download of this source happened, or null when nothing is held.
     *
     * The licence asks for the moment the data was acquired to be stated, and this is the only place the
     * app knows it.
     */
    override suspend fun newestDownloadAtEpochMs(): Long? = dao.newestDownloadAtEpochMs()
    /**
     * What the service says is in [bounds], for the picker to show before anything is downloaded.
     *
     * Null when it could not be counted, which is a different thing from nothing being there: a walker
     * who is told "0" and then finds tracks on the hill was told something untrue.
     */
    suspend fun inventory(bounds: LatLngBounds): Int? = downloader.count(bounds)

    /**
     * Downloads every track the service has inside [bounds] and stores it.
     *
     * A download that stopped short **stores what arrived**: the next attempt asks again rather than
     * starting from a blank cache, and what did arrive is already usable offline.
     */
    suspend fun download(
        bounds: LatLngBounds,
        /** How many have been read so far, called after each page. */
        onProgress: (read: Int) -> Unit = {}
    ): OtherTracksDownloadResult {
        val result = downloader.download(bounds, onProgress)
        store(result.tracks)
        return result
    }

    private suspend fun store(tracks: List<OtherTrack>) {
        if (tracks.isEmpty()) return
        dao.insertAll(tracks.map { it.toEntity(now()) })
    }
}

/**
 * A cache row back into a track.
 *
 * A row whose geometry will not read is dropped rather than guessed at: a cache is a copy of something
 * still published, and a copy that cannot be read is not worth a screen failing over. See
 * [DocPathsJson] and [DocFactsJson] - **DOC's names for two things that are no longer DOC's**, which the
 * plan's rename will move and rename together rather than half at a time.
 */
private fun OfflineOtherTrackEntity.toTrack(): OtherTrack? {
    val paths = DocPathsJson.read(pathsJson)
    if (paths.isEmpty()) return null
    return OtherTrack(
        globalId = globalId,
        name = name,
        paths = paths,
        facts = DocFactsJson.read(factsJson)
    )
}

private fun OtherTrack.toEntity(at: Long) = OfflineOtherTrackEntity(
    globalId = globalId,
    name = name,
    lengthM = lengthM,
    pathsJson = DocPathsJson.write(paths),
    factsJson = DocFactsJson.write(facts),
    downloadedAtEpochMs = at
)
