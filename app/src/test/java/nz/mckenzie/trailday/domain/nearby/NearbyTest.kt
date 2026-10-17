package nz.mckenzie.trailday.domain.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What is nearest, and what else is around.
 *
 * The card is a glance, so what is held still here is what a glance can be wrong about: which thing wins,
 * that a thing exactly on the radius is inside it, that the counts include the nearest one rather than
 * leaving it out of its own total, and that DOC's material and the walker's own are one list rather than
 * two.
 */
class NearbyTest {

    private fun thing(
        name: String,
        what: String,
        distanceM: Double,
        place: String? = null,
        assetId: Long? = null
    ) = NearbyThing(name = name, what = what, distanceM = distanceM, place = place, assetId = assetId)

    @Test
    fun `the nearest wins, whatever order the things arrive in`() {
        val nearby = Nearby.of(
            listOf(
                thing("Far hut", "hut", 4_000.0),
                thing("Angelus Hut", "hut", 2_400.0),
                thing("Glory Tk", "track", 3_000.0)
            )
        )!!

        assertEquals("Angelus Hut", nearby.nearest.name)
        assertEquals("hut", nearby.nearest.what)
        assertEquals("and the list is nearest first", listOf("Angelus Hut", "Glory Tk", "Far hut"), nearby.within.map { it.name })
    }

    @Test
    fun `a thing exactly on the radius is inside it`() {
        val nearby = Nearby.of(listOf(thing("Edge hut", "hut", Nearby.RADIUS_M)))

        assertEquals("five kilometres is a hut five kilometres away, not a hut that is not there", 1, nearby!!.within.size)
    }

    @Test
    fun `past the radius is not in the card at all`() {
        val nearby = Nearby.of(listOf(thing("Far hut", "hut", Nearby.RADIUS_M + 1.0)))

        assertNull("nothing within reach is nothing to show", nearby)
    }

    @Test
    fun `the counts include the nearest one, because it is in the same radius as the rest`() {
        // The card must never say "nearest hut" and then "0 huts within 5 km" about the same hut.
        val nearby = Nearby.of(
            listOf(
                thing("Angelus Hut", "hut", 2_400.0),
                thing("Hut two", "hut", 3_000.0),
                thing("Camp", "campsite", 4_000.0)
            )
        )!!

        assertEquals(listOf("2 huts", "1 campsite"), nearby.counted)
    }

    @Test
    fun `the commonest kind is named first, and ties keep the order they were met`() {
        val nearby = Nearby.of(
            listOf(
                thing("A", "track", 1_000.0),
                thing("B", "hut", 1_100.0),
                thing("C", "hut", 1_200.0),
                thing("D", "track", 1_300.0),
                thing("E", "track", 1_400.0)
            )
        )!!

        assertEquals(listOf("3 tracks", "2 huts"), nearby.counted)
    }

    @Test
    fun `DOC's material and the walker's own are one list, and each keeps what it needs to be opened`() {
        val nearby = Nearby.of(
            listOf(
                thing("Angelus Hut", "hut", 900.0, place = "Nelson Lakes"),
                thing("My camp", "campsite", 700.0, assetId = 42L)
            )
        )!!

        assertEquals("the walker's own place is nearest and can be opened", 42L, nearby.nearest.assetId)
        val docHut = nearby.within.first { it.name == "Angelus Hut" }
        assertNull("a DOC thing has no page to open", docHut.assetId)
        assertEquals("and keeps the place DOC puts it in", "Nelson Lakes", docHut.place)
    }

    @Test
    fun `how long on foot is the one rule for time, over the distance the card knows`() {
        val nearby = Nearby.of(listOf(thing("Angelus Hut", "hut", 2_400.0)))!!

        // 2.4 km on the flat at 5 km/h is 28.8 minutes, which rounds up to 30.
        assertEquals(30 * 60L, nearby.nearest.onFootSeconds())
        assertTrue(nearby.nearest.onFootSeconds() > 0)
    }
}
