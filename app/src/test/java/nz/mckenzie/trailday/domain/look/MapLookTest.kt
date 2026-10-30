package nz.mckenzie.trailday.domain.look

import nz.mckenzie.trailday.domain.asset.AssetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the stored look has to mean.
 *
 * The look is written to preferences, so the ids are the contract: a look written by this build has to
 * read back as the same look tomorrow, and a row written by a build that knows a colour this one does
 * not has to leave the other rows alone rather than lose the lot. The default is pinned for the
 * reason the theme's is - it is the look nobody chose, and an install that has never opened the screen
 * must draw exactly what it always drew.
 */
class MapLookTest {

    @Test
    fun `the default look is the one the app already drew`() {
        val look = MapLook.DEFAULT

        assertEquals("tracks", AssetColors.TRACK_KIND, look.colourOf(AssetKind.TRACK).hex)
        assertEquals("bike tracks", AssetColors.BIKE_TRACK_KIND, look.colourOf(AssetKind.BIKE_TRACK).hex)
        assertEquals("huts", AssetColors.HUT_KIND, look.colourOf(AssetKind.HUT).hex)
        assertEquals("campsites", AssetColors.CAMPSITE_KIND, look.colourOf(AssetKind.CAMPSITE).hex)
        assertEquals("waypoints", AssetColors.WAYPOINT_KIND, look.colourOf(AssetKind.WAYPOINT).hex)

        assertEquals("every line", 3f, look.widthOf(LookTarget.TRACKS).stylePx)
        assertEquals("every marker", 22f, look.sizeOf(AssetKind.HUT).dp)

        assertTrue("an untouched install is the default", look.isDefault)
    }

    @Test
    fun `the other publisher's lines have a row of their own, and a default of their own`() {
        // Choosing a colour for tracks must not be choosing it for the councils' and trusts' lines:
        // the whole reason that layer has a colour is so a walker can tell whose line is whose.
        val tracks = MapLook.DEFAULT.colourOf(LookTarget.TRACKS)
        val others = MapLook.DEFAULT.colourOf(LookTarget.OTHER_TRACKS)

        assertFalse("other tracks default to the track colour", tracks == others)
        assertEquals("and are the shelf's teal", LookColour.TEAL, others)
    }

    @Test
    fun `a default look writes nothing at all`() {
        assertEquals("", MapLook.DEFAULT.toStorage())
        assertEquals(MapLook.DEFAULT, MapLook.fromStorage(""))
        assertEquals(MapLook.DEFAULT, MapLook.fromStorage(null))
    }

    @Test
    fun `a changed look reads back as the look it was`() {
        val chosen = MapLook.DEFAULT
            .withColour(LookTarget.TRACKS, LookColour.CRIMSON)
            .withWidth(LookTarget.TRACKS, LineWidthChoice.THICK)
            .withColour(LookTarget.HUTS, LookColour.CHARCOAL)
            .withSize(LookTarget.HUTS, MarkerSizeChoice.LARGE)

        val stored = chosen.toStorage()

        assertTrue("only the changed rows are written: $stored", stored.contains("tracks="))
        assertFalse("an unchanged row is left out: $stored", stored.contains("campsites="))
        assertEquals(chosen, MapLook.fromStorage(stored))
    }

    @Test
    fun `a row this build does not know is skipped, and the rest are kept`() {
        val stored = "tracks=crimson:thick;hovercrafts=blue:thick;huts=charcoal:large"

        val look = MapLook.fromStorage(stored)

        assertEquals(LookColour.CRIMSON, look.colourOf(LookTarget.TRACKS))
        assertEquals(LineWidthChoice.THICK, look.widthOf(LookTarget.TRACKS))
        assertEquals(LookColour.CHARCOAL, look.colourOf(LookTarget.HUTS))
        assertEquals(MarkerSizeChoice.LARGE, look.sizeOf(LookTarget.HUTS))
        assertEquals("the rest is untouched", LookColour.TEAL.hex, look.colourOf(AssetKind.CAMPSITE).hex)
    }

    @Test
    fun `a colour this build does not know loses that row's colour and nothing else`() {
        val look = MapLook.fromStorage("tracks=vermillion:thick")

        assertEquals("the colour falls back", LookColour.BLUE, look.colourOf(LookTarget.TRACKS))
        assertEquals("the width it could read is kept", LineWidthChoice.THICK, look.widthOf(LookTarget.TRACKS))
    }

    @Test
    fun `a width is not read as a marker size, and the other way round`() {
        // "large" is a marker size and "thin" is a line width. Read into the wrong control they would
        // either be dropped silently or, worse, be accepted as something they are not.
        assertEquals(
            LineWidthChoice.NORMAL,
            MapLook.fromStorage("tracks=blue:large").widthOf(LookTarget.TRACKS)
        )
        assertEquals(
            MarkerSizeChoice.NORMAL,
            MapLook.fromStorage("huts=orange:thin").sizeOf(LookTarget.HUTS)
        )
    }

    @Test
    fun `nonsense is the default rather than a crash`() {
        listOf("", "   ", "=", "tracks", "tracks=", "tracks=:", ";;;", "a build wrote this").forEach { stored ->
            assertEquals("\"$stored\" is not a look", MapLook.DEFAULT, MapLook.fromStorage(stored))
        }
    }

    @Test
    fun `changing one row leaves the others where they were`() {
        val chosen = MapLook.DEFAULT.withColour(LookTarget.HUTS, LookColour.MAGENTA)

        assertEquals(LookColour.MAGENTA, chosen.colourOf(LookTarget.HUTS))
        assertEquals(
            "the look it came from is a value, not a thing that was edited in place",
            LookColour.ORANGE,
            MapLook.DEFAULT.colourOf(LookTarget.HUTS)
        )
        assertTrue(chosen.colourOf(LookTarget.CAMPSITES) == LookColour.TEAL)
    }

    @Test
    fun `the dark tone of a row is its chosen colour's dark tone`() {
        val chosen = MapLook.DEFAULT.withColour(LookTarget.HUTS, LookColour.CRIMSON)

        assertEquals(LookColour.CRIMSON.onDark, chosen.colourOnDark(AssetKind.HUT))
    }

    private companion object {
        /** Reached through the map package, where the app's own colours live. */
        val AssetColors = nz.mckenzie.trailday.map.AssetColors
    }
}
