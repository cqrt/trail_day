package nz.mckenzie.trailday.offline

import kotlinx.coroutines.flow.Flow
import nz.mckenzie.trailday.data.db.OfflineDocItemDao
import nz.mckenzie.trailday.data.db.OfflineDocItemEntity
import nz.mckenzie.trailday.doc.DocAreaDownload
import nz.mckenzie.trailday.doc.DocDownloader
import nz.mckenzie.trailday.doc.DocInventory
import nz.mckenzie.trailday.doc.DocPictures
import nz.mckenzie.trailday.doc.DocReadProgress
import nz.mckenzie.trailday.domain.doc.DocBounds
import nz.mckenzie.trailday.domain.doc.DocCache
import nz.mckenzie.trailday.domain.doc.DocFactsJson
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocPathsJson
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.doc.bounds
import nz.mckenzie.trailday.domain.doc.withoutRepeats
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
    /**
     * DOC's pictures, kept in the same cache as the material they are pictures of: they arrive with a
     * download and are cleared with it.
     */
    private val pictures: DocPictureCache = DocPictureCache(dao),
    private val now: () -> Long = System::currentTimeMillis
) : DocCache {

    /**
     * Everything held of [source], **with the same thing said twice removed**.
     *
     * The rule is applied on the way out as well as on the way in, because the copies were being stored
     * before the app knew to drop them: a cache filled by an older build holds both of DOC's rows for a
     * hut, and a map or a list drawn from it would show that hut twice.
     */
    override suspend fun cached(source: DocSource): List<DocItem> =
        dao.getAll(source.id).mapNotNull { it.toItem() }.withoutRepeats()

    override suspend fun count(): Int = dao.count()

    override suspend fun store(items: List<DocItem>) {
        if (items.isEmpty()) return
        dao.insertAll(items.map { it.toEntity(now()) })
    }

    override suspend fun clear() {
        dao.deleteAll()
        // The pictures go with the material they are pictures of: one entry, cleared as one thing - a
        // picture left behind would be storage nobody could see or get rid of.
        pictures.clear()
    }

    /** A live count, for the offline screen to say what is stored, and for the map to redraw on. */
    override fun observeCount(): Flow<Int> = dao.observeCount()

    /**
     * How many are held of each service, for the offline screen to say what is on the phone.
     *
     * Counted through [cached], so the figure is the number of *things* held rather than the number of
     * rows: a hut DOC publishes twice is one hut on the phone, and a line that said two would have the
     * walker's own list disagreeing with their own screen.
     */
    suspend fun counts(): Map<DocSource, Int> = DocSource.entries.mapNotNull { source ->
        val held = cached(source).size
        if (held == 0) null else source to held
    }.toMap()

    /** How many pictures of DOC's material are held, for the offline screen's own line. */
    suspend fun pictureCount(): Int = pictures.count()

    /**
     * A live count of the pictures, for the offline screen's own line.
     *
     * Live, and not a figure refreshed with the material: the records are written before the pictures are
     * fetched, and re-downloading an area changes no record - so a count that moved only when the records
     * moved would sit at nothing while the pictures arrived.
     */
    fun observePictureCount(): Flow<Int> = dao.observePictureCount()

    /**
     * The picture DOC publishes at [url], or null when it is not held.
     *
     * How a hut's or a campsite's page shows what it looks like with no reception, and the only door onto
     * the cache's pictures: nothing else reads them.
     */
    override suspend fun picture(url: String): ByteArray? = pictures.read(url)

    /**
     * The ground the held material covers, for the offline screen's map, or null when there is nothing
     * held. Worked out from the vertices, because a cache has no box of its own the way an imagery area
     * does.
     */
    suspend fun bounds(): LatLngBounds? = DocSource.entries.flatMap { cached(it) }.bounds()

    /**
     * Downloads everything DOC publishes for [bounds] and stores it, however the reads went.
     *
     * All four services in one act, because that is what a walker keeps for an area, and the pictures of
     * what arrived in a second one: the records are kept first, so the area is usable while the pictures
     * are still coming, and a picture that will not come cannot hold up the material itself. A download
     * that stopped short stores what arrived: the next attempt asks again rather than starting from a
     * blank cache, and what did arrive is already usable offline.
     */
    suspend fun download(
        bounds: DocBounds,
        /** How far through the act the phone is: the records being read, then the pictures being kept. */
        onProgress: (DocReadProgress) -> Unit = {}
    ): DocAreaDownload {
        val result = downloader.downloadArea(bounds) { read ->
            onProgress(DocReadProgress(pictures = false, done = read))
        }
        store(result.items)
        return result.copy(
            pictures = pictures.keep(result.items.mapNotNull { it.pictureUrl }) { kept ->
                onProgress(DocReadProgress(pictures = true, done = kept))
            }
        )
    }

    /**
     * What DOC says is in [bounds], for the picker to show before anything is downloaded.
     *
     * Nothing is stored and nothing is written: it is the same services asked a smaller question, and
     * the answer is a figure for a walker to decide on.
     */
    suspend fun inventory(bounds: DocBounds): DocInventory = downloader.inventory(bounds)
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
 * The sentence an area's DOC download earns, in the walker's own terms: what arrived, which of the four
 * services did not answer, and what came of the pictures.
 *
 * **Both halves matter.** "Kept 412 tracks, 38 huts, 6 campsites, 93 bike locations and 12 pictures" is
 * what was asked for and can be checked at a glance; "The campsites could not be read" is the part that
 * would otherwise be found out on the hill, where nothing can be fetched.
 */
fun docDownloadedMessage(download: DocAreaDownload): String {
    val kept = DocSource.entries.mapNotNull { source ->
        val count = download.counts[source] ?: return@mapNotNull null
        if (count == 0) null else "$count ${whatFromDoc(source, count)}"
    }
    val said = if (kept.isEmpty()) {
        "Nothing from DOC was in that area."
    } else {
        "Kept " + kept.joinToString(", ") + docPicturesClause(download.pictures) + "."
    }
    if (download.failed.isEmpty()) return said

    val missing = download.failed.keys.map { source -> source.displayName.lowercase() }
    return "$said The ${missing.joinToString(" and ")} could not be read."
}

/** "1 hut", "2 huts": one of DOC's services, counted, in the words the walker reads. */
fun whatFromDoc(source: DocSource, count: Int): String =
    if (count == 1) source.singular.lowercase() else source.displayName.lowercase()

/** "1 picture", "12 pictures", or nothing at all when there are none. */
fun pictureCountWords(count: Int): String = when {
    count <= 0 -> ""
    count == 1 -> "1 picture"
    else -> "$count pictures"
}

/**
 * What a download's pictures came to, in the walker's own terms: " and 12 pictures", " and 9 of 12
 * pictures" when some did not come, or nothing when there were none to fetch.
 *
 * The "of" matters: a download that brought nine pictures of twelve should not say "nine pictures" as
 * though that were all of them, because the walker is the one who finds out later that the hut they are
 * standing at has no picture.
 */
fun docPicturesClause(pictures: DocPictures): String = when {
    pictures.asked == 0 -> ""
    pictures.kept == pictures.asked -> " and " + pictureCountWords(pictures.kept)
    else -> " and ${pictures.kept} of ${pictures.asked} pictures"
}

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
