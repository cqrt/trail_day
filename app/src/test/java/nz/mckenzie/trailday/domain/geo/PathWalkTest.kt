package nz.mckenzie.trailday.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the walker is along one line, and the piece of it that is left.
 *
 * The arithmetic here is what the recorder measures a wondered-onto track with, and what the readout says
 * once it has - so the two things that matter are **where along** a long leg the walker is, and that a
 * line joined in the middle **starts where they joined it**.
 */
class PathWalkTest {

    /** A line running east at [lat], [lengthM] metres long, in [steps] equal legs. */
    private fun eastward(lat: Double, lengthM: Double, steps: Int = 10): List<GeoPoint> {
        // Degrees of longitude per metre on that parallel, on the same mean sphere haversine measures
        // with: a line built any other way is a line whose legs are not the length they claim, and every
        // promise below is about a metre along it.
        val degLng = Math.toDegrees(lengthM / EARTH_RADIUS_M) / kotlin.math.cos(Math.toRadians(lat))
        return (0..steps).map { step -> GeoPoint(lat = lat, lng = degLng * step / steps) }
    }

    @Test
    fun `the distance along is measured to the line rather than to the nearest vertex`() {
        // One long leg: standing beside its middle must read half of it, not nothing.
        val line = eastward(-41.0, 1_000.0, steps = 1)
        val middle = GeoPoint(lat = -41.0, lng = (line.first().lng + line.last().lng) / 2.0)

        val on = nearestOnPath(line, middle)!!

        assertEquals("half of a thousand metre leg", 500.0, on.alongM, 2.0)
        assertEquals("and standing on it", 0.0, on.offM, 0.01)
        assertEquals("the whole line", 1_000.0, on.totalM, 2.0)
        assertEquals("with half of it left", 500.0, on.remainingM, 2.0)
    }

    @Test
    fun `a walker past the end of the line is at the end of it`() {
        val line = eastward(-41.0, 500.0)
        val past = GeoPoint(lat = -41.0, lng = line.last().lng + 0.01)

        val on = nearestOnPath(line, past)!!

        assertEquals("no line left", 0.0, on.remainingM, 0.01)
        assertEquals("and as far off it as they are", 840.0, on.offM, 10.0)
    }

    @Test
    fun `nothing is measured against a line of one point`() {
        assertNull(nearestOnPath(listOf(GeoPoint(-41.0, 173.0)), GeoPoint(-41.0, 173.0)))
        assertNull(nearestOnPath(emptyList(), GeoPoint(-41.0, 173.0)))
    }

    @Test
    fun `the piece kept starts where the walker joined`() {
        val line = eastward(-41.0, 1_000.0)

        val ahead = pathFrom(line, alongM = 400.0, forward = true)

        assertEquals("starts at the join", 400.0, polylineLengthMeters(listOf(line.first(), ahead.first())), 5.0)
        assertEquals("and runs to the end", 600.0, polylineLengthMeters(ahead), 5.0)
        assertEquals("which is the line's own end", line.last().lng, ahead.last().lng, 1e-9)
    }

    @Test
    fun `joined backwards, the piece runs the way the walker is going`() {
        val line = eastward(-41.0, 1_000.0)

        val behind = pathFrom(line, alongM = 400.0, forward = false)

        assertEquals("it starts at the join and runs west", 400.0, polylineLengthMeters(behind), 5.0)
        assertTrue("heading back towards the line's start", behind.last().lng < behind.first().lng)
        assertEquals("and ends where the line began", line.first().lng, behind.last().lng, 1e-9)
    }

    @Test
    fun `a join on a vertex does not write it twice`() {
        val line = eastward(-41.0, 500.0, steps = 4)

        val ahead = pathFrom(line, alongM = 125.0, forward = true)

        assertEquals("the line left is the three quarters ahead", 375.0, polylineLengthMeters(ahead), 3.0)
        val repeats = ahead.zipWithNext().count { (a, b) ->
            haversineMeters(a.lat, a.lng, b.lat, b.lng) < 0.5
        }
        assertEquals("and no point is written where the one before it was", 0, repeats)
    }

