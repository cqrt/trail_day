package nz.mckenzie.trailday.domain.doc

import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pairing DOC's bike points with the tracks they sit on.
 *
 * The point is metadata about a track, not a second place: a pair is what lets the map draw one line
 * with a bike badge and say what DOC says about riding it. What is pinned here is the conservative
 * part - the name narrows the candidates, and the point has to be near the line.
 */
class DocBikeLinksTest {

    private fun track(name: String, points: List<GeoPoint>, id: Long = 1) =
        DocItem(source = DocSource.TRACKS, objectId = id, name = name, paths = listOf(points))

    private fun bike(name: String, point: GeoPoint, id: Long = 2) =
        DocItem(source = DocSource.BIKES, objectId = id, name = name, paths = listOf(listOf(point)))

    private fun at(lat: Double, lng: Double) = GeoPoint(lat = lat, lng = lng)

    private val line = listOf(at(-41.0000, 173.0000), at(-41.0000, 173.0100))

    @Test
    fun `a bike point on a track of the same name belongs to it`() {
        val track = track("Globe Hill Track", line)
        val point = bike("Globe Hill Track", at(-41.0000, 173.0050))

        val links = DocBikeLinks.of(listOf(track), listOf(point))

        assertEquals(1, links.pairs.size)
        assertEquals(track.sourceRef, links.pairs.single().track.sourceRef)
        assertEquals(point.sourceRef, links.bikeFor(track)?.sourceRef)
        assertEquals(setOf(point.sourceRef), links.linkedBikeRefs)
        assertEquals(setOf(track.sourceRef), links.linkedTrackRefs)
    }

    @Test
    fun `case, spacing, punctuation and a non-breaking space are the same name`() {
        val track = track("Melina Ridge Track", line)
        val point = bike("  MELINA-RIDGE\u00A0track ", at(-41.0000, 173.0040))

        assertEquals(1, DocBikeLinks.of(listOf(track), listOf(point)).pairs.size)
        assertEquals("melina ridge track", DocBikeLinks.nameKey("  MELINA-RIDGE\u00A0track "))
        assertEquals("pūponga hilltop walk", DocBikeLinks.nameKey(" Pūponga Hilltop Walk\u00A0"))
    }

    @Test
    fun `a point well off the line is not a link even when the name matches`() {
        // The private check that matters: DOC has two different tracks with the same name in places
        // kilometres apart. A name alone would put the bike badge on the wrong one.
        val track = track("Saddle Ridge Track", line)
        val far = bike("Saddle Ridge Track", at(-41.0160, 173.0050))

        assertTrue(DocBikeLinks.of(listOf(track), listOf(far)).isEmpty)
        assertNull(DocBikeLinks.of(listOf(track), listOf(far)).bikeFor(track))
    }

    @Test
    fun `the nearest track wins when DOC repeats a name`() {
        val near = track("Homestead Track", line, id = 10)
        val far = track(
            "Homestead Track",
            listOf(at(-41.0200, 173.0000), at(-41.0200, 173.0100)),
            id = 11
        )
        val point = bike("Homestead Track", at(-41.0000, 173.0030))

        val links = DocBikeLinks.of(listOf(far, near), listOf(point))

        assertEquals(1, links.pairs.size)
        assertEquals(near.sourceRef, links.pairs.single().track.sourceRef)
    }

    @Test
    fun `a point whose track is not held stays a place of its own`() {
        val point = bike("Union Creek Track", at(-42.0000, 171.0000))

        assertTrue(DocBikeLinks.of(emptyList(), listOf(point)).isEmpty)
    }

    @Test
    fun `a hundred metres is the edge and is included`() {
        // About 55 metres north of the line, then about 110 metres: the first links, the second does not.
        val track = track("Rameka Track", line)
        val near = bike("Rameka Track", at(-40.9995, 173.0050), id = 20)
        val far = bike("Rameka Track", at(-40.9990, 173.0050), id = 21)

        val nearLinks = DocBikeLinks.of(listOf(track), listOf(near))
        val farLinks = DocBikeLinks.of(listOf(track), listOf(far))

        assertEquals(1, nearLinks.pairs.size)
        assertTrue(nearLinks.pairs.single().distanceM < 60.0)
        assertTrue(farLinks.isEmpty)
    }

    @Test
    fun `only DOC's own tracks and bike points take part`() {
        val walkerTrack = track("Globe Hill Track", line).copy(source = DocSource.HUTS)
        val point = bike("Globe Hill Track", at(-41.0000, 173.0050))

        assertTrue(DocBikeLinks.of(listOf(walkerTrack), listOf(point)).isEmpty)
    }
}
