package nz.mckenzie.trailday.offline

import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.domain.tiles.PlaceLabels
import nz.mckenzie.trailday.domain.tiles.TileMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The zoom range each pass of a download actually fetches.
 *
 * A pack with two passes is two things counted over one box, and the second one - the roads and names
 * over the imagery - stops where LINZ stops publishing while the photograph goes on to 22. Getting this
 * wrong is quiet: the download either fetches the same deep tiles over and over under new numbers, or
 * stops short and the walker finds a box with no roads on it.
 */
class PackPassTest {

    private val points = File(System.getProperty("java.io.tmpdir"), "pass-test")

    private val store = OfflineTileStore(points, ".pbf")

    private fun pass(cap: Int?) = PackPass(
        store = store,
        zoomCap = cap,
        fetcherFor = { error("nothing here fetches") }
    )

    private val box = LatLngBounds(
        minLat = -42.95,
        minLng = 171.55,
        maxLat = -42.93,
        maxLng = 171.58
    )

    @Test
    fun `a pass with no cap takes the area's own range`() {
        assertEquals(10 to 18, pass(null).zoomsFor(10, 18))
        assertEquals(4 to 4, pass(null).zoomsFor(4, 4))
    }

    @Test
    fun `a capped pass stops at the cap but still starts at the area's own first zoom`() {
        assertEquals(10 to 15, pass(15).zoomsFor(10, 18))
        assertEquals("a cap above the area's deepest zoom changes nothing", 10 to 12, pass(15).zoomsFor(10, 12))
    }

    @Test
    fun `an area that starts deeper than the cap still fetches the tiles the map overzooms`() {
        // A box of zoom 18 imagery needs the deepest vectors that exist, because that is what the map
        // draws a road from when it has nothing closer. Asking for 18 to 18 would fetch nothing.
        assertEquals(15 to 15, pass(15).zoomsFor(18, 22))
        assertTrue("and asks for tiles", pass(15).tilesFor(box, 18, 22).isNotEmpty())
    }

    @Test
    fun `a capped pass covers fewer tiles than the photograph beside it`() {
        val vectors = pass(PlaceLabels.MAX_ZOOM).tilesFor(box, 10, 18)
        val imagery = pass(null).tilesFor(box, 10, 18)

        assertTrue("the vectors are fewer: ${vectors.size} of ${imagery.size}", vectors.size < imagery.size)
        assertTrue("and never deeper than LINZ publishes", vectors.all { it.zoom <= PlaceLabels.MAX_ZOOM })
        assertEquals(
            "and the deep end is where the difference is",
            imagery.filter { it.zoom > PlaceLabels.MAX_ZOOM }.size,
            imagery.size - vectors.size
        )
    }

    @Test
    fun `every pass of a pack is counted at its own cap`() {
        // What createArea adds up, and what the picker's own sentence promises. One number for both
        // passes would make a box look too large to save.
        val imagery = pass(null)
        val vectors = pass(PlaceLabels.MAX_ZOOM)

        assertEquals(
            "the two passes' tiles, not the photograph's twice",
            (imagery.tilesFor(box, 10, 18).size + vectors.tilesFor(box, 10, 18).size).toLong(),
            TileMath.tileCount(box, 10, 18) +
                TileMath.tileCount(box, 10, PlaceLabels.MAX_ZOOM)
        )
    }
}

