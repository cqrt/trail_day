package nz.mckenzie.trailday.doc

import nz.mckenzie.trailday.domain.doc.DocBounds
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocQuery
import nz.mckenzie.trailday.domain.doc.DocSource

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
    private val pageSize: Int = PAGE
) {

    /**
     * Reads all four of DOC's services for [bounds], one after another.
     *
     * **A service that fails does not stop the others.** A walker who keeps an area wants whatever DOC
     * will answer with, and losing the huts because the bike service is down would be the app throwing
     * away the part that worked - so each is read on its own and the phone says which of them answered.
     */
    suspend fun downloadArea(bounds: DocBounds): DocAreaDownload {
        val items = mutableListOf<DocItem>()
        val counts = mutableMapOf<DocSource, Int>()
        val failed = mutableMapOf<DocSource, String>()

        for (source in DocSource.entries) {
            when (val result = download(bounds, source)) {
                is DocDownloadResult.Done -> {
                    counts[source] = result.items.size
                    items += result.items
                }

                is DocDownloadResult.Failed -> {
                    // The pages that did arrive are kept: half a service's answer is still worth having.
                    counts[source] = result.items.size
                    items += result.items
                    failed[source] = result.message
                }
            }
        }
        return DocAreaDownload(items = items, counts = counts, failed = failed)
    }

    suspend fun download(bounds: DocBounds, source: DocSource): DocDownloadResult {
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
                    // A short page is the last one; a full one means there is more behind it.
                    if (page.items.size < pageSize || all.size >= MAX_ITEMS) break
                    offset += page.items.size
                }
            }
        }
        return DocDownloadResult.Done(all)
    }

    companion object {
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
