package nz.mckenzie.trailday.domain.look

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shelf of colours, and the dark tone each one is given.
 *
 * Two things are being held still here. The first is that no colour on the shelf can be mistaken for
 * the line that was walked: a green track beside a recorded green track says nothing. The second is
 * that every colour has a dark-screen tone that reads, because the app follows the phone's theme and a
 * colour chosen on a white page is the colour a walker then takes into a dark one.
 */
class LookColourTest {

    @Test
    fun `eight colours, each with its own id and its own value`() {
        assertEquals("the shelf's size", 8, LookColour.entries.size)
        assertEquals(
            "two colours under one id would overwrite each other in preferences",
            LookColour.entries.size,
            LookColour.entries.map { it.id }.toSet().size
        )
        assertEquals(
            "two colours with one value would be one colour offered twice",
            LookColour.entries.size,
            LookColour.entries.map { it.hex }.toSet().size
        )
    }

    @Test
    fun `no green is on the shelf, because a green line is a line that was walked`() {
        val greens = listOf("#2E7D32", "#81C784", "#4CAF50", "#008000")

        LookColour.entries.forEach { colour ->
            greens.forEach { green ->
                assertNotEquals("${colour.id} is a green", green, colour.hex)
            }
            assertNotEquals(
                "${colour.id} is the recorded green",
                nz.mckenzie.trailday.map.AssetColors.RECORDED,
                colour.hex
            )
        }
    }

    @Test
    fun `every colour reads on the page a dark phone draws behind a card`() {
        val page = LightTone.luminance(LightTone.DARK_PAGE)!!

        LookColour.entries.forEach { colour ->
            val ratio = contrast(LightTone.luminance(colour.onDark)!!, page)
            assertTrue(
                "${colour.id}'s dark tone ${colour.onDark} is only ${round(ratio)}:1 on the dark page",
                ratio >= LightTone.TARGET_CONTRAST
            )
        }
    }

    @Test
    fun `the dark tone is lighter than the colour it comes from, and adds nothing else`() {
        LookColour.entries.forEach { colour ->
            assertTrue(
                "${colour.id} is not lifted at all",
                LightTone.luminance(colour.onDark)!! > LightTone.luminance(colour.hex)!!
            )
            // Only white is mixed in, so no channel of the lifted tone can be dimmer than the colour
            // it came from. The hue survives because nothing was taken away.
            channels(colour.hex).zip(channels(colour.onDark)).forEach { (from, to) ->
                assertTrue("${colour.id} lost some of a channel: $from to $to", to >= from)
            }
        }
    }

    @Test
    fun `a colour that already reads on a dark page is handed back as it is`() {
        // The tone is a floor, not an instruction: washing out a colour that was already light enough
        // would make the same choice look different depending on which shade it started from.
        assertEquals("#FFFFFF", LightTone.of("#FFFFFF"))
        assertEquals("#EEEEEE", LightTone.of("#EEEEEE"))
    }

    @Test
    fun `a value that is not a colour is handed back rather than guessed at`() {
        assertEquals("", LightTone.of(""))
        assertEquals("not a colour", LightTone.of("not a colour"))
        assertEquals("#12345", LightTone.of("#12345"))
        assertNull(LightTone.luminance("nonsense"))
    }

    @Test
    fun `a stored id reads back as the colour it names, and anything else as nothing`() {
        LookColour.entries.forEach { colour ->
            assertEquals(colour, LookColour.fromStorage(colour.id))
        }
        assertNull(LookColour.fromStorage("vermillion"))
        assertNull(LookColour.fromStorage(""))
    }

    @Test
    fun `each id is fit for a preferences file`() {
        LookColour.entries.forEach { colour ->
            assertTrue(
                "\"${colour.id}\" is written to a preferences file, so it stays simple",
                colour.id.isNotBlank() && colour.id.all { it.isLetter() }
            )
            assertTrue("${colour.id} needs a name", colour.displayName.isNotBlank())
        }
    }

    @Test
    fun `the app's own five colours are on the shelf, so today's look can be got back`() {
        val shelf = LookColour.entries.map { it.hex }

        listOf(
            nz.mckenzie.trailday.map.AssetColors.TRACK_KIND,
            nz.mckenzie.trailday.map.AssetColors.BIKE_TRACK_KIND,
            nz.mckenzie.trailday.map.AssetColors.HUT_KIND,
            nz.mckenzie.trailday.map.AssetColors.CAMPSITE_KIND,
            nz.mckenzie.trailday.map.AssetColors.WAYPOINT_KIND
        ).forEach { present ->
            assertTrue("$present is not on the shelf", present in shelf)
        }
    }

    @Test
    fun `no colour on the shelf is the dot that says where the phone is`() {
        assertFalse(
            "a place colour that reads as the position marker would be worse than no colour",
            nz.mckenzie.trailday.map.AssetColors.POSITION in LookColour.entries.map { it.hex }
        )
    }

    private fun channels(hex: String): List<Int> {
        val digits = hex.removePrefix("#")
        return (0 until 3).map { index -> digits.substring(index * 2, index * 2 + 2).toInt(16) }
    }

    private fun contrast(first: Double, second: Double): Double =
        (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)

    private fun round(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)
}
