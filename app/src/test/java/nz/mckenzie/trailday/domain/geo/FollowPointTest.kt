package nz.mckenzie.trailday.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Heading for a point: which way, how far, and how long.
 *
 * The three bearings worth pinning are the ones anybody can check on a map - due north, due east and due
 * south - and the one that catches a swapped sine: a point to the **south** must read 180 and south, not
 * north. The rest is the compass words, which are the app's own choice of eight.
 */
class FollowPointTest {

    private val here = GeoPoint(lat = -46.6000, lng = 168.3000)

    @Test
    fun `due north is zero degrees and the word north`() {
        val north = FollowPoint.of(here, GeoPoint(lat = -46.5000, lng = 168.3000))!!

        assertEquals(0.0, north.bearingDegrees, 0.5)
        assertEquals("north", north.compass)
        assertEquals("a tenth of a degree of latitude, near enough 11.1 km", 11_119.5, north.distanceM, 5.0)
    }

    @Test
    fun `due east is ninety and due south is one eighty`() {
        assertEquals(90.0, FollowPoint.of(here, GeoPoint(lat = -46.6000, lng = 168.4000))!!.bearingDegrees, 1.0)
        assertEquals(
            "south",
            FollowPoint.of(here, GeoPoint(lat = -46.7000, lng = 168.3000))!!.compass
        )
        assertEquals(180.0, FollowPoint.of(here, GeoPoint(lat = -46.7000, lng = 168.3000))!!.bearingDegrees, 1.0)
    }

    @Test
    fun `the compass has eight points, and the diagonals are between them`() {
        assertEquals("north-east", FollowPoint.of(here, GeoPoint(lat = -46.5000, lng = 168.4000))!!.compass)
        assertEquals("south-west", FollowPoint.of(here, GeoPoint(lat = -46.7000, lng = 168.2000))!!.compass)
        assertEquals("west", FollowPoint.of(here, GeoPoint(lat = -46.6000, lng = 168.2000))!!.compass)
    }

    @Test
    fun `standing on the spot is no heading rather than zero metres north`() {
        assertNull(FollowPoint.of(here, here))
        assertNull(FollowPoint.of(here, GeoPoint(lat = -46.6000001, lng = 168.3000001)))
    }

    @Test
    fun `the time to a point is the one rule for time, with no climb known`() {
        val kilometre = FollowPoint.of(here, GeoPoint(lat = -46.5910, lng = 168.3000))!!

        // A kilometre on the flat at 5 km/h is 12.01 minutes, which rounds up to 13.
        assertEquals(13 * 60L, kilometre.toGoSeconds())
        assertEquals("and riding is quicker", 6 * 60L, kilometre.toGoSeconds(TravelMode.RIDING))
    }
}
