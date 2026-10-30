package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.look.LookColour
import nz.mckenzie.trailday.domain.look.LookTarget
import nz.mckenzie.trailday.domain.look.MapLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

/**
 * The look screen's preview: everything the walker can change, on one picture.
 *
 * Two things matter about it. Every row of the screen has to be visible in the sample, or the walker
 * changes a colour and sees nothing happen. And the sample has to stay around the ground it is drawn
 * on, because that ground is wherever the map was left - a sample that wandered off it would leave the
 * preview showing empty tiles instead of the thing being chosen.
 */
class LookSampleTest {

    /** Somewhere in the Tararuas, since the sample is laid out relative to wherever it is. */
    private val centre = GeoPoint(lat = -40.9, lng = 175.3)

    @Test
    fun `every row of the screen is drawn, and nothing else is`() {
        val sample = LookSample.build(centre, MapLook.DEFAULT)

        // The walker's own source carries the track, the bike track and the three places: two lines
        // and three markers.
        assertEquals("two lines", 2, count(sample.assets, "\"type\":\"LineString\""))
        assertEquals("three places", 3, count(sample.assets, "\"type\":\"Point\""))

        // One published line, which is the cased one, and one other publisher's line.
        assertEquals("a published line", 1, count(sample.doc, "\"type\":\"LineString\""))
        assertEquals("and another publisher's", 1, count(sample.otherTracks, "\"type\":\"LineString\""))
    }

    @Test
    fun `each thing is drawn in the colour its row was given`() {
        val look = MapLook.DEFAULT
            .withColour(LookTarget.TRACKS, LookColour.CRIMSON)
            .withColour(LookTarget.BIKE_TRACKS, LookColour.MAGENTA)
            .withColour(LookTarget.OTHER_TRACKS, LookColour.CHARCOAL)
            .withColour(LookTarget.HUTS, LookColour.BLUE)
            .withColour(LookTarget.CAMPSITES, LookColour.PURPLE)
            .withColour(LookTarget.WAYPOINTS, LookColour.ORANGE)

        val sample = LookSample.build(centre, look)

        assertTrue("the track", sample.assets.contains(LookColour.CRIMSON.hex))
        assertTrue("the bike track", sample.assets.contains(LookColour.MAGENTA.hex))
        assertTrue("the hut", sample.assets.contains(LookColour.BLUE.hex))
        assertTrue("the campsite", sample.assets.contains(LookColour.PURPLE.hex))
        assertTrue("the waypoint", sample.assets.contains(LookColour.ORANGE.hex))
        assertTrue("the published line is the track colour", sample.doc.contains(LookColour.CRIMSON.hex))
        assertTrue("the other publisher's line", sample.otherTracks.contains(LookColour.CHARCOAL.hex))
    }

    @Test
    fun `the three places ask for their own picture`() {
        val sample = LookSample.build(centre, MapLook.DEFAULT)

        listOf(AssetKind.HUT, AssetKind.CAMPSITE, AssetKind.WAYPOINT).forEach { kind ->
            assertTrue(
                "$kind has no marker to draw",
                sample.assets.contains("\"icon\":\"${PlaceIcons.imageName(kind)}\"")
            )
        }
    }

    @Test
    fun `everything lands around the ground it is drawn on`() {
        // A sample spread in degrees would come out a different size north to south, and a sample that
        // reached beyond the preview's zoom would be drawn over tiles the walker cannot see.
        listOf(GeoPoint(lat = -46.4, lng = 168.3), GeoPoint(lat = -35.0, lng = 173.2)).forEach { ground ->
            val sample = LookSample.build(ground, MapLook.DEFAULT)
            val documents = listOf(sample.assets, sample.doc, sample.otherTracks)

            documents.flatMap(::coordinates).forEach { (lng, lat) ->
                val north = (lat - ground.lat) * METRES_PER_DEGREE
                val east = (lng - ground.lng) * METRES_PER_DEGREE * cos(Math.toRadians(ground.lat))
                assertTrue("$north m north is outside the sample", abs(north) <= LookSample.HALF_HEIGHT_M)
                assertTrue("$east m east is outside the sample", abs(east) <= LookSample.HALF_WIDTH_M)
            }
        }
    }

    @Test
    fun `the sample fills the preview rather than sitting in one corner of it`() {
        // Six things in a heap would be one colour bobbing about; they are laid out across the box so a
        // width and a colour can each be read on their own.
        val sample = LookSample.build(centre, MapLook.DEFAULT)
        val points = listOf(sample.assets, sample.doc, sample.otherTracks).flatMap(::coordinates)

        val norths = points.map { (_, lat) -> (lat - centre.lat) * METRES_PER_DEGREE }
        val easts = points.map { (lng, _) ->
            (lng - centre.lng) * METRES_PER_DEGREE * cos(Math.toRadians(centre.lat))
        }

        val northSpan = norths.maxOrNull()!! - norths.minOrNull()!!
        val eastSpan = easts.maxOrNull()!! - easts.minOrNull()!!
        assertTrue("the sample is only $northSpan m tall", northSpan >= 500.0)
        assertTrue("the sample is only $eastSpan m wide", eastSpan >= 700.0)
    }

    private fun count(document: String, needle: String): Int =
        document.split(needle).size - 1

    /** Every `[lng, lat]` pair in a document, in the order they were written. */
    private fun coordinates(document: String): List<Pair<Double, Double>> =
        Regex("""\[(-?\d+\.\d+),(-?\d+\.\d+)]""").findAll(document).map { match ->
            match.groupValues[1].toDouble() to match.groupValues[2].toDouble()
        }.toList()

    private companion object {
        const val METRES_PER_DEGREE = 111_320.0
    }
}
