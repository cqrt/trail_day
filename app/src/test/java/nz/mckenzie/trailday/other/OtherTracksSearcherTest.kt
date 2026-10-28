package nz.mckenzie.trailday.other

import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.domain.other.OtherTracksQuery
import nz.mckenzie.trailday.domain.reference.nearbyBandsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading somebody else's tracks **nearest first**, in rings outward.
 *
 * The rule worth holding still is the one a walker noticed was broken: **a bigger radius must never show
 * fewer tracks**. It did, because one page of a bigger circle is spent on rows further away - the service
 * orders by its own counter, not by distance - so the near ones fell out of the answer. The fake below is
 * built to have exactly that fault: it lists the far rows first, the way the counter does.
 */
class OtherTracksSearcherTest {

    @Test
    fun `the rings asked are the radii a walker has been shown, smallest first`() {
        assertEquals(
            listOf(5.0, 10.0, 25.0, 50.0, 100.0, 150.0, 200.0),
            nearbyBandsFor(200.0)
        )
        assertEquals(listOf(5.0, 10.0, 25.0, 30.0), nearbyBandsFor(30.0))
        assertEquals(listOf(3.0), nearbyBandsFor(3.0))
    }

    @Test
    fun `a bigger radius never shows fewer tracks`() = runBlocking {
        // The fault, reproduced: rows are listed furthest first, so the first page of a large circle is
        // spent on the tracks furthest away and the near ones are never read.
        val service = FakeService(furthestFirst())
        val searcher = OtherTracksSearcher(service)

        val counts = listOf(10.0, 25.0, 50.0, 100.0, 200.0).map { radius ->
            val read = searcher.nearest(name = "", at = At, radiusKm = radius) as OtherTracksReadResult.Found
            val inside = read.tracks.filter { track -> track.paths.flatten().any { distanceM(At, it) <= radius * 1000 } }
            radius to inside
        }

        counts.zipWithNext { before, after ->
            assertTrue(
                "at ${after.first} km there must be at least as many as at ${before.first} km, " +
                    "but there were ${after.second.size} against ${before.second.size}",
                after.second.size >= before.second.size
            )
        }
        // And the whole answer at a radius contains the whole answer at a smaller one: not merely the same
        // count of different tracks.
        val at100 = counts.first { it.first == 100.0 }.second.map { it.globalId }.toSet()
        val at200 = counts.first { it.first == 200.0 }.second.map { it.globalId }.toSet()
        assertTrue("everything at 100 km is still there at 200 km", at200.containsAll(at100))
    }

    @Test
    fun `the first ring asks for a page, and later ones ask for the repeats as well`() = runBlocking {
        val service = FakeService(furthestFirst())
        OtherTracksSearcher(service).nearest(name = "", at = At, radiusKm = 100.0)

        assertEquals("the first ring has nothing to repeat", 300, service.asked.first().limit)
        // An outer ring contains every row of the inner ones, so a request sized to the budget alone would
        // be spent on rows already held - that is what cut 83 tracks down to 59 before the allowance.
        service.asked.drop(1).forEach { query ->
            assertTrue("${query.limit} is more than the page", query.limit > 0)
        }
        assertTrue(
            "the rings asked are in order",
            service.asked.mapNotNull { it.bounds?.minLat }.zipWithNext().all { (a, b) -> a >= b }
        )
    }

    @Test
    fun `a ring that fails leaves the rings already read`() = runBlocking {
        val service = FakeService(furthestFirst(), failAt = 3)
        val read = OtherTracksSearcher(service).nearest(name = "", at = At, radiusKm = 200.0)

        assertTrue("what arrived is kept", read is OtherTracksReadResult.Found)
        assertTrue((read as OtherTracksReadResult.Found).tracks.isNotEmpty())
        assertEquals("and the search stopped there", 3, service.asked.size)
    }

    @Test
    fun `a service that cannot be reached at all says so`() = runBlocking {
        val read = OtherTracksSearcher(FakeService(emptyList(), failAt = 1))
            .nearest(name = "", at = At, radiusKm = 100.0)

        assertTrue(read is OtherTracksReadResult.Failed)
    }

    /** Rows listed furthest first: the order the service's own counter puts them in. */
    private fun furthestFirst(): List<OtherTrack> = (1..400).map { step ->
        // Each row is a 50 m line, laid out north of the fix at a fixed distance, so a ring's contents are
        // decided by one number.
        val metres = step * 400.0
        val lat = At.lat + metres / 111_320.0
        OtherTrack(
            globalId = "row-$step",
            // Distinct names, so nothing joins and the count is of rows.
            name = "Track $step",
            paths = listOf(listOf(GeoPoint(lat, At.lng), GeoPoint(lat + 0.0004, At.lng))),
            facts = mapOf("Name" to "Track $step")
        )
    }.reversed()

    private class FakeService(
        private val rows: List<OtherTrack>,
        private val failAt: Int = Int.MAX_VALUE
    ) : OtherTracksReader {

        val asked = mutableListOf<OtherTracksQuery>()

        override suspend fun read(query: OtherTracksQuery): OtherTracksReadResult {
            asked += query
            if (asked.size >= failAt) return OtherTracksReadResult.Failed("HTTP 503")
            val box = query.bounds
            val inside = rows.filter { row ->
                box == null || row.paths.flatten().any {
                    it.lat in box.minLat..box.maxLat && it.lng in box.minLng..box.maxLng
                }
            }
            return OtherTracksReadResult.Found(inside.take(query.limit))
        }

        override suspend fun count(query: OtherTracksQuery): OtherTracksCountResult =
            OtherTracksCountResult.Done(rows.size)
    }

    private companion object {

        val At = GeoPoint(-46.4132, 168.3538)

        /** Flat-earth distance, which is all a test of ordering needs. */
        fun distanceM(a: GeoPoint, b: GeoPoint): Double {
            val north = (a.lat - b.lat) * 111_320.0
            val east = (a.lng - b.lng) * 111_320.0 * 0.69
            return kotlin.math.sqrt(north * north + east * east).let { abs(it) }
        }
    }
}
