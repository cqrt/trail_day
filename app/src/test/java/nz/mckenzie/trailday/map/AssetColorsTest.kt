package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.look.LookColour
import nz.mckenzie.trailday.domain.look.LookTarget
import nz.mckenzie.trailday.domain.look.MapLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colours the map and the list are built on.
 *
 * The five kinds' colours are no longer constants the app draws with - they are the five answers the
 * look is defaulted to ([MapLook.DEFAULT]), and the walker can choose any of the eight on the shelf
 * instead. So what is held here is the relationship between the two: the default is the shelf, the
 * recorded line is nobody's colour on it, and every colour on it can be read on the cards the app draws
 * on.
 *
 * Two of these were real bugs before the shelf existed: a brown that read on white and vanished on the
 * dark card, and a deep purple with the same story. A colour offered to the walker has to survive both.
 */
class AssetColorsTest {

    @Test
    fun `the five default colours are the shelf's own values`() {
        val shelf = LookColour.entries.map { it.hex }

        listOf(
            AssetColors.TRACK_KIND,
            AssetColors.BIKE_TRACK_KIND,
            AssetColors.HUT_KIND,
            AssetColors.CAMPSITE_KIND,
            AssetColors.WAYPOINT_KIND
        ).forEach { value ->
            assertTrue("$value is not a colour the walker can pick", value in shelf)
        }
    }

    @Test
    fun `the default look gives each kind a colour, and no two share one`() {
        val colours = AssetKind.entries.map { MapLook.DEFAULT.colourOf(it).hex }

        assertEquals("a colour per kind", AssetKind.entries.size, colours.size)
        assertEquals(
            "two kinds sharing a colour would make the list say less than it claims to: $colours",
            colours.size,
            colours.toSet().size
        )
    }

    @Test
    fun `the two lines do not share a colour with each other or with a place`() {
        val look = MapLook.DEFAULT
        val track = look.colourOf(AssetKind.TRACK).hex
        val bikeTrack = look.colourOf(AssetKind.BIKE_TRACK).hex

        assertNotEquals("a bike track must not look like a track", track, bikeTrack)
        AssetKind.entries.filter { it.shape == AssetShape.POINT }.forEach { place ->
            assertNotEquals("$place must not look like a track", track, look.colourOf(place).hex)
            assertNotEquals("$place must not look like a bike track", bikeTrack, look.colourOf(place).hex)
        }
    }

    @Test
    fun `the other publisher's line is a colour of its own`() {
        // Choosing a colour for tracks must not be choosing it for the councils' and trusts' lines: the
        // whole reason that layer has a colour is so a walker can tell whose line is whose.
        val look = MapLook.DEFAULT

        assertNotEquals(look.colourOf(LookTarget.TRACKS), look.colourOf(LookTarget.OTHER_TRACKS))
    }

    @Test
    fun `every colour on the shelf is readable on the light card the list uses`() {
        LookColour.entries.forEach { colour ->
            val ratio = contrastRatio(colour.hex, LIGHT_CARD)
            assertTrue("${colour.id} on a light card is only ${round(ratio)}:1", ratio >= MIN_CONTRAST)
        }
    }

    @Test
    fun `and its derived tone on the dark card, because the app follows the system theme`() {
        LookColour.entries.forEach { colour ->
            val ratio = contrastRatio(colour.onDark, DARK_CARD)
            assertTrue("${colour.id} on a dark card is only ${round(ratio)}:1", ratio >= MIN_CONTRAST)
        }
    }

    @Test
    fun `the recorded line is nobody's kind, on the shelf or in either theme`() {
        // An icon the walker could read as "the recording" would be worse than no icon at all, and the
        // gold casing under a recorded line filters on this exact value.
        LookColour.entries.forEach { colour ->
            assertNotEquals("${colour.id} looks like the line being recorded", AssetColors.RECORDED, colour.hex)
            assertNotEquals(
                "${colour.id}'s dark tone looks like the line being recorded",
                AssetColors.RECORDED,
                colour.onDark
            )
        }
    }

    @Test
    fun `and no colour on the shelf is the dot that says where the phone is`() {
        LookColour.entries.forEach { colour ->
            assertNotEquals(
                "${colour.id} and the position marker are the same colour",
                AssetColors.POSITION,
                colour.hex
            )
        }
    }

    @Test
    fun `a line a walk has been made of is drawn in the recorded green, whatever its kind`() {
        // The map draws a place in its kind's colour until a walk has been made of it. From then on it is
        // the line the walker actually went along, and it wears the recorded green - with the map's own
        // gold casing, which filters on exactly this colour.
        val look = MapLook.DEFAULT

        assertEquals(
            "the recorded green",
            AssetColors.RECORDED,
            look.colourOfPlace(AssetKind.TRACK, recorded = true)
        )
        assertEquals(
            "a bike track a ride was made of is recorded too, not its own colour",
            AssetColors.RECORDED,
            look.colourOfPlace(AssetKind.BIKE_TRACK, recorded = true)
        )
        assertEquals(
            "and a place with no walk is still drawn in its row's colour",
            AssetColors.HUT_KIND,
            look.colourOfPlace(AssetKind.HUT, recorded = false)
        )
    }

    @Test
    fun `the recorded green the look answers with is the one the map's layers filter on`() {
        // `colourOfPlace` names its own copy of the value, and the gold casing filters on AssetColors'.
        // Two copies of one number is how a recorded line comes to be drawn with no edge under it.
        assertEquals(MapLook.RECORDED, AssetColors.RECORDED)
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

        /** The app's own card colours: white in the light scheme, a sage-charcoal in the dark one. */
        const val LIGHT_CARD = "#FFFFFF"
        const val DARK_CARD = "#1D211A"
    }
}
