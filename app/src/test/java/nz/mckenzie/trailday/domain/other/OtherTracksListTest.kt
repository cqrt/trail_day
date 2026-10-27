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
    fun `a walkway the dataset cut into pieces is one track`() {
        // Measured against the live service: "Otatara Scenic Reserve Walkway" is four rows around
        // Invercargill whose ends coincide exactly.
        val joined = listOf(otataraA, otataraB, otataraC).joinedByName()

        assertEquals("three pieces are one walkway", 1, joined.size)
        assertEquals("and it keeps every piece of the line", 3, joined.single().paths.size)
    }

    @Test
    fun `the same name a country away is a different walkway`() {
        // There is an Otatara in Invercargill and another in Napier, and a name alone would merge them.
        val joined = listOf(otataraA, otataraB, otataraNapier).joinedByName()

        assertEquals(2, joined.size)
    }

    @Test
    fun `a walkway interrupted by a road is still one walkway`() {
        // Measured: "Kingswell Creek Walkway" is nine rows along one creek in Invercargill, and the gaps
        // along it are 12, 21, 22 and 126 metres rather than nothing - a walkway stops at a road, a bridge
        // and a piece of private land. Ten metres left it in four pieces, which is what a walker reported.
        val joined = listOf(kingswellEast, kingswellMiddle, kingswellWest).joinedByName()

        assertEquals("three separated sections are one walkway", 1, joined.size)
        assertEquals(3, joined.single().paths.size)
    }

    @Test
    fun `two rows whose ends are a gap apart are not joined`() {
        // Five hundred metres: beyond any separation measured inside one walkway (the worst was 126 m), so
        // a same-named track that far away is a different walk.
        val far = track(
            name = "Otatara Scenic Reserve Walkway",
            facts = arrayOf("Walk" to "3"),
            paths = listOf(listOf(GeoPoint(-46.42866, 168.30600), GeoPoint(-46.42900, 168.30640))),
            id = "ff"
        )

        assertEquals(2, listOf(otataraA, far).joinedByName().size)
    }

    @Test
    fun `two tracks nobody named are not joined, however close they are`() {
        // A fifth of the dataset has a placeholder in its name field and is renamed after its locality, so
        // twelve unnamed tracks in Mount Victoria all read "Mount Victoria". Joining on that would draw one
        // enormous fake track across a suburb; the publisher's own name is what may join two rows.
        val first = track(
            name = "Mount Victoria",
            facts = arrayOf("Walk" to "3"),
            paths = listOf(listOf(GeoPoint(-41.2900, 174.7900), GeoPoint(-41.2905, 174.7905))),
            id = "u1",
            published = "Unnamed Track"
        )
        val second = track(
            name = "Mount Victoria",
            facts = arrayOf("Walk" to "3"),
            paths = listOf(listOf(GeoPoint(-41.2905, 174.7905), GeoPoint(-41.2910, 174.7910))),
            id = "u2",
            published = "Unnamed Track"
        )

        assertEquals(2, listOf(first, second).joinedByName().size)
    }

    @Test
    fun `a joined walkway is as long as all of it`() {
        val whole = listOf(otataraA, otataraB).joinedByName().single()

        assertEquals(
            "the length is the pieces together, not the first piece",
            otataraA.lengthM + otataraB.lengthM,
            whole.lengthM,
            0.001
        )
    }

    @Test
    fun `a joined walkway is found by every publisher of it`() {
        val whole = listOf(
            otataraA.copy(facts = otataraA.facts + ("DataSupplier" to "Invercargill City Council")),
            otataraB.copy(facts = otataraB.facts + ("DataSupplier" to "Southland District Council"))
        ).joinedByName().single()

        assertEquals(
            listOf("Invercargill City Council", "Southland District Council"),
            whole.suppliers
        )
        assertEquals(
            listOf(whole),
            listOf(whole).showing(mapOf("DataSupplier" to setOf("Southland District Council")))
        )
    }

    @Test
    fun `a joined walkway keeps the lowest id, so which row stands for it does not depend on the order`() {
        val forwards = listOf(otataraA, otataraB).joinedByName().single()
        val backwards = listOf(otataraB, otataraA).joinedByName().single()

        assertEquals(forwards.globalId, backwards.globalId)
    }

    @Test
    fun `a track that is not one of a joined pair is left alone`() {
        assertEquals(1, listOf(plain).joinedByName().size)
        assertEquals(plain, listOf(plain).joinedByName().single())
    }

    @Test
    fun `the three facts a page can be narrowed by are the three a walker asks about`() {
        assertEquals(listOf("Walk", "DataSupplier", "DogAccess"), OTHER_TRACK_FILTERS.map { it.field })
    }

    /** Three pieces of one Invercargill walkway, each starting where the last ended. */
    private val otataraA = track(
        name = "Otatara Scenic Reserve Walkway",
        facts = arrayOf("Walk" to "3"),
        paths = listOf(listOf(GeoPoint(-46.42866, 168.29894), GeoPoint(-46.42892, 168.29953))),
        id = "aa"
    )

    private val otataraB = track(
        name = "otatara scenic reserve walkway",
        facts = arrayOf("Walk" to "3"),
        paths = listOf(listOf(GeoPoint(-46.42892, 168.29953), GeoPoint(-46.43143, 168.29589))),
        id = "bb"
    )

    private val otataraC = track(
        name = "Otatara Scenic Reserve Walkway",
        facts = arrayOf("Walk" to "3"),
        paths = listOf(listOf(GeoPoint(-46.43143, 168.29589), GeoPoint(-46.42866, 168.29894))),
        id = "cc"
    )

    /** The same name, in Napier. */
    private val otataraNapier = track(
        name = "Otatara Scenic Reserve Walkway",
        facts = arrayOf("Walk" to "3"),
        paths = listOf(listOf(GeoPoint(-39.55034, 176.83383), GeoPoint(-39.55040, 176.83390))),
        id = "zz"
    )

    /**
     * Three of the nine real sections of "Kingswell Creek Walkway", with the service's own coordinates.
     *
     * The gaps between them are 21 m and 126 m - measured, not invented - which is what a walkway
     * interrupted by roads looks like.
     */
    private val kingswellEast = track(
        name = "Kingswell Creek Walkway",
        facts = arrayOf("Walk" to "3"),
        paths = listOf(listOf(GeoPoint(-46.43923, 168.37170), GeoPoint(-46.43922, 168.38069))),
        id = "k1"
    )

    private val kingswellMiddle = track(
        name = "Kingswell Creek Walkway",
        facts = arrayOf("Walk" to "3"),
        paths = listOf(listOf(GeoPoint(-46.43951, 168.36225), GeoPoint(-46.43916, 168.37143))),
        id = "k2"
    )

    private val kingswellWest = track(
        name = "Kingswell Creek Walkway",
        facts = arrayOf("Walk" to "3"),
        paths = listOf(listOf(GeoPoint(-46.44047, 168.36135), GeoPoint(-46.44073, 168.36101))),
        id = "k3"
    )

    private fun track(
        name: String,
        facts: Array<Pair<String, String>>,
        paths: List<List<GeoPoint>>,
        id: String = name,
        /** What the publisher put in the name field, which is what a join is allowed to look at. */
        published: String = name
    ) = OtherTrack(
        globalId = id,
        name = name,
        paths = paths,
        facts = (facts + arrayOf(OtherTracksUrl.NAME_FIELD to published)).toMap()
    )

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
