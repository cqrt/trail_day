package nz.mckenzie.trailday.domain.journey

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.METRES_PER_DEG_LAT
import nz.mckenzie.trailday.domain.geo.METRES_PER_DEG_LNG_AT_EQUATOR
import nz.mckenzie.trailday.domain.geo.polylineLengthMeters
import nz.mckenzie.trailday.domain.reference.Followable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/**
 * Walking onto another line, and the three things that make it safe.
 *
 * **A line crossed is not a line walked**, **a line the walker is already measured against is never offered
 * back to them**, and **being on the line is not being off it**. Everything else here is those three, seen
 * from a different angle.
 *
 * The distance off the followed line is handed in rather than measured, because that is how the recorder
 * hands it in: it is the same number the readout shows. A walker standing on a line 40 m from the one they
 * are following is the ordinary case, and every test below is built that way.
 */
class JoiningTrackTest {

    private class Published(
        override val name: String,
        override val paths: List<List<GeoPoint>>
    ) : Followable {
        override val sourceRef: String get() = "ref:$name"
    }

    private val lat = -41.5
    private val perDegreeLng = METRES_PER_DEG_LNG_AT_EQUATOR * cos(Math.toRadians(lat))

    /** A line running east from [lng], [lengthM] long, [northM] north of where the walk started. */
    private fun east(lng: Double, lengthM: Double, northM: Double = 0.0): List<GeoPoint> =
        (0..20).map { step ->
            GeoPoint(
                lat = lat + northM / METRES_PER_DEG_LAT,
                lng = lng + lengthM * step / 20 / perDegreeLng
            )
        }

    /** A line running north and south through [lat], [lengthM] long, [eastM] east of [lng]. */
    private fun north(lng: Double, lengthM: Double, eastM: Double = 0.0): List<GeoPoint> =
        (0..20).map { step ->
            GeoPoint(
                lat = lat - lengthM / 2 / METRES_PER_DEG_LAT + lengthM * step / 20 / METRES_PER_DEG_LAT,
                lng = lng + eastM / perDegreeLng
            )
        }

    /** A place [eastM] east of [lng] and [northM] north of where the walk started. */
    private fun at(lng: Double, eastM: Double, northM: Double = 0.0): GeoPoint =
        GeoPoint(lat = lat + northM / METRES_PER_DEG_LAT, lng = lng + eastM / perDegreeLng)

    private val followed = east(173.9500, 400.0)
    private val other = Published("Mount Pleasant Track", listOf(east(173.9500, 400.0, northM = 40.0)))

    /**
     * Walks [points], and answers with the **first** line handed over.
     *
     * The first rather than the last: the recorder resets the rule the moment it switches, so a rule that
     * keeps answering on later fixes is answering something the recorder never asks again.
     */
    private fun walk(
        rule: JoiningTrack,
        followed: List<GeoPoint>,
        points: List<GeoPoint>,
        offFollowed: Double,
        candidates: List<Followable>
    ): Join? {
        var first: Join? = null
        for (point in points) {
            val answer = rule.consider(followed, point, offFollowed, candidates)
            if (first == null) first = answer
        }
        return first
    }

    @Test
    fun `walking along another line hands it over`() {
        val rule = JoiningTrack()

        val join = walk(
            rule,
            followed,
            listOf(0.0, 8.0, 16.0, 24.0).map { metres -> at(173.9500, metres, 40.0) },
            offFollowed = 40.0,
            candidates = listOf(other)
        )

        assertNotNull("twenty metres on it is walking it", join)
        assertSame("and it is that line", other, join!!.track)
        assertEquals("joined just past twenty metres along", 24.0, join.atAlongM, 3.0)
        assertEquals(
            "and handed the rest of it, starting where the walker joined",
            400.0 - join.atAlongM,
            polylineLengthMeters(join.line),
            6.0
        )
    }

    @Test
    fun `a line only crossed is not walked`() {
        // The other line runs north and south, and the walker crosses it on the level of the line being
        // followed: 40 m of walking within 20 m of it, and not one metre of it along it.
        val crossing = Published("Crossing Track", listOf(north(173.9520, 200.0)))
        val rule = JoiningTrack()

        var join: Join? = null
        for (metres in listOf(-20.0, -10.0, 0.0, 10.0, 20.0)) {
            join = rule.consider(followed, at(173.9520, metres, 40.0), offFollowed = 40.0, candidates = listOf(crossing))
        }

        assertNull("a crossing is not a hand-over", join)
    }

