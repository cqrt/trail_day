package nz.mckenzie.trailday.doc

import nz.mckenzie.trailday.domain.doc.DocBounds
import nz.mckenzie.trailday.domain.doc.DocCompanion
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocQuery
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.doc.DocTrackCompanions
import nz.mckenzie.trailday.domain.doc.pictureWhere
import nz.mckenzie.trailday.domain.doc.withCompanion

/** How a download of DOC's material for an area went. */
sealed interface DocDownloadResult {

    /** Everything the service had in the area. */
    data class Done(val items: List<DocItem>) : DocDownloadResult

    /**
     * The download stopped short, and [message] says why.
     *
     * [items] are the pages that did arrive before it stopped, because a half-downloaded area is still
     * worth keeping: the next attempt starts again from what came back rather than from nothing.
     */
    data class Failed(val message: String, val items: List<DocItem>) : DocDownloadResult
}

/**
 * How a DOC download is going: **which part of the act is running, and how far through it is.**
 *
 * Two shapes rather than one because the act has two parts that count different things - the records of
 * DOC's services, then the pictures of what arrived - and the caller is the only place that knows how many
 * the box holds. See [DocDownloader.downloadArea] and [DocPictureCache.keep], which count what they read.
 */
data class DocReadProgress(
    /** True while the pictures of what arrived are being kept, false while the records are being read. */
    val pictures: Boolean,
    /** Records read, or pictures kept, so far in this part of the act. */
    val done: Int
)

/**
 * How downloading a whole area's DOC data went: everything that arrived, and which of the four services
 * could not be read.
 *
 * One act over four services, because that is what a walker keeps for an area - the tracks to walk, the
 * huts and campsites to stop at, the bike locations to ride - and asking for them one at a time is the
 * walker doing the app's bookkeeping. What each service said is kept apart so the phone can say it back:
 * "kept 412 tracks and 38 huts, and the campsites could not be read" is a sentence somebody can act on.
 */
data class DocAreaDownload(
    /** Everything that arrived, from every service that answered. */
    val items: List<DocItem>,
    /** How many of each service answered with, zero included: a service that was empty is not a failure. */
    val counts: Map<DocSource, Int>,
    /** The services that could not be read at all, with the phone's sentence about each. */
    val failed: Map<DocSource, String>,
    /**
     * The pictures of what arrived, once they have been fetched: empty here, because reading the
     * services and keeping pictures are two steps and this is the first of them - see
     * [nz.mckenzie.trailday.offline.OfflineDocStore].
     */
    val pictures: DocPictures = DocPictures.NONE
)

/**
 * Reads everything one of DOC's services has in a box, a page at a time.
 *
 * The service answers at most 2,000 features a request, so an area with more is read by asking again
 * from where the last page ended - which is why [DocQuery.offset] exists. The total is capped: a box
 * over a whole island is not an offline area for a walk, and a runaway download is worse than a short
 * one.
 *
 * Kept apart from storage so the paging can be tested against a fake reader with no network and no
 * database - the pages are the whole of what it decides.
 */
