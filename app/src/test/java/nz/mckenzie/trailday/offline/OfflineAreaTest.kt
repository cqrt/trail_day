package nz.mckenzie.trailday.offline

import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules the offline screen reads off an area's numbers. Percent in
 * particular is worth pinning down: reporting 100% for an area that is short
 * would be the kind of lie that only shows up in the field.
 */
class OfflineAreaTest {

    @Test
    fun `a Topographic download says what it kept of the map and of the heights`() {
        val said = topoDownloadedMessage(
            area(stored = 92, bytes = 811_000, complete = true, pack = OfflinePack.TOPOGRAPHY)
        )

        // One figure for the two halves together, because they are one thing to a walker: the map either
        // draws this ground or it does not - and the record is where the two have already been added up.
        assertTrue("how much came", said.contains("92 tiles"))
        assertTrue("and what it came to", said.contains("KB"))
    }

    @Test
    fun `forgetting an area takes only the tiles no other area needs`() {
        val shared = TileRef(14, 100, 200)
        val mine = listOf(shared, TileRef(14, 101, 200), TileRef(14, 102, 200))
        val other = listOf(shared, TileRef(14, 105, 200))

        val free = tilesToFree(mine, other)

        assertEquals(
            "the two only this area asked for",
            listOf(TileRef(14, 101, 200), TileRef(14, 102, 200)),
            free
        )
        assertFalse("and not the one the two of them share", shared in free)
    }

    @Test
    fun `an area nothing else describes gives up every tile of its own`() {
        val mine = listOf(TileRef(14, 1, 1), TileRef(14, 2, 1))

        assertEquals(mine, tilesToFree(mine, emptyList()))
    }

    private fun area(
        planned: Int = 100,
        stored: Int = 0,
        bytes: Long = 0L,
        complete: Boolean = false,
        pack: OfflinePack = OfflinePack.IMAGERY
    ) = OfflineArea(
        id = 1L,
        name = "Block",
        bounds = LatLngBounds(minLat = -41.6, minLng = 173.9, maxLat = -41.4, maxLng = 174.1),
        plannedTiles = planned,
        storedTiles = stored,
        bytes = bytes,
        isComplete = complete,
        pack = pack
    )

    @Test
    fun `a partial download reports its share`() {
        assertEquals(0, area(planned = 100, stored = 0).percent)
        assertEquals(25, area(planned = 100, stored = 25).percent)
        assertEquals(99, area(planned = 100, stored = 99).percent)
    }

    @Test
    fun `an incomplete area never reports 100 percent`() {
        // 304 of 304 tiles but the download was interrupted: not done yet.
        assertEquals(99, area(planned = 304, stored = 304, complete = false).percent)
        assertEquals(100, area(planned = 304, stored = 304, complete = true).percent)
    }

    @Test
    fun `missing tiles are what the plan needs that is not on disk`() {
        assertEquals(0, area(planned = 304, stored = 304, complete = true).missingTiles)
        assertEquals(12, area(planned = 304, stored = 292, complete = true).missingTiles)
        // Never negative, even if the count somehow overshoots the plan.
        assertEquals(0, area(planned = 10, stored = 12).missingTiles)
    }

    @Test
    fun `a complete area with a shortfall is flagged but not treated as broken`() {
        assertFalse(area(planned = 304, stored = 304, complete = true).isShortButComplete)
        assertTrue(area(planned = 304, stored = 292, complete = true).isShortButComplete)
        assertFalse(area(planned = 304, stored = 292, complete = false).isShortButComplete)
    }

    @Test
    fun `an empty plan does not divide by zero`() {
        assertEquals(0, area(planned = 0, stored = 0).percent)
        assertEquals(0, area(planned = 0, stored = 0).missingTiles)
    }

    @Test
    fun `sizes are formatted for the screen`() {
        assertEquals("68.4 MB", area().copy(bytes = 71_720_000L).sizeLabel)
        assertEquals("512 B", area().copy(bytes = 512L).sizeLabel)
    }
}
