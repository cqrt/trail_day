package nz.mckenzie.trailday.doc

import nz.mckenzie.trailday.domain.doc.DocBounds
import nz.mckenzie.trailday.domain.doc.DocCompanion
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.doc.DocQuery
import nz.mckenzie.trailday.domain.doc.DocTrackCompanion
import nz.mckenzie.trailday.domain.doc.pictureWhere
import nz.mckenzie.trailday.domain.doc.withCompanion
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

        override suspend fun count(query: DocQuery, where: String): DocCountResult =
            DocCountResult.Done(0)
    }

    @Test
    fun `a download says how far through the whole act it is`() = runBlocking {
        // Two pages for the service asked, then a short page for each of the others. The figures are
        // cumulative across the four, because one line for the whole act is what a walker reads.
        val source = PagingSource(
            listOf(
                List(DocDownloader.PAGE) { track(it.toLong()) },
                listOf(track(9999))
            )
        )
        val seen = mutableListOf<Int>()

        DocDownloader(source).downloadArea(box) { seen += it }

        assertTrue("progress was reported at all", seen.isNotEmpty())
        assertEquals("and it never goes backwards", seen.sorted(), seen)
        assertEquals("the last figure is everything that was read", 2001, seen.last())
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

            override suspend fun count(query: DocQuery, where: String): DocCountResult =
                DocCountResult.Done(0)
        }

        val failed = runBlocking { DocDownloader(source).download(box, DocSource.TRACKS) } as DocDownloadResult.Failed

        assertEquals("the first page is kept", DocDownloader.PAGE, failed.items.size)
        assertTrue("and the reason is the service's", failed.message.contains("Unable to resolve host"))
    }

    /** A reader that answers each of DOC's services with one thing of its own, or fails for the named. */
    private class BySource(private val fail: Set<DocSource> = emptySet()) : DocReader {
        val asked = mutableListOf<DocSource>()

        override suspend fun search(query: DocQuery): DocReadResult {
            asked += query.source
            if (query.source in fail) {
                return DocReadResult.Failed("the ${query.source.displayName.lowercase()} are down")
            }
            return DocReadResult.Found(
                listOf(
                    DocItem(
                        source = query.source,
                        objectId = query.source.ordinal.toLong(),
                        name = "One ${query.source.singular}",
                        paths = listOf(listOf(GeoPoint(-46.6, 168.3), GeoPoint(-46.61, 168.31)))
                    )
                )
            )
        }

        override suspend fun count(query: DocQuery, where: String): DocCountResult =
            DocCountResult.Done(if (query.source in fail) 0 else 1)
    }

    @Test
    fun `an area is read from all four of DOC's services in one act`() {
        val source = BySource()

        val download = runBlocking { DocDownloader(source).downloadArea(box) }

        assertEquals("every service DOC publishes was asked", DocSource.entries, source.asked)
        assertEquals("and each one's answer is here", 4, download.items.size)
        assertEquals(
            "counted service by service, so the phone can say what arrived",
            DocSource.entries.associateWith { 1 },
            download.counts
        )
        assertTrue("with nothing left unread", download.failed.isEmpty())
    }

    /** A reader whose area has one track, and a website summary for it. */
    private class WithCompanion : DocReader {
        override suspend fun search(query: DocQuery): DocReadResult =
            if (query.source == DocSource.TRACKS) {
                DocReadResult.Found(
                    listOf(
                        DocItem(
                            source = DocSource.TRACKS,
                            objectId = 1,
                            name = "Forks Flat Tk",
                            paths = listOf(
                                listOf(GeoPoint(-46.1282, 168.6067), GeoPoint(-46.13, 168.61))
                            ),
                            facts = mapOf("SubObjectType" to "Walking Track")
                        )
                    )
                )
            } else {
                DocReadResult.Found(emptyList())
            }

        override suspend fun count(query: DocQuery, where: String): DocCountResult =
            DocCountResult.Done(0)

        override suspend fun companions(bounds: DocBounds): DocCompanionResult =
            DocCompanionResult.Found(
                listOf(
                    DocCompanion(
                        name = "Forks Flat Track",
                        point = GeoPoint(-46.1282, 168.6067),
                        introduction = "A gentle walk through native bush.",
                        difficulty = "Easy",
                        completionTime = "1 hour",
                        webPage = "https://www.doc.govt.nz/link/example.aspx",
                        thumbnail = "https://www.doc.govt.nz/images/example.jpg"
                    )
                )
            )
    }

    @Test
    fun `an area download folds each track's website summary into its facts`() {
        val download = runBlocking { DocDownloader(WithCompanion()).downloadArea(box) }

        val track = download.items.single { it.source == DocSource.TRACKS }
        assertEquals("A gentle walk through native bush.", track.introduction)
        assertEquals("Easy", track.difficulty)
        assertEquals("1 hour", track.completionTime)
        assertEquals("https://www.doc.govt.nz/link/example.aspx", track.link)
        assertEquals(
            "the website picture travels with the track, so the area's pictures can keep it",
            "https://www.doc.govt.nz/images/example.jpg",
            track.pictureUrl
        )
        assertEquals("and the line's own facts are still there", "Walking Track", track.kind)
    }

    /** An enricher that adds one API fact, or refuses to answer at all. */
    private class Enriching(private val fail: Boolean = false) : DocEnricher {
        override suspend fun enrich(items: List<DocItem>): List<DocItem> {
            if (fail) error("no proxy")
            return items.map { item ->
                if (item.source == DocSource.TRACKS) {
                    item.withCompanion(DocTrackCompanion(permittedActivities = "Walking and tramping"))
                } else {
                    item
                }
            }
        }
    }

    @Test
    fun `an area download lets the proxy add its richer facts`() {
        val download = runBlocking {
            DocDownloader(WithCompanion(), enricher = Enriching()).downloadArea(box)
        }

        val track = download.items.single { it.source == DocSource.TRACKS }
        assertEquals("Walking and tramping", track.permittedActivities)
        assertEquals("and the public summary is still there", "A gentle walk through native bush.", track.introduction)
    }

    @Test
    fun `an enricher that cannot answer leaves the layers' own facts alone`() {
        val download = runBlocking {
            DocDownloader(WithCompanion(), enricher = Enriching(fail = true)).downloadArea(box)
        }

        val track = download.items.single { it.source == DocSource.TRACKS }
        assertEquals("A gentle walk through native bush.", track.introduction)
        assertEquals(null, track.permittedActivities)
    }

    @Test
    fun `one service failing does not stop the others, and is named`() {
        val source = BySource(fail = setOf(DocSource.HUTS))

        val download = runBlocking { DocDownloader(source).downloadArea(box) }

        assertEquals("the other three still arrived", 3, download.items.size)
        assertEquals("and the huts are the ones named as unread", setOf(DocSource.HUTS), download.failed.keys)
        assertEquals("with no huts counted, because none arrived", 0, download.counts[DocSource.HUTS])
        assertEquals("while the three that answered are counted", 1, download.counts[DocSource.TRACKS])
    }

    /**
     * A reader that counts what it would give, and what DOC addresses a picture for, keeping the two
     * questions apart the way the services do.
     */
    private class Counting(
        private val records: Map<DocSource, Int>,
        private val pictures: Map<DocSource, Int> = emptyMap(),
        private val fail: Set<DocSource> = emptySet()
    ) : DocReader {

        val asked = mutableListOf<Pair<DocSource, String>>()

        override suspend fun search(query: DocQuery): DocReadResult =
            DocReadResult.Found(emptyList())

        override suspend fun count(query: DocQuery, where: String): DocCountResult {
            asked += query.source to where
            if (query.source in fail) {
                return DocCountResult.Failed("the ${query.source.displayName.lowercase()} are down")
            }
            val picturesWanted = where == query.source.pictureWhere()
            return DocCountResult.Done(
                (if (picturesWanted) pictures[query.source] else records[query.source]) ?: 0
            )
        }
    }

    @Test
    fun `an inventory counts every service, and the pictures of the three that publish them`() {
        val source = Counting(
            records = mapOf(
                DocSource.TRACKS to 412,
                DocSource.HUTS to 38,
                DocSource.CAMPSITES to 6,
                DocSource.BIKES to 93
            ),
            pictures = mapOf(DocSource.HUTS to 38, DocSource.CAMPSITES to 4, DocSource.BIKES to 20)
        )

        val inventory = runBlocking { DocDownloader(source).inventory(box) }

        assertEquals(
            "every service's own count is here",
            mapOf(
                DocSource.TRACKS to 412,
                DocSource.HUTS to 38,
                DocSource.CAMPSITES to 6,
                DocSource.BIKES to 93
            ),
            inventory.records
        )
        assertEquals("the pictures of the three place services, added up", 62, inventory.pictures)
        assertEquals("and 549 things in all", 549, inventory.totalRecords)
        assertTrue("with every question answered: ${inventory.failed}", inventory.complete)

        // The tracks carry no picture address, so DOC is not asked for one: a question about nothing has
        // no answer worth the walker's time.
        assertTrue(
            "and the tracks are never asked about pictures",
            source.asked.none { (service, where) -> service == DocSource.TRACKS && where == service.pictureWhere() }
        )
    }

    @Test
    fun `a service that will not answer makes the whole count unknown`() {
        // A partial inventory is not offered as an estimate: a service that could not be asked is a
        // service whose records and size are missing, and "500 KB" about an area with three services
        // unheard from is a figure that misleads rather than informs.
        val source = Counting(
            records = mapOf(DocSource.TRACKS to 412),
            fail = setOf(DocSource.HUTS)
        )

        val inventory = runBlocking { DocDownloader(source).inventory(box) }

        assertTrue("the answer is marked as not the whole truth", !inventory.complete)
        assertEquals(setOf(DocSource.HUTS), inventory.failed.keys)
    }
}
