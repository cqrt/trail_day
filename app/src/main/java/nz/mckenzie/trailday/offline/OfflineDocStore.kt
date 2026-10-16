package nz.mckenzie.trailday.offline

import kotlinx.coroutines.flow.Flow
import nz.mckenzie.trailday.data.db.OfflineDocItemDao
import nz.mckenzie.trailday.data.db.OfflineDocItemEntity
import nz.mckenzie.trailday.doc.DocAreaDownload
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

    /** A live count, for the offline screen to say what is stored, and for the map to redraw on. */
    override fun observeCount(): Flow<Int> = dao.observeCount()

    /** How many are held of each service, for the offline screen to say what is on the phone. */
    suspend fun counts(): Map<DocSource, Int> = dao.counts().mapNotNull { row ->
        DocSource.known(row.source)?.let { source -> source to row.count }
    }.toMap()

    /**
     * The ground the held material covers, for the offline screen's map, or null when there is nothing
     * held. Worked out from the vertices, because a cache has no box of its own the way an imagery area
     * does.
     */
    suspend fun bounds(): LatLngBounds? = DocSource.entries.flatMap { cached(it) }.bounds()

    /**
     * Downloads everything DOC publishes for [bounds] and stores it, however the reads went.
     *
     * All four services in one act, because that is what a walker keeps for an area. A download that
     * stopped short stores what arrived: the next attempt asks again rather than starting from a blank
     * cache, and what did arrive is already usable offline.
     */
    suspend fun download(bounds: DocBounds): DocAreaDownload {
        val result = downloader.downloadArea(bounds)
        store(result.items)
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

/**
 * The sentence an area's DOC download earns, in the walker's own terms: what arrived, and which of the
 * four services did not answer.
 *
 * **Both halves matter.** "Kept 412 tracks, 38 huts, 6 campsites, 93 bike locations" is what was asked
 * for and can be checked at a glance; "The campsites could not be read" is the part that would otherwise
 * be found out on the hill, where nothing can be fetched.
 */
fun docDownloadedMessage(download: DocAreaDownload): String {
    val kept = DocSource.entries.mapNotNull { source ->
        val count = download.counts[source] ?: return@mapNotNull null
        if (count == 0) null else "$count ${whatFromDoc(source, count)}"
    }
    val said = if (kept.isEmpty()) {
        "Nothing from DOC was in that area."
    } else {
        "Kept " + kept.joinToString(", ") + "."
    }
    if (download.failed.isEmpty()) return said

    val missing = download.failed.keys.map { source -> source.displayName.lowercase() }
    return "$said The ${missing.joinToString(" and ")} could not be read."
}

/** "1 hut", "2 huts": one of DOC's services, counted, in the words the walker reads. */
fun whatFromDoc(source: DocSource, count: Int): String =
    if (count == 1) source.singular.lowercase() else source.displayName.lowercase()

/**
 * What is held from DOC, in the walker's own terms: "412 tracks, 38 huts, 6 campsites, 93 bike
 * locations", and empty when nothing is held.
 *
 * The same words a download's own sentence is built from, and in DOC's own order, so the tab and the
 * download cannot come to name the same thing differently or list the four in different orders.
 */
fun docHeldWords(counts: Map<DocSource, Int>): String =
    DocSource.entries.mapNotNull { source ->
        val count = counts[source] ?: return@mapNotNull null
        if (count == 0) null else "$count ${whatFromDoc(source, count)}"
    }.joinToString(", ")
