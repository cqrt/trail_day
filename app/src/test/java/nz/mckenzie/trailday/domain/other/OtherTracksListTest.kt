package nz.mckenzie.trailday.domain.other

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.reference.ReferenceSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A page of somebody else's tracks: what a filter offers, what it keeps, and the order it is in.
 *
 * The choices worth holding still are that **a row that answers a filter twice matches on either answer**
 * (a track two councils both published), that nothing ticked means everything, that what cannot be measured
 * sorts last rather than first, and that a track published in pieces is as near as its nearest piece.
 */
class OtherTracksListTest {

    @Test
    fun `a filter offers only what the page actually holds`() {
        val page = listOf(walk, ride, plain)

        assertEquals(listOf("Path", "Short Walk"), page.valuesPresent("Walk"))
        assertEquals(
            listOf("Tasman District Council", "Wellington City Council"),
            page.valuesPresent("DataSupplier")
        )
        // Nothing on the page carries a dog rule, so the group has nothing to offer and is not offered.
        assertTrue(page.valuesPresent("DogAccess").isEmpty())
    }

    @Test
    fun `a row that answers a filter twice matches on either answer`() {
        // A track two councils both published names both, and narrowing by one of them means a track that
        // council had a hand in - not only one where it happens to be named first.
        val both = track(
            name = "Southern Walkway",
            facts = arrayOf(
                "Walk" to "3",
                "DataSupplier" to "Wellington City Council, Greater Wellington Regional Council"
            ),
            paths = listOf(listOf(GeoPoint(-41.2860, 174.7760), GeoPoint(-41.2865, 174.7765)))
        )

        assertEquals(
            listOf("Greater Wellington Regional Council", "Wellington City Council"),
            listOf(both).valuesPresent("DataSupplier")
        )
        assertEquals(
            listOf(both),
            listOf(both).showing(mapOf("DataSupplier" to setOf("Greater Wellington Regional Council")))
        )
    }

    @Test
    fun `nothing ticked is everything, and a ticked value keeps only its own`() {
        val page = listOf(walk, ride)

        assertEquals(2, page.showing(emptyMap()).size)
        assertEquals(2, page.showing(mapOf("Walk" to emptySet())).size)
        assertEquals(listOf(ride), page.showing(mapOf("Walk" to setOf("Path"))))
    }

    @Test
    fun `a filter and another filter both have to agree`() {
        val page = listOf(walk, ride)

        assertEquals(
            listOf(walk),
            page.showing(mapOf("Walk" to setOf("Short Walk"), "DataSupplier" to setOf("Wellington City Council")))
        )
        assertTrue(
            page.showing(mapOf("Walk" to setOf("Short Walk"), "DataSupplier" to setOf("Auckland Council"))).isEmpty()
        )
    }

    @Test
    fun `nearest sorts by the nearest point of the line, and what cannot be measured sorts last`() {
        val page = listOf(far, near)

        assertEquals(listOf(near, far), page.showing(emptyMap(), ReferenceSort.NEAREST, wellington))
        // With no fix to measure from there is no nearest, and the order falls back to the name rather
        // than putting something at the top on a shrug.
        assertEquals(listOf(far, near), page.showing(emptyMap(), ReferenceSort.NEAREST, from = null))
    }

    @Test
    fun `a track published in pieces is as near as its nearest piece`() {
        // The near piece is a *second path*: a side track hanging off a route does not make the route look
        // distant, and joining the two would have drawn a line across whatever is between them.
        val split = track(
            name = "Split track",
            facts = arrayOf("Walk" to "3"),
            paths = listOf(
                listOf(GeoPoint(-41.5000, 174.9000), GeoPoint(-41.5010, 174.9010)),
                listOf(GeoPoint(-41.2866, 174.7763), GeoPoint(-41.2867, 174.7764))
            )
        )

        val distance = split.distanceM(wellington)

        assertTrue("the near piece is metres away, not kilometres: $distance", distance != null && distance < 200)
    }

    @Test
    fun `within keeps the circle and not the box's corners`() {
        val page = listOf(near, far)

        assertEquals(listOf(near), page.within(radiusM = 500.0, from = wellington))
        assertEquals("no fix to measure from is everything", 2, page.within(500.0, from = null).size)
    }

    @Test
    fun `what was downloaded is read by name, case-blind`() {
        val page = listOf(walk, ride)

        assertEquals(2, page.matching("").size)
        assertEquals(listOf(walk), page.matching("bolton"))
        assertTrue(page.matching("nowhere").isEmpty())
    }

    @Test
    fun `a row with no walking grade answers nothing, so it cannot be offered as a chip`() {
        assertNull(plain.filterValues("Walk").firstOrNull())
        assertTrue(plain.filterValues("Walk").isEmpty())
    }

    @Test
    fun `the three facts a page can be narrowed by are the three a walker asks about`() {
        assertEquals(listOf("Walk", "DataSupplier", "DogAccess"), OTHER_TRACK_FILTERS.map { it.field })
    }

    private fun track(name: String, facts: Array<Pair<String, String>>, paths: List<List<GeoPoint>>) =
        OtherTrack(globalId = name, name = name, paths = paths, facts = facts.toMap())

    private val wellington = GeoPoint(lat = -41.2865, lng = 174.7762)

    /** About 100 m north-east of the fix, a short walk in the cemetery. */
    private val walk = track(
        name = "Bolton Street Cemetery",
        facts = arrayOf("Walk" to "2", "DataSupplier" to "Wellington City Council"),
        paths = listOf(listOf(GeoPoint(-41.2860, 174.7760), GeoPoint(-41.2864, 174.7763))
        )
    )

    /** The same place, a grade down, so a filter has two values to offer. */
    private val ride = track(
        name = "Crazy Horse",
        facts = arrayOf("Walk" to "1", "DataSupplier" to "Wellington City Council"),
        paths = listOf(listOf(GeoPoint(-41.2861, 174.7761), GeoPoint(-41.2862, 174.7762)))
    )

    /** A row the dataset named nothing and graded nothing. */
    private val plain = track(
        name = "Richmond",
        facts = arrayOf("DataSupplier" to "Tasman District Council"),
        paths = listOf(listOf(GeoPoint(-41.3400, 173.1900), GeoPoint(-41.3405, 173.1905)))
    )

    /** A few metres from the fix. */
    private val near = track(
        name = "Near",
        facts = arrayOf("Walk" to "3"),
        paths = listOf(listOf(GeoPoint(-41.2866, 174.7763), GeoPoint(-41.2867, 174.7764)))
    )

    /** Kilometres away. */
    private val far = track(
        name = "Far",
        facts = arrayOf("Walk" to "3"),
        paths = listOf(listOf(GeoPoint(-41.3100, 174.8000), GeoPoint(-41.3110, 174.8010)))
    )
}