class DocDownloader(
    private val reader: DocReader,
    private val pageSize: Int = PAGE,
    /**
     * The richer facts for the tracks that arrived, when a source has any to add.
     *
     * The public layers are the fallback and the geometry; the proxy is what carries DOC's website
     * facts into an offline area. A download with no enricher - a test, or a build with no proxy -
     * keeps exactly what the layers sent.
     */
    private val enricher: DocEnricher = DocEnricher.None
) {

    /**
     * Reads all four of DOC's services for [bounds], one after another.
     *
     * **A service that fails does not stop the others.** A walker who keeps an area wants whatever DOC
     * will answer with, and losing the huts because the bike service is down would be the app throwing
     * away the part that worked - so each is read on its own and the phone says which of them answered.
     */
    suspend fun downloadArea(
        bounds: DocBounds,
        /**
         * How many records have been read for the whole act so far, called after each page.
         *
         * Cumulative across the four services, because one line for the whole act is what a walker reads -
         * and reported from here because the paging is the only place that knows how far through a service
         * it is. The caller knows how many the box holds, so the two meet outside.
         */
        onProgress: (read: Int) -> Unit = {}
    ): DocAreaDownload {
        val items = mutableListOf<DocItem>()
        val counts = mutableMapOf<DocSource, Int>()
        val failed = mutableMapOf<DocSource, String>()

        var readSoFar = 0
        for (source in DocSource.entries) {
            when (val result = download(bounds, source) { read -> onProgress(readSoFar + read) }) {
                is DocDownloadResult.Done -> {
                    counts[source] = result.items.size
                    items += result.items
                    readSoFar += result.items.size
                }

                is DocDownloadResult.Failed -> {
                    // The pages that did arrive are kept: half a service's answer is still worth having.
                    counts[source] = result.items.size
                    items += result.items
                    readSoFar += result.items.size
                    failed[source] = result.message
                }
            }
        }
        // The website layer is read once for the whole box, not once per track: an area can hold
        // hundreds of lines, and its summaries are points that arrive in the same paged answer. A
        // failed companion read leaves the tracks exactly as the line service sent them.
        val withSummaries = if (items.any { it.source == DocSource.TRACKS }) {
            val companions = when (val result = reader.companions(bounds)) {
                is DocCompanionResult.Found -> result.companions
                is DocCompanionResult.Failed -> result.companions
            }
            mergeCompanions(items, companions)
        } else {
            items
        }
        // The proxy is the richer source, and it is optional: a download that cannot reach it keeps
        // the website summary the public layer already gave each track.
        val enriched = runCatching { enricher.enrich(withSummaries) }.getOrDefault(withSummaries)
        return DocAreaDownload(items = enriched, counts = counts, failed = failed)
    }

    /**
     * The same tracks with the website summary matched to each one.
     *
     * Matching is name first and distance second, so a summary from another part of the country cannot
     * land on a line that happens to share a name. A track with no match is kept untouched.
     */
    private fun mergeCompanions(
        items: List<DocItem>,
        companions: List<DocCompanion>
    ): List<DocItem> {
        if (companions.isEmpty()) return items
        return items.map { item ->
            if (item.source != DocSource.TRACKS) {
                item
            } else {
                DocTrackCompanions.bestFor(item, companions)
                    ?.let { item.withCompanion(it.summary()) }
                    ?: item
            }
        }
    }

    /**
     * Asks all four services **how much** they have in [bounds], and how many pictures DOC addresses for
     * it, without fetching any of it.
     *
     * One question a service, and one more for the three that publish pictures: seven small requests
     * where a download would be seven pages of records. It is asked before an area is taken on so the
     * walker knows what it costs them - and it is asked of the same services, with the same box and the
     * same filters, that the download itself would read, because a count of something else is worse than
     * no count at all.
     */
    suspend fun inventory(bounds: DocBounds): DocInventory {
        val records = mutableMapOf<DocSource, Int>()
        val failed = mutableMapOf<DocSource, String>()
        var pictures = 0

        for (source in DocSource.entries) {
            val query = DocQuery(source = source, bounds = bounds, limit = 1)
            when (val result = reader.count(query)) {
                is DocCountResult.Done -> records[source] = result.count
                is DocCountResult.Failed -> failed[source] = result.message
            }

            // A service that publishes no picture address for its records is not asked for one: the
            // tracks carry none, and a question about nothing has no answer worth waiting for.
            val where = source.pictureWhere() ?: continue
            when (val result = reader.count(query, where)) {
                is DocCountResult.Done -> pictures += result.count
                is DocCountResult.Failed -> failed[source] = result.message
            }
        }
        return DocInventory(records = records, pictures = pictures, failed = failed)
    }

    suspend fun download(
        bounds: DocBounds,
        source: DocSource,
        /** How many have been read for this service so far, called after each page. */
        onProgress: (read: Int) -> Unit = {}
    ): DocDownloadResult {
        val all = mutableListOf<DocItem>()
        var offset = 0
        while (true) {
            val page = reader.search(
                DocQuery(source = source, bounds = bounds, limit = pageSize, offset = offset)
            )
            when (page) {
                is DocReadResult.Failed -> return DocDownloadResult.Failed(page.message, all)
                is DocReadResult.Found -> {
                    all += page.items
                    onProgress(all.size)
                    // A short page is the last one; a full one means there is more behind it.
                    if (page.items.size < pageSize || all.size >= MAX_ITEMS) break
                    offset += page.items.size
                }
            }
        }
        return DocDownloadResult.Done(all)
    }

    companion object {
        /**
         * The downloader the app uses: DOC's layers, enriched through the Trail Day proxy.
         *
         * A named constructor rather than a default argument, because the enricher is the one thing
         * every screen's factory would otherwise have to remember to pass.
         */
        fun online(reader: DocReader = ArcGisDoc()): DocDownloader =
            DocDownloader(reader, enricher = DocProxyEnricher())

        /** The service's own ceiling per request, so one page is one request. */
        const val PAGE = 2000

        /**
         * Where a download stops, whatever the box.
         *
         * Four thousand, which was a generous count of tracks in a day's walk when DOC published 3,255
         * of them nationally - and is now the ceiling for whichever source is being read, so a box over
         * a whole island stops rather than filling the phone.
         */
        const val MAX_ITEMS = 4000
    }
}
