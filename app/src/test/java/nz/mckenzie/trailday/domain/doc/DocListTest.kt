package nz.mckenzie.trailday.domain.doc

import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Narrowing and ordering what a DOC search found.
 *
 * Three rules worth holding still. "Nearest" means nearest *vertex* - a long line passing beside the
 * phone is near the phone, where its middle would not be - and a nearest sort with nothing to be near to
 * is no order at all, so it falls back to by name rather than inventing one. And **the facts a filter
 * offers are the ones the rows actually carry**: a hut has no difficulty and a track has nobody to book
 * with, so a value a row does not have cannot be offered as a chip.
 */
class DocListTest {

    @Test
    fun `a hut DOC publishes twice is one hut`() {
        // The live service does exactly this: the same hut, the same place, the same point to five decimal
        // places, under two numbers a few hundred apart. 500 of its 1,429 huts are one of these.
        val huts = listOf(
            hutAt(621583, "Crow Hut", "Ruahine Forest Park", "Manawatu/Whanganui", -39.76697, 176.08515),
            hutAt(622512, "Crow Hut", "Ruahine Forest Park", "Manawatu/Whanganui", -39.76697, 176.08515)
        )

        val kept = huts.withoutRepeats()

        assertEquals("one hut", 1, kept.size)
        assertEquals("and it is the lower DOC number", 621583L, kept.single().objectId)
    }

    @Test
    fun `huts that share a name but not a place are all kept`() {
        // DOC really does publish a Crow Hut in the Ruahine, another in Arthur's Pass and a third in
        // Kahurangi. A rule that merged on the name alone would take two of them away.
        val huts = listOf(
            hutAt(1, "Crow Hut", "Ruahine Forest Park", "Manawatu/Whanganui", -39.76697, 176.08515),
            hutAt(2, "Crow Hut", "Arthur's Pass National Park", "Canterbury", -42.94343, 171.51701),
            hutAt(3, "Crow Hut", "Kahurangi National Park", "Nelson/Tasman", -41.29258, 172.46863)
        )

        assertEquals("three huts", 3, huts.withoutRepeats().size)
    }

    @Test
    fun `huts with one name and one place but two points are both kept`() {
        val huts = listOf(
            hutAt(1, "Boundary Hut", "Some Forest Park", "Canterbury", -42.0, 172.0),
            hutAt(2, "Boundary Hut", "Some Forest Park", "Canterbury", -42.05, 172.05)
        )

        assertEquals("five kilometres apart is not the same hut", 2, huts.withoutRepeats().size)
    }

    @Test
    fun `nothing is dropped when nothing is repeated`() {
        val huts = listOf(hutAt(1, "One Hut", "A Park", "A Region", -42.0, 172.0))

        assertEquals(huts, huts.withoutRepeats())
    }

    /** A hut at a chosen point, because the duplicate rule is about where a hut is. */
    private fun hutAt(
        id: Long,
        name: String,
        place: String,
        region: String,
        lat: Double,
        lng: Double
    ) = DocItem(
        source = DocSource.HUTS,
        objectId = id,
        name = name,
        paths = listOf(listOf(GeoPoint(lat, lng))),
        facts = mapOf("place" to place, "region" to region)
    )

    private fun track(id: Long, name: String, kind: String?, at: GeoPoint, type: String? = null) = DocItem(
        source = DocSource.TRACKS,
        objectId = id,
        name = name,
        paths = listOf(
            listOf(at, GeoPoint(at.lat + 0.01, at.lng + 0.01)),
            listOf(at, GeoPoint(at.lat - 0.01, at.lng))
        ),
        facts = buildMap {
            kind?.let { put("SubObjectType", it) }
            // The type is one of the tracks service's pivoted characteristics, so it is written the way
            // the wire writes it: inside a name-and-value slot rather than as a field of its own.
            if (type != null) {
                put("CharName1", DocSource.TRACK_TYPE_CHARACTERISTIC)
                put("CharValue1", type)
            }
        }
    )

    private fun hut(id: Long, name: String, bookable: String?, region: String?) = DocItem(
        source = DocSource.HUTS,
        objectId = id,
        name = name,
        paths = listOf(listOf(GeoPoint(-41.62, 172.65))),
        facts = buildMap {
            bookable?.let { put("bookable", it) }
            region?.let { put("region", it) }
        }
    )

    private val invercargill = GeoPoint(-46.4132, 168.3538)

    @Test
    fun `a filter offers the values the rows carry, each once, sorted, blanks left out`() {
        val tracks = listOf(
            track(1, "A", "Tramping Track", invercargill),
            track(2, "B", "Short Walk", invercargill),
            track(3, "C", "Tramping Track", invercargill),
            track(4, "D", null, invercargill),
            track(5, "E", "  ", invercargill)
        )

        assertEquals(listOf("Short Walk", "Tramping Track"), tracks.valuesPresent("SubObjectType"))
    }

    @Test
    fun `a track's type is offered from the slot DOC keeps it in`() {
        val tracks = listOf(
            track(1, "A", "Walking Track", invercargill, type = "One way"),
            track(2, "B", "Walking Track", invercargill, type = "Loop"),
            track(3, "C", "Walking Track", invercargill, type = "  ")
        )

        assertEquals(
            listOf("Loop", "One way"),
            tracks.valuesPresent(DocSource.TRACK_TYPE_CHARACTERISTIC)
        )
    }

