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
 * Reads everything one of DOC's services has in a box, a page at a time.
 *
 * The service answers at most 2,000 features a request, so an area with more is read by asking again
 * from where the last page ended - which is why [DocQuery.offset] exists. The total is capped: a box
 * over a whole island is not an offline area for a walk, and a runaway download is worse than a short
 * one.
 *
 * **One source per download**, and the caller decides which: a box of the ground is what a walker keeps
 * before losing reception, and the four kinds of thing in it are four reads because DOC publishes them
 * as four services. See the plan for the step that downloads all four in one act.
 *
 * Kept apart from storage so the paging can be tested against a fake reader with no network and no
 * database - the pages are the whole of what it decides.
 */
class DocDownloader(
    private val reader: DocReader,
    private val pageSize: Int = PAGE
) {

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