    @Test
    fun `the following readout and the join rule agree about where the walker is`() {
        // The same walk over the segments, once with the climbing and once without.
        val line = eastward(-41.0, 800.0)
        val beside = GeoPoint(lat = -40.9997, lng = 0.002)

        val progress = FollowProgress.of(line, beside)!!
        val on = nearestOnPath(line, beside)!!

        assertEquals(on.alongM, progress.alongM, 1e-6)
        assertEquals(on.offM, progress.offM, 1e-6)
        assertEquals(on.remainingM, progress.remainingM, 1e-6)
    }

    @Test
    fun `a place along the line is a real place on it, not the nearest vertex`() {
        val line = eastward(-41.0, 1_000.0, steps = 10)

        val half = pointAlong(line, 500.0)!!
        val quarter = pointAlong(line, 250.0)!!

        // The promise is the distance, not a longitude: the line is measured with haversine, and the
        // helper above places its points with a flatter projection, so a metre along is what is held.
        assertEquals(
            "half a kilometre along the line",
            500.0,
            polylineLengthMeters(listOf(line.first(), half)),
            0.5
        )
        assertEquals(
            "and a quarter of it",
            250.0,
            polylineLengthMeters(listOf(line.first(), quarter)),
            0.5
        )
        assertEquals("on the line's own latitude", -41.0, half.lat, 1e-9)
    }

    @Test
    fun `a place past the end of the line is the end of it`() {
        val line = eastward(-41.0, 500.0)

        assertEquals(line.first().lng, pointAlong(line, 0.0)!!.lng, 1e-9)
        assertEquals(line.first().lng, pointAlong(line, -40.0)!!.lng, 1e-9)
        assertEquals(line.last().lng, pointAlong(line, 900.0)!!.lng, 1e-9)
    }

    @Test
    fun `the metres run along the whole line, round a corner`() {
        // 500 m east, then 500 m north: 200 m past the corner the place is 200 m up the second leg.
        val cornerLng = 500.0 / (METRES_PER_DEG_LNG_AT_EQUATOR * kotlin.math.cos(Math.toRadians(-41.0)))
        val corner = GeoPoint(lat = -41.0, lng = cornerLng)
        val north = GeoPoint(lat = -41.0 + 500.0 / METRES_PER_DEG_LAT, lng = cornerLng)
        val firstLegM = haversineMeters(-41.0, 0.0, corner.lat, corner.lng)

        val at = pointAlong(listOf(GeoPoint(-41.0, 0.0), corner, north), firstLegM + 200.0)!!

        assertEquals("on the corner's longitude", cornerLng, at.lng, 1e-9)
        assertEquals(
            "and 200 m up the second leg",
            200.0,
            haversineMeters(corner.lat, corner.lng, at.lat, at.lng),
            0.5
        )
    }

    @Test
    fun `there is no place along a line of one point`() {
        assertNull(pointAlong(emptyList(), 10.0))
        assertNull(pointAlong(listOf(GeoPoint(-41.0, 173.0)), 10.0))
    }

    @Test
    fun `the chart's finger lands where the height sample says it should`() {
        // The one arithmetic this feature exists on: a fraction of the graph, metres along the line,
        // and the place those metres are. The two live in different files, so they are held together here.
        val line = eastward(-41.0, 1_000.0, steps = 10).mapIndexed { index, point ->
            point.copy(altitudeM = 100.0 + index * 10.0)
        }
        val profile = ElevationProfile.of(line)!!

        val atHalf = pointAlong(line, profile.alongAt(0.5f))!!

        assertEquals("half the width of the chart", 500.0, polylineLengthMeters(listOf(line.first(), atHalf)), 2.0)
    }
}
