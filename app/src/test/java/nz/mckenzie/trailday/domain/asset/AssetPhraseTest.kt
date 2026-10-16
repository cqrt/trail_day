package nz.mckenzie.trailday.domain.asset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a place is called, which shape each kind is, and what a stored kind means.
 *
 * A picker with a blank chip is a picker the walker cannot use, so the words are tested for every
 * value rather than only the ones a screen happens to draw today. The shape table is tested for the
 * same kind of reason: the kind deciding the shape is the whole point of it, and a kind answering
 * wrongly would draw a waypoint as a line across a valley with nothing else to notice.
 */
class AssetPhraseTest {

    @Test
    fun `every kind has a name, and none of them is blank`() {
        assertEquals("Track", AssetPhrase.kind(AssetKind.TRACK))
        assertEquals("Bike track", AssetPhrase.kind(AssetKind.BIKE_TRACK))
        assertEquals("Hut", AssetPhrase.kind(AssetKind.HUT))
        assertEquals("Campsite", AssetPhrase.kind(AssetKind.CAMPSITE))
        assertEquals("Waypoint", AssetPhrase.kind(AssetKind.WAYPOINT))

        AssetKind.entries.forEach { kind ->
            assertTrue("$kind has no name", AssetPhrase.kind(kind).isNotBlank())
        }
    }

    @Test
    fun `the picker offers everything there is, in enum order`() {
        assertEquals(AssetKind.entries.toList(), AssetPhrase.kinds)
    }

    @Test
    fun `two kinds are lines and three are places`() {
        assertEquals(
            setOf(AssetKind.TRACK, AssetKind.BIKE_TRACK),
            AssetKind.entries.filter { it.shape == AssetShape.LINE }.toSet()
        )
        assertEquals(
            setOf(AssetKind.HUT, AssetKind.CAMPSITE, AssetKind.WAYPOINT),
            AssetKind.entries.filter { it.shape == AssetShape.POINT }.toSet()
        )
    }

    @Test
    fun `a line is asked for its length, and changing a drawing is changing a line`() {
        assertEquals("Length", AssetPhrase.lengthLabel())
        assertEquals("Change the line", AssetPhrase.changeLabel())
    }

    @Test
    fun `a stored kind is read back as itself`() {
        AssetKind.entries.forEach { kind ->
            assertEquals(kind, AssetKind.fromStorage(kind.name))
        }
    }

    @Test
    fun `an unknown kind reads as a track, because that is what every old place is`() {
        assertEquals(AssetKind.TRACK, AssetKind.fromStorage(null))
        assertEquals(AssetKind.TRACK, AssetKind.fromStorage(""))
        assertEquals(AssetKind.TRACK, AssetKind.fromStorage("PADDOCK"))
    }

    @Test
    fun `an unknown kind has no meaning at all, which is what a write is refused on`() {
        assertNull(AssetKind.known("PADDOCK"))
        assertNull(AssetKind.known(null))
        assertNull(AssetKind.known(""))

        assertEquals(AssetKind.HUT, AssetKind.known("HUT"))
    }

    @Test
    fun `an unknown shape reads as a line`() {
        assertEquals(AssetShape.LINE, AssetShape.fromStorage(null))
        assertEquals(AssetShape.LINE, AssetShape.fromStorage("SQUIGGLE"))
        assertEquals(AssetShape.POINT, AssetShape.fromStorage("POINT"))
    }
}
