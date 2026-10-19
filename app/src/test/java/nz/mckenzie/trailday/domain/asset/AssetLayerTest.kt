package nz.mckenzie.trailday.domain.asset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layers of the work the map can be asked to leave out.
 *
 * Two promises are pinned here, and both are made to somebody who has never opened the sheet: that
 * a map nobody has touched draws everything, and that what is stored is what is *hidden* - which
 * is why a layer added in a later build arrives on the map rather than missing from it.
 */
class AssetLayerTest {

    @Test
    fun `nothing stored hides nothing, so a fresh install draws everything`() {
        assertTrue(AssetLayer.hiddenIn(null).isEmpty())
        assertTrue(AssetLayer.hiddenIn(emptySet()).isEmpty())
    }

    @Test
    fun `the ids a preference stores are unique and never blank`() {
        val ids = AssetLayer.ALL.map { it.id }

        assertEquals("two layers sharing an id would hide each other", ids.size, ids.toSet().size)
        assertFalse(ids.any { it.isBlank() })
    }

    @Test
    fun `a stored id reads back as the layer it names`() {
        assertEquals(setOf(AssetLayer.BIKE_TRACKS), AssetLayer.hiddenIn(setOf("bike-tracks")))
        assertEquals(
            setOf(AssetLayer.TRACKS, AssetLayer.HUTS),
            AssetLayer.hiddenIn(setOf("tracks", "huts"))
        )
    }

    @Test
    fun `an older build's places switch hides every kind of place`() {
        // Before the map had a layer per kind, one switch hid every kind of place. Somebody who
        // threw it meant the huts, the campsites and their own spots alike - so it is read back as
        // the three, because a layer that comes back is a map that has quietly changed under them.
        assertEquals(
            AssetLayer.ALL.filter { it.kind.shape == AssetShape.POINT }.toSet(),
            AssetLayer.hiddenIn(setOf("places"))
        )
        assertEquals(3, AssetLayer.hiddenIn(setOf("places")).size)
    }

    @Test
    fun `an id this build does not know is ignored rather than hiding something`() {
        // A preference written by a build that had a layer this one does not.
        assertTrue(AssetLayer.hiddenIn(setOf("helicopter-pads")).isEmpty())
        assertEquals(setOf(AssetLayer.TRACKS), AssetLayer.hiddenIn(setOf("tracks", "quarry")))
    }

    @Test
    fun `there is one switch per kind of place, and no kind without one`() {
        assertEquals("five kinds, five switches", AssetKind.entries.size, AssetLayer.ALL.size)
        assertEquals(
            "and one of them for each kind, so a kind cannot arrive without a switch",
            AssetKind.entries.toSet(),
            AssetLayer.ALL.map { it.kind }.toSet()
        )
        assertEquals(
            "which is what lets a kind be hidden on its own, rather than in a family",
            AssetLayer.ALL.size,
            AssetLayer.ALL.map { it.id }.toSet().size
        )
    }

    @Test
    fun `every switch has a name of its own`() {
        // The rows in the layers sheet are the walker's own words for the work, and nothing else: a layer
        // called Huts does not need a line under it saying what a hut is.
        assertEquals(
            AssetLayer.ALL.size,
            AssetLayer.ALL.map { it.displayName }.toSet().size
        )
        AssetLayer.ALL.forEach { layer ->
            assertTrue("${layer.name} has nothing to show on the switch", layer.displayName.isNotBlank())
        }
    }
}