    @Test
    fun `on the line being followed it does not look at all`() {
        val rule = JoiningTrack()

        val join = rule.consider(followed, at(173.9500, 30.0, 40.0), offFollowed = 2.0, candidates = listOf(other))

        assertNull("being on the line is not being off it", join)
    }

    @Test
    fun `the line being walked is never offered back`() {
        // Standing on the line with the distance handed in as 40: the distance is the caller's number, and
        // what is being tested is that the rule does not hand back the piece it is already measuring.
        val rule = JoiningTrack()
        val mine = Published("The Line", listOf(followed))

        var join: Join? = null
        for (metres in listOf(0.0, 30.0, 60.0)) {
            join = rule.consider(followed, at(173.9500, metres), offFollowed = 40.0, candidates = listOf(mine))
        }

        assertNull("the piece underfoot is not a line to move onto", join)
    }

    @Test
    fun `the piece cut from a line it is already on is never offered either`() {
        // What a mid-route hand-over hands over: a piece of the published line, cut at the join. The walker
        // is 60 m past the end of that piece and standing on the published line itself, which is the one
        // case where the parent is near enough to be measured. Without the guard the rule would join them
        // back onto the line they are already on, every twenty metres, for the rest of the walk.
        val published = east(173.9500, 400.0)
        val cut = published.subList(0, 5)
        val parent = Published("The Line", listOf(published))
        val rule = JoiningTrack()

        var join: Join? = null
        for (metres in listOf(160.0, 175.0, 190.0, 205.0)) {
            join = rule.consider(cut, at(173.9500, metres), offFollowed = 60.0, candidates = listOf(parent))
        }

        assertNull("a piece of a line is still that line", join)
    }

    @Test
    fun `the next piece of the same walkway is offered without a fuss`() {
        val first = east(173.9500, 200.0)
        val second = east(173.9525, 200.0, northM = 40.0)
        val walkway = Published("Kingswell Creek Walkway", listOf(first, second))
        val rule = JoiningTrack()

        var join: Join? = null
        for (metres in listOf(0.0, 10.0, 20.0, 30.0)) {
            join = rule.consider(first, at(173.9525, metres, 40.0), offFollowed = 40.0, candidates = listOf(walkway))
        }

        assertNotNull("the walk carries on", join)
        assertSame("on the same walkway", walkway, join!!.track)
    }

    @Test
    fun `joined backwards, the piece runs the way the walker is going`() {
        val rule = JoiningTrack()

        val join = walk(
            rule,
            followed,
            listOf(300.0, 280.0, 260.0).map { metres -> at(173.9500, metres, 40.0) },
            offFollowed = 40.0,
            candidates = listOf(other)
        )

        assertNotNull("walking back down it is walking it", join)
        assertTrue("so the piece starts where the walker is", join!!.line.first().lng > join.line.last().lng)
        assertEquals(
            "and runs all the way back to the line's first point",
            join.atAlongM,
            polylineLengthMeters(join.line),
            8.0
        )
    }

    @Test
    fun `dithering on the same ground is not walking a line`() {
        val rule = JoiningTrack()

        var join: Join? = null
        // Two fixes ten metres apart, taken over and over: ten metres neither way is not twenty metres on.
        repeat(6) {
            join = rule.consider(followed, at(173.9500, 10.0, 40.0), offFollowed = 40.0, candidates = listOf(other))
            join = rule.consider(followed, at(173.9500, 0.0, 40.0), offFollowed = 40.0, candidates = listOf(other))
        }

        assertNull(join)
    }

    @Test
    fun `nothing within reach answers nothing`() {
        val faraway = Published("Somewhere Else", listOf(east(174.0, 400.0)))
        val rule = JoiningTrack()

        val join = rule.consider(followed, at(173.9500, 40.0, 40.0), offFollowed = 400.0, candidates = listOf(faraway))

        assertNull(join)
    }

    @Test
    fun `a new target starts the settling again`() {
        val rule = JoiningTrack()

        rule.consider(followed, at(173.9500, 0.0, 40.0), offFollowed = 40.0, candidates = listOf(other))
        rule.consider(followed, at(173.9500, 15.0, 40.0), offFollowed = 40.0, candidates = listOf(other))
        rule.reset()
        val join = rule.consider(followed, at(173.9500, 30.0, 40.0), offFollowed = 40.0, candidates = listOf(other))

        assertNull("the first fix on a line is never the twentieth metre", join)
    }
}
