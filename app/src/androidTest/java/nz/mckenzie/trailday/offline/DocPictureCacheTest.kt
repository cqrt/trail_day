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

    @Test
    fun theAddressesThatArePicturesAreKeptAndTheOthersLeftAlone() = runBlocking {
        val asked = mutableListOf<String>()
        val cache = DocPictureCache(db.offlineDocItemDao(), fetch = { url -> asked += url; byteArrayOf(1, 2, 3) })

        val kept = cache.keep(
            listOf(
                jpg,
                // DOC's own data addresses a page about some things rather than a picture of them.
                "https://www.doc.govt.nz/thumbs/large/link/def.aspx",
                "  $jpg  ".trim() + "?width=400",
                jpg
            )
        )

        assertEquals("a picture's address, once, and never the page", listOf(jpg, "$jpg?width=400"), asked)
        assertEquals(DocPictures(asked = 2, kept = 2), kept)
        assertEquals(2, cache.count())
        assertArrayEquals("and the bytes are what DOC served", byteArrayOf(1, 2, 3), cache.read(jpg))
    }

    @Test
    fun aPictureThatWillNotComeIsNotAFailedDownload() = runBlocking {
        val cache = DocPictureCache(
            db.offlineDocItemDao(),
            fetch = { url -> if (url.endsWith("good.jpg")) byteArrayOf(9) else null }
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
        val cache = DocPictureCache(db.offlineDocItemDao(), fetch = { fetches++; byteArrayOf(1) })

        cache.keep(listOf(jpg))
        val again = cache.keep(listOf(jpg))

        assertEquals("asked for the second time, fetched only once", 1, fetches)
        assertEquals(DocPictures(asked = 1, kept = 1), again)
    }

    @Test
    fun aPictureFarLargerThanAThumbnailIsNotKept() = runBlocking {
        val cache = DocPictureCache(db.offlineDocItemDao(), fetch = { ByteArray(64) }, maxBytes = 32)

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
        val pictures = DocPictureCache(dao, fetch = { byteArrayOf(1) })
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
        val pictures = DocPictureCache(dao, fetch = { byteArrayOf(1) })
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
