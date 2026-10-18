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
 * the kind it is drawn as, and a bike track hides nothing because DOC publishes no bike lines.
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
    fun `DOC's tracks go with the tracks, casing and all`() {
        // The white casing is the line's own edge: leaving it behind would draw a white line along the
        // ground where a track used to be.
        assertEquals(listOf(DocLayerIds.CASING, DocLayerIds.TRACKS), DocLayerIds.idsOf(AssetLayer.TRACKS))
    }

    @Test
    fun `DOC's huts, campsites and bike locations go with the kinds they are drawn as`() {
        assertEquals(listOf(DocLayerIds.pointOf(nz.mckenzie.trailday.domain.asset.AssetKind.HUT)), DocLayerIds.idsOf(AssetLayer.HUTS))
        assertEquals(
            listOf(DocLayerIds.pointOf(nz.mckenzie.trailday.domain.asset.AssetKind.CAMPSITE)),
            DocLayerIds.idsOf(AssetLayer.CAMPSITES)
        )
        // DOC publishes its bike layer as places, so they are drawn as waypoints and go with them.
        assertEquals(
            listOf(DocLayerIds.pointOf(nz.mckenzie.trailday.domain.asset.AssetKind.WAYPOINT)),
            DocLayerIds.idsOf(AssetLayer.WAYPOINTS)
        )
    }

    @Test
    fun `a bike track switch has nothing of DOC's to hide`() {
        assertTrue(
            "DOC publishes no bike lines, so the switch is about the walker's own rides",
            DocLayerIds.idsOf(AssetLayer.BIKE_TRACKS).isEmpty()
        )
    }
}
