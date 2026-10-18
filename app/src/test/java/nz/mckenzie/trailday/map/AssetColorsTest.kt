package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colours the map and the list draw with.
 *
 * Every kind must have a colour of its own in both themes, and no kind colour may be the recorded line's or
 * the position marker's - an icon the walker could read as "the recording" would be worse than no icon at
 * all. The mock screens are light only, so the dark set is derived from the light one: the same five hues,
 * light enough to read on a dark card, which is what [AssetColors.forKindOnDark] is for.
 */
class AssetColorsTest {

    @Test
    fun `each of the five kinds has its own colour, and no two share one, in both themes`() {
        val light = AssetKind.entries.map { AssetColors.forKind(it) }
        val dark = AssetKind.entries.map { AssetColors.forKindOnDark(it) }

        assertEquals("a colour per kind", AssetKind.entries.size, light.size)
        assertEquals(
            "two kinds sharing a light colour would make the list say less than it claims to: $light",
            light.size,
            light.toSet().size
        )
        assertEquals(
            "and two sharing a dark one would do the same on a dark phone: $dark",
            dark.size,
            dark.toSet().size
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
    fun `and the derived dark one on the dark card, because the app follows the system theme`() {
        // This is the case that made a brown icon disappear: fine on white, all but invisible on the dark
        // grey a phone in dark mode puts behind it. A deep purple is the same story, which is why the dark
        // set exists rather than the light one being reused.
        AssetKind.entries.forEach { kind ->
            val ratio = contrastRatio(AssetColors.forKindOnDark(kind), DARK_CARD)
            assertTrue("$kind on a dark card is only ${round(ratio)}:1", ratio >= MIN_CONTRAST)
        }
    }

    @Test
    fun `the recorded line is nobody's kind, in either theme`() {
        AssetKind.entries.forEach { kind ->
            assertNotEquals(
                "$kind looks like the line being recorded",
                AssetColors.RECORDED,
                AssetColors.forKind(kind)
            )
            assertNotEquals(
                "$kind looks like the line being recorded in dark mode",
                AssetColors.RECORDED,
                AssetColors.forKindOnDark(kind)
            )
            assertNotEquals(
                "$kind and the position marker are the same colour",
                AssetColors.forKind(kind),
                AssetColors.POSITION
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

    @Test
    fun `a line a walk has been made of is drawn in the recorded green, whatever its kind`() {
        // The map draws a place in its kind's colour until a walk has been made of it. From then on it is the
        // line the walker actually went along, and it wears the recorded green - with the map's own gold
        // casing, which filters on exactly this colour.
        assertEquals(
            "the recorded green",
            AssetColors.RECORDED,
            AssetColors.forPlace(AssetKind.TRACK, recorded = true)
        )
        assertEquals(
            "a bike track a ride was made of is recorded too, not purple",
            AssetColors.RECORDED,
            AssetColors.forPlace(AssetKind.BIKE_TRACK, recorded = true)
        )
        assertEquals(
            "and a place with no walk is still its kind's own colour",
            AssetColors.HUT_KIND,
            AssetColors.forPlace(AssetKind.HUT, recorded = false)
        )
    }

    private companion object {
        /** Enough for a thin outline to be read on the card behind it. */
        const val MIN_CONTRAST = 3.0

        /** The app's own card colours: white in the light scheme, a sage-charcoal in the dark one. */
        const val LIGHT_CARD = "#FFFFFF"
        const val DARK_CARD = "#1D211A"
    }
}
