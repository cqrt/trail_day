package nz.mckenzie.trailday.domain.asset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The four parts of the walker's own work under the Your Places master. */
class YourPlaceLayerTest {

    @Test
    fun `nothing stored hides nothing`() {
        assertTrue(YourPlaceLayer.hiddenIn(null).isEmpty())
        assertTrue(YourPlaceLayer.hiddenIn(emptySet()).isEmpty())
    }

    @Test
    fun `a stored id reads back as the tile it names`() {
        assertEquals(
            setOf(YourPlaceLayer.TRACKS, YourPlaceLayer.RECORDINGS),
            YourPlaceLayer.hiddenIn(setOf("tracks", "recordings"))
        )
    }

    @Test
    fun `an id this build does not know is ignored`() {
        assertEquals(
            setOf(YourPlaceLayer.SPOTS),
            YourPlaceLayer.hiddenIn(setOf("spots", "helicopter-pads"))
        )
    }

    @Test
    fun `every kind belongs to one of the four tiles`() {
        assertEquals(YourPlaceLayer.SPOTS, YourPlaceLayer.forKind(AssetKind.WAYPOINT))
        assertEquals(YourPlaceLayer.SPOTS, YourPlaceLayer.forKind(AssetKind.HUT))
        assertEquals(YourPlaceLayer.SPOTS, YourPlaceLayer.forKind(AssetKind.CAMPSITE))
        assertEquals(YourPlaceLayer.TRACKS, YourPlaceLayer.forKind(AssetKind.TRACK))
        assertEquals(YourPlaceLayer.BIKE_TRACKS, YourPlaceLayer.forKind(AssetKind.BIKE_TRACK))
    }
}
