package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.look.Look
import nz.mckenzie.trailday.domain.look.LookTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line being followed, drawn from whatever kind of target it is.
 *
 * The recorder showed only the walker's own places, so a walk following one of DOC's tracks or a
 * council's had nothing on the map but the line it was recording. What the walker reads the two against
 * is this document, so what matters is that a published line arrives in it, in its own row's colour.
 */
class TargetGeoJsonTest {

    private val line = listOf(
        GeoPoint(-41.2865, 174.7762),
        GeoPoint(-41.2866, 174.7763),
        GeoPoint(-41.2867, 174.7764)
    )

    @Test
    fun `nothing followed is an empty collection rather than a failure`() {
        assertEquals("{\"type\":\"FeatureCollection\",\"features\":[]}", TargetGeoJson.empty())
        assertEquals(TargetGeoJson.empty(), TargetGeoJson.build(null, null, listOf(line)))
        assertEquals(
            "a line of one point is not a line",
            TargetGeoJson.empty(),
            TargetGeoJson.build("Kew Bush Walkway", LookTarget.OTHER_TRACKS, listOf(line.take(1)))
        )
    }

    @Test
    fun `a followed line arrives with its name and its own row's colour`() {
        val json = TargetGeoJson.build("Kew Bush Walkway", LookTarget.OTHER_TRACKS, listOf(line))

        assertTrue(json.contains("\"type\":\"LineString\""))
        assertTrue(json.contains("\"name\":\"Kew Bush Walkway\""))
        assertTrue(json.contains("\"stroke\":\"${Look.colourOf(LookTarget.OTHER_TRACKS).hex}\""))
        assertFalse("a followed line is nobody's place", json.contains("\"id\":1"))
    }

    @Test
    fun `a track published in pieces is drawn whole, so the piece underfoot reads against it`() {
        val spur = listOf(GeoPoint(-41.28, 174.77), GeoPoint(-41.281, 174.771))
        val json = TargetGeoJson.build("Port Access Road", LookTarget.TRACKS, listOf(line, spur))

        assertEquals(
            "both paths, plus the stretch of the track that carries its name",
            3,
            Regex("\"type\":\"LineString\"").findAll(json).count()
        )
    }

    @Test
    fun `a line of the walker's own keeps the kind it is drawn by`() {
        val colour = Look.colourOf(LookTarget.BIKE_TRACKS).hex
        val json = TargetGeoJson.build("Ride home", LookTarget.BIKE_TRACKS, listOf(line))

        assertTrue(json.contains("\"stroke\":\"$colour\""))
    }
}
