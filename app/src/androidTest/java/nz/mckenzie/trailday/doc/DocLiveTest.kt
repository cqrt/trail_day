package nz.mckenzie.trailday.doc

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import nz.mckenzie.trailday.domain.doc.DocBounds
import nz.mckenzie.trailday.domain.doc.DocQuery
import nz.mckenzie.trailday.domain.doc.DocShape
import nz.mckenzie.trailday.domain.doc.DocSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DOC's four services, asked for real.
 *
 * Everything else about the reading is pinned against answers copied from these services, which proves
 * the app reads the shape it was written for - but not that the shape is still the one DOC sends. That
 * is what this is for: one known thing from each of the four, read through the app's own reader, so a
 * service that moves a field or stops answering is seen here rather than in the hills.
 *
 * **A service that cannot be reached skips rather than fails.** DOC being down is not a fault in this
 * app, and a suite that goes red for somebody else's outage is a suite people learn to ignore. What it
 * asserts is what DOC said *when it answered* - which is the thing worth knowing.
 */
@RunWith(AndroidJUnit4::class)
class DocLiveTest {

    private val reader = ArcGisDoc()

    /** Somewhere in the Southern Alps: inside every one of the four services' coverage. */
    private suspend fun read(source: DocSource, name: String) = reader.search(
        DocQuery(source = source, nameContains = name, limit = 5)
    )

    private fun found(result: DocReadResult, source: DocSource): List<nz.mckenzie.trailday.domain.doc.DocItem> {
        assumeTrue(
            "DOC's ${source.displayName} service could not be reached: " +
                (result as? DocReadResult.Failed)?.message,
            result is DocReadResult.Found
        )
        return (result as DocReadResult.Found).items
    }

    @Test
    fun theTracksServiceAnswersWithLinesThatCarryTheirKind() = runBlocking {
        val items = found(read(DocSource.TRACKS, "Angelus"), DocSource.TRACKS)

        assumeTrue("no track of that name today", items.isNotEmpty())
        assertEquals(DocShape.LINE, DocSource.TRACKS.shape)
        items.forEach { track ->
            assertTrue("a track is a line", track.pointCount >= 2)
            assertTrue("and it has a length of its own", track.lengthM > 0.0)
            assertTrue("with a name", track.name.isNotBlank())
        }
        assertTrue(
            "a track keeps what DOC says it is: ${items.first().facts.keys}",
            items.any { it.kind != null }
        )
    }

    @Test
    fun theHutsServiceAnswersWithPlacesAndWhatDOCSaysAboutThem() = runBlocking {
        val items = found(read(DocSource.HUTS, "Angelus"), DocSource.HUTS)

        assumeTrue("no hut of that name today", items.isNotEmpty())
        val hut = items.first()
        assertEquals("a hut is one coordinate", 1, hut.pointCount)
        assertTrue("where it is", hut.point != null)
        assertTrue("a hut has a name", hut.name.isNotBlank())
        assertTrue(
            "and keeps DOC's own answer about booking it: ${hut.facts}",
            hut.facts.containsKey("bookable")
        )
        assertTrue(
            "and where DOC puts it: ${hut.facts}",
            hut.place != null || hut.region != null
        )
    }

    @Test
    fun theCampsitesServiceAnswersWithPlacesAndTheirSites() = runBlocking {
        val items = found(read(DocSource.CAMPSITES, "Kiosk Creek"), DocSource.CAMPSITES)

        assumeTrue("no campsite of that name today", items.isNotEmpty())
        val camp = items.first()
        assertEquals("a campsite is one coordinate", 1, camp.pointCount)
        assertTrue("a campsite has a name", camp.name.isNotBlank())
        assertTrue(
            "and keeps how many sites it has: ${camp.facts}",
            camp.unpoweredSites != null || camp.poweredSites != null
        )
    }

    @Test
    fun theBikeServiceAnswersWithPlacesAndHowHardTheyAre() = runBlocking {
        val items = found(read(DocSource.BIKES, "Globe Hill"), DocSource.BIKES)

        assumeTrue("nothing of that name today", items.isNotEmpty())
        val bike = items.first()
        assertEquals(
            "the bike layer is points, not lines: it says where riding is permitted",
            1,
            bike.pointCount
        )
        assertTrue("a bike location has a name", bike.name.isNotBlank())
        assertTrue(
            "and keeps how hard DOC says it is and how long it takes: ${bike.facts}",
            bike.difficulty != null || bike.bikingTime != null
        )
    }

    @Test
    fun anAreaIsReadFromAllOfDocsServicesInOneAct() = runBlocking {
        // Nelson Lakes: DOC has tracks, huts and campsites in this box, and the fourth service is
        // asked for as surely as the other three.
        val box = DocBounds(minLat = -41.75, minLng = 172.55, maxLat = -41.50, maxLng = 172.85)

        val download = DocDownloader(reader, pageSize = 100).downloadArea(box)

        assumeTrue(
            "DOC could not be reached for any of the four: ${download.failed}",
            download.failed.keys.size < DocSource.entries.size
        )
        val answered = download.counts.filterValues { it > 0 }.keys
        assertTrue(
            "more than one of the four answered in one act: ${download.counts}",
            answered.size >= 2
        )
        assertEquals(
            "and the counts add up to everything that is here",
            download.items.size,
            download.counts.values.sum()
        )
        assertTrue(
            "each thing says which service it came from, so the map draws it as what it is",
            download.items.all { it.source in DocSource.entries }
        )
    }

    @Test
    fun anAreasInventoryIsCountedWithoutFetchingAnyOfIt() = runBlocking {
        // The count a walker reads before taking an area on: what DOC has in the box, and how many of the
        // pictures it would have to spend its time on. Counted, not fetched - see `DocInventory`.
        val box = DocBounds(minLat = -41.75, minLng = 172.55, maxLat = -41.50, maxLng = 172.85)

        val inventory = DocDownloader(reader).inventory(box)

        assumeTrue(
            "DOC could not be asked: ${inventory.failed}",
            inventory.failed.keys.size < DocSource.entries.size
        )
        assertTrue(
            "something is in that box, and DOC's own figures say so: ${inventory.records}",
            inventory.totalRecords > 0
        )
        assertTrue(
            "and the pictures of it are counted as well: ${inventory.pictures}",
            inventory.pictures > 0
        )
    }
}
