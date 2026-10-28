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
        assertEquals("Nothing downloaded yet", imagerySummary(areaCount = 0, "0 B", tiles = 0L))
        assertEquals(
            "and tiles from browsing are not called an area",
            "No areas yet \u00b7 22 tiles from browsing",
            imagerySummary(areaCount = 0, "155 KB", tiles = 22L)
        )
    }

    @Test
    fun `one area is one area and its size, without repeating the name`() {
        // The name is on the row directly under this line; saying it twice made the subtitle the longest
        // thing on the card.
        assertEquals(
            "1 area \u00b7 61 MB",
            imagerySummary(areaCount = 1, "61 MB", tiles = 0L)
        )
    }

    @Test
    fun `several areas are counted, not named`() {
        assertEquals(
            "3 areas \u00b7 61 MB",
            imagerySummary(areaCount = 3, "61 MB", tiles = 0L)
        )
    }

    @Test
    fun `an area says how far along it is, and never calls a half-download done`() {
        assertEquals("Downloaded, 976.6 KB", areaStatus(area("Done")))
        assertEquals("Downloading 45%", areaStatus(area("Going", planned = 100, stored = 45, complete = false)))
        assertEquals("Not started", areaStatus(area("Later", planned = 100, stored = 0, complete = false)))
    }
}
