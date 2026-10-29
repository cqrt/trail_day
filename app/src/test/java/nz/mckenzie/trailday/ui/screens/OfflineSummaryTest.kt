package nz.mckenzie.trailday.ui.screens

import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.offline.OfflineArea
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two lines the offline cards say about themselves: what an area list holds, and what one area is doing.
 *
 * Both are the sort of sentence that goes wrong quietly - "1 areas", or "Downloaded" for an area that stopped
 * halfway - so they are held still here rather than checked by eye on a phone.
 */
class OfflineSummaryTest {

    private fun area(
        name: String,
        planned: Int = 100,
        stored: Int = 100,
        complete: Boolean = true
    ) = OfflineArea(
        id = 1L,
        name = name,
        bounds = LatLngBounds(minLat = -41.6, minLng = 172.7, maxLat = -41.5, maxLng = 172.8),
        plannedTiles = planned,
        storedTiles = stored,
        bytes = 1_000_000L,
        isComplete = complete
    )

    @Test
    fun `an empty cache says so rather than saying nought areas`() {
        assertEquals(
            "Nothing downloaded yet",
            keptTilesSummary(areaCount = 0, "0 B", tiles = 0L, nothing = "Nothing downloaded yet")
        )
        assertEquals(
            "and tiles from browsing are not called an area",
            "No areas yet \u00b7 22 tiles from browsing",
            keptTilesSummary(areaCount = 0, "155 KB", tiles = 22L, nothing = "Nothing downloaded yet")
        )
        assertEquals(
            "one tile is one tile",
            "No areas yet \u00b7 1 tile from browsing",
            keptTilesSummary(areaCount = 0, "8 KB", tiles = 1L, nothing = "Nothing downloaded yet")
        )
    }

    @Test
    fun `one area is one area, its tiles and its size, without repeating the name`() {
        // The name is on the row directly under this line; saying it twice made the subtitle the longest
        // thing on the card.
        assertEquals(
            "1 area \u00b7 28 tiles \u00b7 61 MB",
            keptTilesSummary(areaCount = 1, "61 MB", tiles = 28L, nothing = "Nothing downloaded yet")
        )
    }

    @Test
    fun `several areas are counted, not named`() {
        assertEquals(
            "3 areas \u00b7 940 tiles \u00b7 61 MB",
            keptTilesSummary(areaCount = 3, "61 MB", tiles = 940L, nothing = "Nothing downloaded yet")
        )
    }

    @Test
    fun `the drawn map's card says the same things as the imagery's`() {
        // The two cards answer the same question - a box, the tiles in it, what it cost - so one line answers
        // both. Only the words for "nothing" differ, because the drawn map is kept rather than downloaded.
        assertEquals(
            "2 areas \u00b7 410 tiles \u00b7 12 MB",
            keptTilesSummary(areaCount = 2, "12 MB", tiles = 410L, nothing = "Nothing kept yet")
        )
        assertEquals(
            "Nothing kept yet",
            keptTilesSummary(areaCount = 0, "0 B", tiles = 0L, nothing = "Nothing kept yet")
        )
    }

    @Test
    fun `an area says how far along it is, and never calls a half-download done`() {
        assertEquals("Downloaded, 976.6 KB", areaStatus(area("Done")))
        assertEquals("Downloading 45%", areaStatus(area("Going", planned = 100, stored = 45, complete = false)))
        assertEquals("Not started", areaStatus(area("Later", planned = 100, stored = 0, complete = false)))
    }
}
