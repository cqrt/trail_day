package nz.mckenzie.trailday.doc

import nz.mckenzie.trailday.domain.doc.DocBounds
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.doc.DocQuery
import nz.mckenzie.trailday.domain.geo.GeoPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a whole area's tracks a page at a time.
 *
 * The service answers at most 2,000 a request, so an area with more is a loop - and the loop's two
 * worth-holding rules are that it keeps asking until a page comes back short, and that a failure
 * half way through keeps the pages that did arrive rather than throwing them away.
 */
class DocDownloaderTest {

    private fun track(id: Long): DocItem = DocItem(
        source = DocSource.TRACKS,
        objectId = id,
        name = "Track $id",
        paths = listOf(listOf(GeoPoint(-46.6, 168.3), GeoPoint(-46.61, 168.31))),
        facts = mapOf("SubObjectType" to "Walking Track")
    )

    private val box = DocBounds(minLat = -46.7, minLng = 168.1, maxLat = -46.5, maxLng = 168.6)

    /** A source that hands back prepared pages, recording the offset each was asked at. */
    private class PagingSource(private val pages: List<List<DocItem>>) : DocReader {
        val offsets = mutableListOf<Int>()
        private var call = 0

        override suspend fun search(query: DocQuery): DocReadResult {
            offsets += query.offset
            val page = pages.getOrElse(call) { emptyList() }
            call++
            return DocReadResult.Found(page)
        }
    }

    @Test
    fun `a full page is followed by the next, until a short one ends it`() {
        val source = PagingSource(
            listOf(
                List(DocDownloader.PAGE) { track(it.toLong()) },
                listOf(track(9999))
            )
        )

        val result = runBlocking { DocDownloader(source).download(box, DocSource.TRACKS) }

        assertEquals(DocDownloader.PAGE + 1, (result as DocDownloadResult.Done).items.size)
        assertEquals("the second page was asked for where the first ended", listOf(0, 2000), source.offsets)
    }

    @Test
    fun `one short page is the whole download`() {
        val source = PagingSource(listOf(listOf(track(1), track(2))))

        val done = runBlocking { DocDownloader(source).download(box, DocSource.TRACKS) } as DocDownloadResult.Done

        assertEquals(2, done.items.size)
        assertEquals(listOf(0), source.offsets)
    }

    @Test
    fun `a failure half way keeps the pages that arrived`() {
        val source = object : DocReader {
            private var call = 0
            override suspend fun search(query: DocQuery): DocReadResult {
                call++
                return if (call == 1) {
                    DocReadResult.Found(List(DocDownloader.PAGE) { track(it.toLong()) })
                } else {
                    DocReadResult.Failed("Unable to resolve host")
                }
            }
        }

        val failed = runBlocking { DocDownloader(source).download(box, DocSource.TRACKS) } as DocDownloadResult.Failed

        assertEquals("the first page is kept", DocDownloader.PAGE, failed.items.size)
        assertTrue("and the reason is the service's", failed.message.contains("Unable to resolve host"))
    }
}
