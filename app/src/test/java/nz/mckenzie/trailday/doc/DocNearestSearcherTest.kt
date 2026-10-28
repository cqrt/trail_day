package nz.mckenzie.trailday.doc

import kotlinx.coroutines.runBlocking
import nz.mckenzie.trailday.domain.doc.DocBounds
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocQuery
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading what DOC publishes **nearest first**, in rings outward.
 *
 * The fault this exists to prevent is the one a walker found on the other browser and would have found here
 * too: one page of a bigger box is spent on whichever rows DOC lists first, so huts **disappeared as the
 * radius grew**. The fake below lists the far rows first, the way DOC's own numbering does.
 */
class DocNearestSearcherTest {

    @Test
    fun `a bigger radius never shows fewer huts`() = runBlocking {
        val searcher = DocNearestSearcher(FakeService(furthestFirst()))

        val counts = listOf(10.0, 25.0, 50.0, 100.0, 200.0).map { radius ->
            val read = searcher.nearest(Query, at = At, radiusKm = radius) as DocReadResult.Found
            val inside = read.items.filter { item ->
                item.paths.flatten().any { distanceM(At, it) <= radius * 1000 }
            }
            radius to inside
        }

        counts.zipWithNext { before, after ->
            assertTrue(
                "at ${after.first} km there must be at least as many as at ${before.first} km, but there " +
                    "were ${after.second.size} against ${before.second.size}",
                after.second.size >= before.second.size
            )
        }
        val at100 = counts.first { it.first == 100.0 }.second.map { it.objectId }.toSet()
        val at200 = counts.first { it.first == 200.0 }.second.map { it.objectId }.toSet()
        assertTrue("everything at 100 km is still there at 200 km", at200.containsAll(at100))
    }

    @Test
    fun `the same hut inside two rings is one hut`() = runBlocking {
        // DOC publishes some of its huts twice, and a hut inside two rings arrives on both.
        val service = FakeService(furthestFirst() + furthestFirst().take(5))
        val read = DocNearestSearcher(service).nearest(Query, at = At, radiusKm = 100.0)

        val items = (read as DocReadResult.Found).items
        assertEquals("no row twice", items.size, items.map { it.objectId }.distinct().size)
    }

    @Test
    fun `the first ring asks for a page, and later ones for the repeats as well`() = runBlocking {
        val service = FakeService(furthestFirst())
        DocNearestSearcher(service).nearest(Query, at = At, radiusKm = 200.0)

        assertEquals("the first ring has nothing to repeat", 300, service.asked.first().limit)
        assertTrue(
            "an outer ring asks for more than the budget, which is what its repeats cost",
            service.asked.drop(1).any { it.limit > 0 }
        )
        assertTrue("the rings are asked in order", service.asked.size >= 2)
        assertEquals(DocSource.HUTS, service.asked.first().source)
    }

    @Test
    fun `a ring that fails leaves the rings already read`() = runBlocking {
        val read = DocNearestSearcher(FakeService(furthestFirst(), failAt = 3))
            .nearest(Query, at = At, radiusKm = 200.0)

        assertTrue("what arrived is kept", read is DocReadResult.Found)
        assertTrue((read as DocReadResult.Found).items.isNotEmpty())
    }

    @Test
    fun `a service that cannot be reached at all says so`() = runBlocking {
        val read = DocNearestSearcher(FakeService(emptyList(), failAt = 1))
            .nearest(Query, at = At, radiusKm = 100.0)

        assertTrue(read is DocReadResult.Failed)
    }

    /** Huts listed furthest first: the order DOC's own numbering puts them in. */
    private fun furthestFirst(): List<DocItem> = (1..400).map { step ->
        val metres = step * 400.0
        val lat = At.lat + metres / 111_320.0
        DocItem(
            source = DocSource.HUTS,
            objectId = step.toLong(),
            name = "Hut $step",
            paths = listOf(listOf(GeoPoint(lat, At.lng)))
        )
    }.reversed()

    private class FakeService(
        private val items: List<DocItem>,
        private val failAt: Int = Int.MAX_VALUE
    ) : DocReader {

        val asked = mutableListOf<DocQuery>()

        override suspend fun search(query: DocQuery): DocReadResult {
            asked += query
            if (asked.size >= failAt) return DocReadResult.Failed("HTTP 503")
            val box = query.near?.let { at ->
                nz.mckenzie.trailday.domain.tiles.boxAround(at, query.radiusKm.orZero())
            }
            val inside = items.filter { item ->
                box == null || item.paths.flatten().any {
                    it.lat in box.minLat..box.maxLat && it.lng in box.minLng..box.maxLng
                }
            }
            return DocReadResult.Found(inside.take(query.limit))
        }

        override suspend fun count(query: DocQuery, where: String): DocCountResult =
            DocCountResult.Done(items.size)

        override suspend fun companions(bounds: DocBounds): DocCompanionResult =
            DocCompanionResult.Found(emptyList())
    }

    private companion object {

        val At = GeoPoint(-46.4132, 168.3538)

        val Query = DocQuery(source = DocSource.HUTS)

        fun Double?.orZero(): Double = this ?: 0.0

        /** Flat-earth distance, which is all a test of ordering needs. */
        fun distanceM(a: GeoPoint, b: GeoPoint): Double {
            val north = (a.lat - b.lat) * 111_320.0
            val east = (a.lng - b.lng) * 111_320.0 * 0.69
            return kotlin.math.abs(kotlin.math.sqrt(north * north + east * east))
        }
    }
}
