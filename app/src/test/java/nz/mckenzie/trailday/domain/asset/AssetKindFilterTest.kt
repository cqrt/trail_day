package nz.mckenzie.trailday.domain.asset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the place list shows when a kind chip is tapped.
 *
 * The case that matters is the one where a filter is wrong in a way nobody can see: a row that
 * quietly disappears.
 */
class AssetKindFilterTest {

    @Test
    fun `everything is shown before a chip is tapped`() {
        assertTrue(AssetKindFilter.All.keeps("HUT"))
        assertTrue(AssetKindFilter.All.keeps("TRACK"))
    }

    @Test
    fun `a kind shows its own rows and nothing else`() {
        val huts = AssetKindFilter(AssetKind.HUT)

        assertTrue(huts.keeps("HUT"))
        assertFalse("a track is not a hut", huts.keeps("TRACK"))
        assertFalse("and a campsite is not either", huts.keeps("CAMPSITE"))
    }

    @Test
    fun `an unknown stored word is read the way the rest of the app reads it`() {
        // A value this build has never heard of reads as a track, so it is found under Track rather
        // than disappearing from every chip at once.
        assertTrue(AssetKindFilter(AssetKind.TRACK).keeps("PADDOCK"))
        assertFalse(AssetKindFilter(AssetKind.HUT).keeps("PADDOCK"))
    }

    @Test
    fun `tapping the chip already showing brings all of them back`() {
        val huts = AssetKindFilter.afterTapping(AssetKind.HUT, AssetKindFilter.All)
        assertEquals("a chip tapped from All shows that kind", AssetKind.HUT, huts.kind)

        assertEquals(
            "and the same chip tapped again is the way back out",
            AssetKindFilter.All,
            AssetKindFilter.afterTapping(AssetKind.HUT, huts)
        )

        assertEquals(
            "another chip moves the filter rather than clearing it",
            AssetKind.CAMPSITE,
            AssetKindFilter.afterTapping(AssetKind.CAMPSITE, huts).kind
        )
    }

    @Test
    fun `choosing all while a kind is showing clears it`() {
        val huts = AssetKindFilter(AssetKind.HUT)

        assertEquals(AssetKindFilter.All, AssetKindFilter.afterTapping(null, huts))
        assertEquals("and choosing it again changes nothing", AssetKindFilter.All, AssetKindFilter.afterTapping(null, AssetKindFilter.All))
    }
}
