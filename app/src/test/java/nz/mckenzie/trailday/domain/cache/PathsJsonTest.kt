package nz.mckenzie.trailday.domain.cache

import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A cached track's geometry as text.
 *
 * The cache row is the only place a path is not a points table, so what has to be held still is that
 * a track comes back exactly - every path, every vertex, in order - and that a broken row is nothing
 * rather than a crash in front of a walker with no reception.
 */
class PathsJsonTest {

    @Test
    fun `paths survive the round trip, side tracks and all`() {
        val paths = listOf(
            listOf(GeoPoint(-46.6, 168.3), GeoPoint(-46.61, 168.31)),
            listOf(GeoPoint(-46.61, 168.31), GeoPoint(-46.62, 168.32))
        )

        assertEquals(paths, PathsJson.read(PathsJson.write(paths)))
    }

    @Test
    fun `a single vertex is still a path, and nothing is nothing`() {
        assertEquals(emptyList<List<GeoPoint>>(), PathsJson.write(emptyList()).let(PathsJson::read))
        assertEquals(
            listOf(listOf(GeoPoint(-46.6, 168.3))),
            PathsJson.read(PathsJson.write(listOf(listOf(GeoPoint(-46.6, 168.3)))))
        )
    }

    @Test
    fun `text that will not read is no paths rather than a crash`() {
        assertTrue(PathsJson.read("not json").isEmpty())
        assertTrue(PathsJson.read("").isEmpty())
        assertTrue("a pair too short to be a point is dropped", PathsJson.read("[[[168.3]]]").isEmpty())
    }
}
