package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two marks an activity's page puts on its own map.
 *
 * Four promises are held here: there are exactly two of them, the first fix is the start and the last
 * is the finish, the coordinates are the ones a map expects, and a trip with nothing to say between
 * two ends says nothing at all.
 */
class EndsGeoJsonTest {

    private val walk = listOf(
        GeoPoint(lat = -41.5000, lng = 173.9500),
        GeoPoint(lat = -41.5010, lng = 173.9600),
        GeoPoint(lat = -41.5020, lng = 173.9700)
    )

    @Test
    fun theFirstFixIsTheStartAndTheLastIsTheFinish() {
        val json = EndsGeoJson.build(walk)

        assertEquals("one feature per end, not one per fix", 2, json.featureCount())
        assertTrue(
            "the start is the first fix, and it comes first: $json",
            json.indexOf("\"part\":\"start\"") < json.indexOf("\"part\":\"finish\"")
        )
        assertTrue("the start is the first fix", json.contains("[173.9500000,-41.5000000]"))
        assertTrue("and the finish is the last", json.contains("[173.9700000,-41.5020000]"))
        assertFalse(
            "the fix in the middle is not an end",
            json.contains("[173.9600000,-41.5010000]")
        )
    }

    @Test
    fun theCoordinatesAreLongitudeFirstToSevenDecimals() {
        val json = EndsGeoJson.build(
            listOf(
                GeoPoint(lat = -41.5, lng = 173.95),
                GeoPoint(lat = -41.6, lng = 173.96)
            )
        )

        // GeoJSON is [longitude, latitude], and seven decimals is about a centimetre: the app writes
        // every point in the same shape, so a document cannot be the one that rounds differently.
        assertTrue("longitude first, seven decimals: $json", json.contains("[173.9500000,-41.5000000]"))
    }

    @Test
    fun noPointsAndOnePointHaveNoEnds() {
        assertEquals(EndsGeoJson.EMPTY, EndsGeoJson.build(emptyList()))
        assertEquals(
            "one fix is one end, which is no two ends to tell apart",
            EndsGeoJson.EMPTY,
            EndsGeoJson.build(listOf(GeoPoint(-41.5, 173.95)))
        )
    }

    @Test
    fun aLoopIsTwoEndsAtOnePlace() {
        val loop = listOf(
            GeoPoint(lat = -41.5000, lng = 173.9500),
            GeoPoint(lat = -41.5010, lng = 173.9600),
            GeoPoint(lat = -41.5000, lng = 173.9500)
        )

        val json = EndsGeoJson.build(loop)

        assertEquals("both ends are still drawn, so the flag stands on the dot", 2, json.featureCount())
        assertEquals(
            "and they are at the same coordinate",
            2,
            json.split("[173.9500000,-41.5000000]").size - 1
        )
    }

    private fun String.featureCount(): Int = split("\"type\":\"Feature\"").size - 1
}
