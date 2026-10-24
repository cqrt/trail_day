package nz.mckenzie.trailday.offline

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.doc.ArcGisDoc
import nz.mckenzie.trailday.doc.DocDownloader
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The cache holding one row per thing, whatever an older build put there.
 *
 * DOC publishes some of its huts twice - the same name, the same place and the same point, under two
 * numbers - and the app stored both copies before it knew to drop them. A download stores one now, which
 * is the first half of the rule; this is the other half, that a cache already holding both reads back
 * one, so a map or a list drawn from it cannot show a hut twice.
 */
@RunWith(AndroidJUnit4::class)
class OfflineDocCacheTest {

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

    private fun store() = OfflineDocStore(
        dao = db.offlineDocItemDao(),
        downloader = DocDownloader(ArcGisDoc())
    )

    /** One of DOC's huts, as the service publishes it. */
    private fun hut(id: Long, name: String, place: String, region: String, at: GeoPoint) = DocItem(
        source = DocSource.HUTS,
        objectId = id,
        name = name,
        paths = listOf(listOf(at)),
        facts = mapOf("place" to place, "region" to region)
    )

    @Test
    fun aCacheHoldingBothOfDOCsRowsForOneHutReadsBackOne() = runBlocking {
        val store = store()
        val crow = GeoPoint(-39.76697, 176.08515)

        // What an older build stored: both of DOC's rows for the same hut, a few hundred numbers apart.
        store.store(
            listOf(
                hut(621583, "Crow Hut", "Ruahine Forest Park", "Manawatu/Whanganui", crow),
                hut(622512, "Crow Hut", "Ruahine Forest Park", "Manawatu/Whanganui", crow)
            )
        )

        assertEquals("one hut", 1, store.cached(DocSource.HUTS).size)
        assertEquals("and the count says one too", mapOf(DocSource.HUTS to 1), store.counts())
    }

    @Test
    fun twoHutsThatShareANameAreBothKept() = runBlocking {
        val store = store()

        store.store(
            listOf(
                hut(1, "Crow Hut", "Ruahine Forest Park", "Manawatu/Whanganui", GeoPoint(-39.76697, 176.08515)),
                hut(2, "Crow Hut", "Kahurangi National Park", "Nelson/Tasman", GeoPoint(-41.29258, 172.46863))
            )
        )

        assertEquals("two huts", 2, store.cached(DocSource.HUTS).size)
        assertEquals(mapOf(DocSource.HUTS to 2), store.counts())
    }
}
