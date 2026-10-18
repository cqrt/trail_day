package nz.mckenzie.trailday.domain.doc

import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What DOC publishes under a tap on the map.
 *
 * What is held still here is what a tap can be wrong about: that a finger halfway along a long straight piece
 * of a track picks that track rather than nothing, that the nearest of two things at a junction wins, that a
 * place is found by its own dot, and that a tap on empty ground says nothing rather than the closest thing
 * anywhere.
 */
class DocHitTestTest {

    /** A track running north from [lat] for roughly [lengthM] metres, one degree of latitude being 111 km. */
    private fun track(name: String, lat: Double, lng: Double, lengthM: Double = 1_000.0) =
        DocItem(
            source = DocSource.TRACKS,
            objectId = name.hashCode().toLong(),
            name = name,
            paths = listOf(
                listOf(
                    GeoPoint(lat = lat, lng = lng),
                    GeoPoint(lat = lat + lengthM / 111_000.0, lng = lng)
                )
            ),
            facts = mapOf("SubObjectType" to "Walking Track")
        )

    private fun hut(name: String, lat: Double, lng: Double) =
        DocItem(
            source = DocSource.HUTS,
            objectId = name.hashCode().toLong(),
            name = name,
            paths = listOf(listOf(GeoPoint(lat = lat, lng = lng))),
            facts = mapOf("place" to "Nelson Lakes")
        )

    @Test
    fun `a finger halfway along a long straight piece of a track picks that track`() {
        // Halfway up the line, and 10 m to the side of it - well inside a walking-scale tap, and nowhere near
        // either end of it. Measured to the nearest vertex this would be half a kilometre away.
        val picked = DocHitTest.nearest(
            items = listOf(track("Routeburn Track", lat = -44.9000, lng = 168.1000, lengthM = 1_000.0)),
            lat = -44.9000 + 500.0 / 111_000.0,
            lng = 168.1000 + 10.0 / 84_000.0,
            radiusM = 40.0
        )

        assertEquals("Routeburn Track", picked?.name)
    }

    @Test
    fun `empty ground says nothing rather than the closest thing anywhere`() {
        assertNull(
            DocHitTest.nearest(
                items = listOf(track("Routeburn Track", lat = -44.9, lng = 168.1)),
                lat = -44.9,
                lng = 168.2,
                radiusM = 40.0
            )
        )
    }

    @Test
    fun `the nearest of two tracks at a junction wins`() {
        // One track through the tap, another 20 m to the side of it.
        val through = track("Under the finger", lat = -41.6000, lng = 172.7000)
        val beside = track("Twenty metres off", lat = -41.6000, lng = 172.7000 + 20.0 / 84_000.0)

        val picked = DocHitTest.nearest(
            items = listOf(beside, through),
            lat = -41.6000,
            lng = 172.7000,
            radiusM = 40.0
        )

        assertTrue("the one the finger is on wins", picked === through)
    }

    @Test
    fun `a hut standing on a track answers for the hut`() {
        // The hut sits exactly where the track reaches it, so the line is as near as the place: the place is
        // what the walker's finger is on, and it is what the card should say.
        val track = track("Speargrass-Angelus Route", lat = -41.8882, lng = 172.7487, lengthM = 2_000.0)
        val hut = hut("Angelus Hut", lat = -41.8882, lng = 172.7487)

        val picked = DocHitTest.nearest(listOf(track, hut), -41.8882, 172.7487, radiusM = 40.0)

        assertEquals("Angelus Hut", picked?.name)
    }

    @Test
    fun `a place is found by its own dot, and its distance is the distance to it`() {
        val item = hut("Angelus Hut", lat = -41.6200, lng = 172.6500)
        val at = GeoPoint(lat = -41.6200, lng = 172.6500 + 9.0 / 84_000.0)

        assertEquals("Angelus Hut", DocHitTest.nearest(listOf(item), at.lat, at.lng, radiusM = 20.0)?.name)
        val distance = DocHitTest.distanceTo(item, at)!!
        assertEquals("about nine metres", 9.0, distance, 0.5)
    }

    @Test
    fun `a thing with no geometry to measure is left out rather than counted at zero`() {
        val nowhere = DocItem(
            source = DocSource.TRACKS,
            objectId = 7,
            name = "No line",
            paths = listOf(emptyList())
        )

        assertNull(DocHitTest.nearest(listOf(nowhere), -41.6, 172.7, radiusM = 40.0))
        assertNull("and it has no distance at all", DocHitTest.distanceTo(nowhere, GeoPoint(-41.6, 172.7)))
    }
}
