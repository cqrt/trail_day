package nz.mckenzie.trailday.offline

import kotlinx.coroutines.flow.Flow
import nz.mckenzie.trailday.data.db.OfflineDocItemDao
import nz.mckenzie.trailday.data.db.OfflineDocItemEntity
import nz.mckenzie.trailday.doc.DocDownloadResult
import nz.mckenzie.trailday.doc.DocDownloader
import nz.mckenzie.trailday.domain.doc.DocBounds
import nz.mckenzie.trailday.domain.doc.DocCache
import nz.mckenzie.trailday.domain.doc.DocFactsJson
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocPathsJson
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.doc.bounds
import nz.mckenzie.trailday.domain.tiles.LatLngBounds

/**
 * DOC's material kept on the phone, beside the offline imagery.
 *
 * The same shape as [OfflineAreaManager]: a download pulls what a service has for a box, and this
 * records it so a browser or a map can offer it with no reception. It is a cache rather than the record
 * of the map - clearing it costs nothing but the download.
 *
 * Stored globally rather than per area: something DOC publishes is the same thing wherever it was
 * downloaded from, and two overlapping areas asking for it should end with one copy, not two. Keying by
 * the source **and** the service's own number is what makes that so.
 */
class OfflineDocStore(
    private val dao: OfflineDocItemDao,
    private val downloader: DocDownloader,
    private val now: () -> Long = System::currentTimeMillis
) : DocCache {

    override suspend fun cached(source: DocSource): List<DocItem> =
        dao.getAll(source.id).mapNotNull { it.toItem() }

    override suspend fun count(): Int = dao.count()

    override suspend fun store(items: List<DocItem>) {
        if (items.isEmpty()) return
        dao.insertAll(items.map { it.toEntity(now()) })
    }

    override suspend fun clear() {
        dao.deleteAll()
    }

    /** A live count, for the offline screen to say what is stored. */
    fun observeCount(): Flow<Int> = dao.observeCount()

    /**
     * The ground the held material covers, for the offline screen's map, or null when there is nothing
     * held. Worked out from the vertices, because a cache has no box of its own the way an imagery area
     * does.
     */
    suspend fun bounds(source: DocSource): LatLngBounds? = cached(source).bounds()

    /**
     * Downloads everything [source] has in [bounds] and stores it, however the read went.
     *
     * A download that stopped short stores the pages that arrived: the next attempt asks the service
     * again rather than starting from a blank cache, and what did arrive is already usable offline.
     */
    suspend fun download(bounds: DocBounds, source: DocSource): DocDownloadResult {
        val result = downloader.download(bounds, source)
        store(
            when (result) {
                is DocDownloadResult.Done -> result.items
                is DocDownloadResult.Failed -> result.items
            }
        )
        return result
    }
}

/**
 * A cache row back into an item.
 *
 * A row whose source this build has never heard of is dropped rather than guessed at, and so is one
 * whose geometry will not read: a cache is a copy of something still published, and a copy that cannot
 * be read is not worth a screen failing over. See [DocPathsJson] and [DocFactsJson].
 */
private fun OfflineDocItemEntity.toItem(): DocItem? {
    val known = DocSource.known(source) ?: return null
    val paths = DocPathsJson.read(pathsJson)
    if (paths.isEmpty()) return null
    return DocItem(
        source = known,
        objectId = objectId,
        name = name,
        paths = paths,
        facts = DocFactsJson.read(factsJson)
    )
}

private fun DocItem.toEntity(at: Long) = OfflineDocItemEntity(
    source = source.id,
    objectId = objectId,
    name = name,
    lengthM = lengthM,
    pathsJson = DocPathsJson.write(paths),
    factsJson = DocFactsJson.write(facts),
    downloadedAtEpochMs = at
)

/** The sentence a download's count earns, in the walker's own terms. */
fun docDownloadedMessage(count: Int, source: DocSource): String {
    val what = source.displayName.lowercase()
    return when (count) {
        0 -> "No DOC $what were in that area."
        1 -> "Downloaded 1 DOC ${source.singular.lowercase()}."
        else -> "Downloaded $count DOC $what."
    }
}
