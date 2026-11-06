package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dot a finger on a height chart puts on the map.
 *
 * Two promises are held here, and both of them are about the map being pointed at rather than
 * drawn on: exactly one place, at the coordinates the map expects, and nothing at all once the
 * finger has lifted - an empty collection, so the layer has no feature to draw.
 */
class FingerGeoJsonTest {

    @Test
    fun `no finger is no dot`() {
        assertEquals(FingerGeoJson.EMPTY, FingerGeoJson.build(null))
        assertFalse(FingerGeoJson.build(null).contains("Point"))
    }

    @Test
    fun `one finger is one point, longitude first`() {
        val json = FingerGeoJson.build(GeoPoint(lat = -41.5, lng = 173.95))

        assertEquals("one feature, not a collection of guesses", 1, json.split("\"type\":\"Feature\"").size - 1)
        assertTrue("GeoJSON says longitude first: $json", json.contains("[173.9500000,-41.5000000]"))
    }
}
