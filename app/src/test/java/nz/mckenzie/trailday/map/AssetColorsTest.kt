package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colours the map and the list draw with.
 *
 * Every kind must have a colour of its own, and the kind colours must not be the recorded line's or
 * the position marker's - an icon the walker could read as "the recording" would be worse than no
 * icon at all.
 */
class AssetColorsTest {

    @Test
    fun `each of the five kinds has its own colour, and no two share one`() {
        val colours = AssetKind.entries.map { AssetColors.forKind(it) }

        assertEquals("a colour per kind", AssetKind.entries.size, colours.size)
        assertEquals(
            "two kinds sharing a colour would make the map say less than it claims to: $colours",
            colours.size,
            colours.toSet().size
        )
    }

    @Test
    fun `the two lines do not share a colour with each other or with a place`() {
        val track = AssetColors.forKind(AssetKind.TRACK)
        val bikeTrack = AssetColors.forKind(AssetKind.BIKE_TRACK)

        assertNotEquals("a bike track must not look like a track", track, bikeTrack)
        AssetKind.entries
            .filter { it.shape == nz.mckenzie.trailday.domain.asset.AssetShape.POINT }
            .forEach { place ->
                assertNotEquals("$place must not look like a track", track, AssetColors.forKind(place))
                assertNotEquals("$place must not look like a bike track", bikeTrack, AssetColors.forKind(place))
            }
    }

    @Test
    fun `a kind colour is readable on the light card the list uses`() {
        AssetKind.entries.forEach { kind ->
            val ratio = contrastRatio(AssetColors.forKind(kind), LIGHT_CARD)
            assertTrue("$kind on a light card is only ${round(ratio)}:1", ratio >= MIN_CONTRAST)
        }
    }

    @Test
    fun `and on the dark one, because the app follows the system theme`() {
        // This is the case that made a brown icon disappear: fine on white, all but
        // invisible on the dark grey a phone in dark mode puts behind it.
        AssetKind.entries.forEach { kind ->
            val ratio = contrastRatio(AssetColors.forKind(kind), DARK_CARD)
            assertTrue("$kind on a dark card is only ${round(ratio)}:1", ratio >= MIN_CONTRAST)
        }
    }

    @Test
    fun `and not a kind colour either, even though it is now blue`() {
        AssetKind.entries.forEach { kind ->
            assertTrue(
                "$kind and the position marker are the same colour",
                AssetColors.forKind(kind) != AssetColors.POSITION
            )
        }
    }

    /** WCAG contrast: 1.0 is the same colour, 21.0 is black on white. */
    private fun contrastRatio(first: String, second: String): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun luminance(hex: String): Double {
        val value = hex.removePrefix("#").toInt(16)
        val channels = listOf((value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)
            .map { channel ->
                val fraction = channel / 255.0
                if (fraction <= 0.03928) fraction / 12.92
                else Math.pow((fraction + 0.055) / 1.055, 2.4)
            }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
    }

    private fun round(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

    private companion object {
        /** Enough for a thin outline to be read on the card behind it. */
        const val MIN_CONTRAST = 3.0

        /** A Material light card, and a dark one. */
        const val LIGHT_CARD = "#FFFFFF"
        const val DARK_CARD = "#121212"
    }
}