    @Test
    fun `a hut is narrowed by the facts a hut has, and not by the ones it does not`() {
        val huts = listOf(
            hut(1, "Angelus Hut", "Yes", "Nelson/Marlborough"),
            hut(2, "Liverpool Hut", "No", "Nelson/Marlborough"),
            hut(3, "Cupola Hut", null, null)
        )

        assertEquals(listOf("No", "Yes"), huts.valuesPresent("bookable"))
        assertEquals(listOf("Nelson/Marlborough"), huts.valuesPresent("region"))
        // The one that matters: a hut has no difficulty, so a difficulty filter would be a chip that
        // matches nothing - which is exactly what the per-source vocabulary exists to prevent.
        assertTrue(huts.valuesPresent("difficulty").isEmpty())
        assertEquals(
            listOf(huts[0]),
            huts.showing(mapOf("bookable" to setOf("Yes")), DocSort.NAME, from = null)
        )
    }

    @Test
    fun `a kind and a type together narrow to the tracks that are both`() {
        val oneWayWalk = track(1, "One way walk", "Walking Track", invercargill, type = "One way")
        val loopWalk = track(2, "Loop walk", "Walking Track", invercargill, type = "Loop")
        val loopTramp = track(3, "Loop tramp", "Tramping Track", invercargill, type = "Loop")
        val tracks = listOf(oneWayWalk, loopWalk, loopTramp)

        assertEquals(
            "the type alone keeps every kind of it (ordered by name, as asked)",
            listOf(loopTramp, loopWalk),
            tracks.showing(
                filters = mapOf(DocSource.TRACK_TYPE_CHARACTERISTIC to setOf("Loop")),
                sort = DocSort.NAME,
                from = invercargill
            )
        )
        assertEquals(
            "and both together keep only what is both",
            listOf(loopTramp),
            tracks.showing(
                filters = mapOf(
                    "SubObjectType" to setOf("Tramping Track"),
                    DocSource.TRACK_TYPE_CHARACTERISTIC to setOf("Loop")
                ),
                sort = DocSort.NAME,
                from = invercargill
            )
        )
    }

    @Test
    fun `nearest is measured to the closest vertex, not the middle of the line`() {
        // The second path starts exactly at the fix, so the track is nought metres away even though its
        // other end is a kilometre off.
        val track = track(1, "A", "Walking Track", invercargill)

        assertEquals(0.0, track.distanceM(invercargill)!!, 0.5)
    }

    @Test
    fun `nearest orders by the distance to the phone`() {
        val near = track(1, "Near", "Walking Track", GeoPoint(-46.4130, 168.3538))
        val far = track(2, "Far", "Walking Track", GeoPoint(-46.30, 168.20))

        val shown = listOf(far, near).showing(emptyMap(), DocSort.NEAREST, from = invercargill)

        assertEquals(listOf(near, far), shown)
    }

    @Test
    fun `a nearest sort with no fix falls back to by name`() {
        val b = track(1, "B", "Walking Track", invercargill)
        val a = track(2, "A", "Walking Track", invercargill)

        val shown = listOf(b, a).showing(emptyMap(), DocSort.NEAREST, from = null)

        assertEquals(listOf(a, b), shown)
    }

    @Test
    fun `picking a value keeps only the rows that carry it`() {
        val walk = track(1, "Walk", "Walking Track", invercargill)
        val tramp = track(2, "Tramp", "Tramping Track", invercargill)

        val shown = listOf(walk, tramp)
            .showing(mapOf("SubObjectType" to setOf("Tramping Track")), DocSort.NAME, invercargill)

        assertEquals(listOf(tramp), shown)
    }

    @Test
    fun `a filter with nothing ticked is no filter, not an empty list`() {
        val walk = track(1, "Walk", "Walking Track", invercargill)
        val tramp = track(2, "Tramp", "Tramping Track", invercargill)
        // By name, which is the order a filter asks for unless the walker says nearest.
        val both = listOf(tramp, walk)

        assertEquals(both, listOf(walk, tramp).showing(emptyMap(), DocSort.NAME, invercargill))
        assertEquals(
            "and a field with nothing chosen is the same as no filter at all",
            both,
            listOf(walk, tramp).showing(mapOf("SubObjectType" to emptySet()), DocSort.NAME, invercargill)
        )
    }

    @Test
    fun `within keeps only what is truly in the radius, unlike the service's box`() {
        // The service's box would have handed both back; the circle keeps only the one inside it.
        val near = track(1, "Near", "Walking Track", GeoPoint(-46.4130, 168.3538))
        val far = track(2, "Far", "Walking Track", GeoPoint(-46.30, 168.20))

        val shown = listOf(near, far).within(radiusM = 5_000.0, from = invercargill)

        assertEquals(listOf(near), shown)
    }

    @Test
    fun `within with no fix keeps everything, because there is no centre to measure from`() {
        val tracks = listOf(track(1, "A", "Walking Track", GeoPoint(-46.30, 168.20)))

        assertEquals(tracks, tracks.within(5_000.0, from = null))
    }

    @Test
    fun `bounds is the box every vertex fits in, and nothing when there are no tracks`() {
        val a = track(1, "A", "Walking Track", GeoPoint(-46.50, 168.10))
        val b = track(2, "B", "Walking Track", GeoPoint(-46.40, 168.20))

        val bounds = listOf(a, b).bounds()!!
        // The track helper adds a diagonal 0.01 either way, so the extent is the corners of both.
        assertEquals(-46.51, bounds.minLat, 1e-9)
        assertEquals(168.10, bounds.minLng, 1e-9)
        assertEquals(-46.39, bounds.maxLat, 1e-9)
        assertEquals(168.21, bounds.maxLng, 1e-9)

        assertEquals(null, emptyList<DocItem>().bounds())
    }
}
