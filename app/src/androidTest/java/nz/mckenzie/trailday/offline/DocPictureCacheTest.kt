package nz.mckenzie.trailday.offline

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.doc.ArcGisDoc
import nz.mckenzie.trailday.doc.DocDownloader
import nz.mckenzie.trailday.doc.DocPictures
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DOC's pictures, kept in the cache the material lives in.
 *
 * The fetch is the only thing these tests hold still - a fake that hands back bytes, or refuses to - so
 * what is checked is the cache's own rules: what it keeps, what it keeps only once, what it does with a
 * picture that will not come, and what clearing the material does to the pictures.
 */
@RunWith(AndroidJUnit4::class)
class DocPictureCacheTest {

    private lateinit var db: TrailDayDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TrailDayDatabase::class.java
        ).build()
    }

    @After
    fun close() = db.close()

    private val jpg = "https://www.doc.govt.nz/thumbs/large/link/abc.jpg"

    /**
     * Bytes that are a picture, or at least begin like one.
     *
     * The cache keeps what came back only if it is a picture by its own first bytes - DOC's thumbnail
     * addresses answer with JPEGs - so the fake fetcher has to hand back something that looks like one.
     */
    private fun jpeg(size: Int = 3) = ByteArray(size).also {
        if (size > 0) it[0] = 0xFF.toByte()
        if (size > 1) it[1] = 0xD8.toByte()
    }

    @Test
    fun theAddressesThatArePicturesAreKeptAndTheOthersLeftAlone() = runBlocking {
        val asked = mutableListOf<String>()
        val cache = DocPictureCache(db.offlineDocItemDao(), fetch = { url -> asked += url; jpeg() })

        val kept = cache.keep(
            listOf(
                jpg,
                // A page about a thing is not a picture of it, whatever its address says.
                "https://www.doc.govt.nz/parks-and-recreation/places-to-go/def.aspx",
                "  $jpg  ".trim() + "?width=400",
                jpg
            )
        )

        assertEquals("a picture's address, once, and never the page", listOf(jpg, "$jpg?width=400"), asked)
        assertEquals(DocPictures(asked = 2, kept = 2), kept)
        assertEquals(2, cache.count())
        assertArrayEquals("and the bytes are what DOC served", jpeg(), cache.read(jpg))
    }

    @Test
    fun aPictureThatWillNotComeIsNotAFailedDownload() = runBlocking {
        val cache = DocPictureCache(
            db.offlineDocItemDao(),
            fetch = { url -> if (url.endsWith("good.jpg")) jpeg(9) else null }
        )

        val kept = cache.keep(listOf("https://www.doc.govt.nz/good.jpg", "https://www.doc.govt.nz/missing.jpg"))

        assertEquals("both were asked for", 2, kept.asked)
        assertEquals("one arrived", 1, kept.kept)
        assertNull("and the one that did not is simply not here", cache.read("https://www.doc.govt.nz/missing.jpg"))
        assertEquals("while the one that did is", 1, cache.count())
    }

    @Test
    fun keepingTheSamePictureAgainFetchesNothingNew() = runBlocking {
        var fetches = 0
        val cache = DocPictureCache(db.offlineDocItemDao(), fetch = { fetches++; jpeg() })

        cache.keep(listOf(jpg))
        val again = cache.keep(listOf(jpg))

        assertEquals("asked for the second time, fetched only once", 1, fetches)
        assertEquals(DocPictures(asked = 1, kept = 1), again)
    }

    @Test
    fun aPageThatArrivesWhereAPictureWasPromisedIsNotKept() = runBlocking {
        // DOC's thumbnail addresses are `.aspx` and answer with a JPEG; any other `.aspx` is a page. The
        // address cannot promise a picture by itself, so what comes back is checked - and a page is not kept.
        val cache = DocPictureCache(
            db.offlineDocItemDao(),
            fetch = { "<!DOCTYPE html><html><body>Not a photograph.</body></html>".toByteArray() }
        )

        val kept = cache.keep(listOf("https://www.doc.govt.nz/thumbs/large/link/def.aspx"))

        assertEquals("it was asked for", 1, kept.asked)
        assertEquals("and refused", 0, kept.kept)
        assertEquals(0, cache.count())
    }

    @Test
    fun aPictureFarLargerThanAThumbnailIsNotKept() = runBlocking {
        val cache = DocPictureCache(db.offlineDocItemDao(), fetch = { jpeg(64) }, maxBytes = 32)

        val kept = cache.keep(listOf(jpg))

        assertEquals("nothing was kept of it", 0, kept.kept)
        assertEquals(0, cache.count())
    }

    @Test
    fun anAreaWithNoPicturesAtAllAsksForNone() = runBlocking {
        val cache = DocPictureCache(db.offlineDocItemDao(), fetch = { error("no picture should be fetched") })

        assertEquals(DocPictures.NONE, cache.keep(emptyList()))
        assertEquals(DocPictures.NONE, cache.keep(listOf("https://www.doc.govt.nz/a.aspx")))
    }

    @Test
    fun clearingTheDOCsCacheTakesThePicturesWithIt() = runBlocking {
        val dao = db.offlineDocItemDao()
        val pictures = DocPictureCache(dao, fetch = { jpeg() })
        val store = OfflineDocStore(dao = dao, downloader = DocDownloader(ArcGisDoc()), pictures = pictures)

        pictures.keep(listOf("https://www.doc.govt.nz/a.jpg", "https://www.doc.govt.nz/b.jpg"))
        assertEquals(2, store.pictureCount())

        store.clear()

        assertEquals("one entry, cleared as one thing", 0, store.pictureCount())
    }

    @Test
    fun thePictureCountIsSaidOnItsOwnRatherThanWithTheMaterial() = runBlocking {
        // The bug this pins: the pictures land *after* the records, and re-downloading an area changes no
        // record at all - so a count that only moved when the records moved sat at nothing while seven
        // pictures arrived. The count is its own live question about its own table.
        val dao = db.offlineDocItemDao()
        val pictures = DocPictureCache(dao, fetch = { jpeg() })
        val store = OfflineDocStore(dao = dao, downloader = DocDownloader(ArcGisDoc()), pictures = pictures)

        val seen = Channel<Int>(Channel.CONFLATED)
        val watching = launch(Dispatchers.IO) { store.observePictureCount().collect { seen.send(it) } }
        try {
            assertEquals("nothing at first", 0, withTimeout(5_000) { seen.receive() })

            pictures.keep(listOf("https://www.doc.govt.nz/a.jpg", "https://www.doc.govt.nz/b.jpg"))

            // The count follows the pictures as they land - one insert is one invalidation, so which
            // values arrive on the way is Room's business. What matters is that it arrives at two, with
            // no record of the material having changed.
            val settled = withTimeout(5_000) {
                var value = seen.receive()
                while (value != 2) value = seen.receive()
                value
            }
            assertEquals("and both once they have landed", 2, settled)
        } finally {
            watching.cancel()
        }
    }
}
