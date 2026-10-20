package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which of DOC's own layers each switch hides.
 *
 * The bug that started this: a switch called "Huts" left DOC's huts standing on the ground, because the
 * switches hid only the walker's own layers. What is pinned here is that **every layer DOC draws can be
 * hidden, and by the switch that names it** - a casing hides with the line it edges, a place hides with
 * the kind it is drawn as, DOC's rideable tracks hide with the bike switch, and the walker's waypoint
 * switch has no DOC layer because a bike point is a bike trail now.
 */
class DocLayerIdsTest {

    @Test
    fun `every layer DOC draws is hidden by one of the switches`() {
        val hidden = AssetLayer.ALL.flatMap { DocLayerIds.idsOf(it) }.toSet()

        assertEquals(
            "a layer of DOC's with no switch to hide it is a layer that stays on the ground: " +
                (DocLayerIds.ALL - hidden),
            DocLayerIds.ALL.toSet(),
            hidden
        )
    }

    @Test
    fun `no two switches hide the same layer of DOC's`() {
        val ids = AssetLayer.ALL.flatMap { DocLayerIds.idsOf(it) }

        assertEquals("two switches hiding one layer makes one of them a lie: $ids", ids.size, ids.toSet().size)
    }

    @Test
    fun `DOC Tracks goes with the walking-only tracks, casing and all`() {
        // The white casing is the line's own edge: leaving it behind would draw a white line along the
        // ground where a track used to be.
        assertEquals(
            listOf(DocLayerIds.CASING, DocLayerIds.TRACKS),
            DocLayerIds.idsOf(AssetLayer.TRACKS)
        )
    }

    @Test
    fun `Bike tracks goes with the rideable tracks, their casing and their badges`() {
        assertEquals(
            listOf(DocLayerIds.BIKE_CASING, DocLayerIds.BIKE_TRACKS, DocLayerIds.BIKE_BADGES),
            DocLayerIds.idsOf(AssetLayer.BIKE_TRACKS)
        )
    }

    @Test
    fun `DOC's huts and campsites go with the kinds they are drawn as`() {
        assertEquals(listOf(DocLayerIds.pointOf(nz.mckenzie.trailday.domain.asset.AssetKind.HUT)), DocLayerIds.idsOf(AssetLayer.HUTS))
        assertEquals(
            listOf(DocLayerIds.pointOf(nz.mckenzie.trailday.domain.asset.AssetKind.CAMPSITE)),
            DocLayerIds.idsOf(AssetLayer.CAMPSITES)
        )
    }

    @Test
    fun `the waypoint switch has no DOC layer, because bike points are bike trails now`() {
        assertTrue(
            "DOC has no waypoint marker layer; its bike points are badges under the bike switch",
            DocLayerIds.idsOf(AssetLayer.WAYPOINTS).isEmpty()
        )
    }
}
