package nz.mckenzie.trailday.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app says when it cannot have the location it needs.
 *
 * Two refusals, and they ask for two different trips into Settings: one turns location on, the other
 * changes a permission that already exists from Approximate to Precise. Saying the wrong one sends a
 * walker to a switch that is already on.
 */
class LocationPermissionTest {

    @Test
    fun `choosing approximate asks for precise, and says where to change it`() {
        val words = LocationPermission.refusal(approximateOnly = true)

        assertEquals(LocationPermission.APPROXIMATE, words)
        assertTrue("it must name what to choose", words.contains("Precise"))
        assertTrue("and where", words.contains("settings"))
    }

    @Test
    fun `a plain refusal asks for location, and says where`() {
        val words = LocationPermission.refusal(approximateOnly = false)

        assertEquals(LocationPermission.DENIED, words)
        assertTrue("and where", words.contains("settings"))
        assertTrue(
            "a refusal is not told to look for a Precise switch that isn't there",
            !words.contains("Precise")
        )
    }

    @Test
    fun `the two refusals are not the same sentence`() {
        assertNotEquals(LocationPermission.APPROXIMATE, LocationPermission.DENIED)
    }
}
