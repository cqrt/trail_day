package nz.mckenzie.trailday.domain.journey

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.reference.Followable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which published lines are worth measuring against, and why it is a box.
 *
 * A box is a filter and never an answer: the test that matters is that a line whose own ground is far
 * away is left out even when its box has been dragged over the walker by a long path somewhere else on it.
 */
class LinesNearbyTest {

    private class Published(override val name: String, override val paths: List<List<GeoPoint>>) : Followable {
        override val sourceRef: String get() = "ref:$name"
    }

    /** A line running east at [lat], from [fromLng] to [toLng]. */
    private fun line(lat: Double, fromLng: Double, toLng: Double) =
        listOf(GeoPoint(lat, fromLng), GeoPoint(lat, toLng))

    @Test
    fun `a line running past the walker is near`() {
        val near = Published("Nearby", listOf(line(-41.5, 173.95, 173.96)))
        val far = Published("Faraway", listOf(line(-41.5, 174.10, 174.11)))

        val boxes = listOf(near, far).boxed()

        assertEquals(listOf(near), boxes.near(GeoPoint(-41.5, 173.955), 150.0))
    }

    @Test
    fun `a box is a filter and not an answer`() {
        // A path that runs up and away: its box stretches from the walker's own ground to ground five
        // kilometres north, because a box is drawn round what a path covers rather than along it. The
        // walker is inside the box while standing nowhere near the line, and the measuring is what says so.
        val awayAndUp = Published(
            "Long Diagonal",
            listOf(
                listOf(GeoPoint(-41.5, 173.90), GeoPoint(-41.455, 173.955), GeoPoint(-41.455, 174.05))
            )
        )

        val boxes = listOf(awayAndUp).boxed()

        assertEquals(
            "let through, and measured afterwards",
            listOf(awayAndUp),
            boxes.near(GeoPoint(-41.5, 173.955), 150.0)
        )
    }

    @Test
    fun `a line that is its own ground 600 m away is not near`() {
        // One straight path's box is as tall as the path: a line running level, 600 m north of the walker,
        // is outside the box rather than in it.
        val levelAndNorth = Published("Long Line", listOf(line(-41.4946, 173.90, 173.96)))

        assertTrue(
            listOf(levelAndNorth).boxed().near(GeoPoint(-41.5, 173.955), 150.0).isEmpty()
        )
    }

    @Test
    fun `a line with a path in pieces is named once`() {
        val pieces = Published(
            "Nine Rows",
            listOf(
                line(-41.5, 173.95, 173.951),
                line(-41.5, 173.951, 173.952),
                line(-41.5, 173.952, 173.953)
            )
        )

        val boxes = listOf(pieces).boxed()

        assertEquals("three paths, three boxes", 3, boxes.size)
        assertEquals("and one track", 1, boxes.near(GeoPoint(-41.5, 173.951), 150.0).size)
    }

    @Test
    fun `a path of one point is not a line and is not boxed`() {
        val place = Published("A Hut", listOf(listOf(GeoPoint(-41.5, 173.95))))

        assertTrue(place.let { listOf(it) }.boxed().isEmpty())
    }

    @Test
    fun `nothing at all answers nothing`() {
        assertTrue(emptyList<Followable>().boxed().isEmpty())
        assertTrue(emptyList<BoxedLine>().near(GeoPoint(-41.5, 173.95), 150.0).isEmpty())
    }
}
