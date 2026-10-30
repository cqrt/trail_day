package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.look.Look
import nz.mckenzie.trailday.domain.look.LookTarget
import nz.mckenzie.trailday.domain.look.MapLook
import nz.mckenzie.trailday.domain.look.MarkerSizeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which picture a place is drawn with.
 *
 * What matters is not the drawing - that is a bitmap, and it is looked at - but that the map can
 * always put something on a place, in the kind's own colour. A place drawn as nothing at all looks
 * like an asset somebody has deleted.
 */
class PlaceIconsTest {

    @Test
    fun `every kind of place has a picture to ask for`() {
        PlaceIcons.KINDS.forEach { kind ->
            assertTrue(
                "a ${kind.name} would have no picture to ask for",
                PlaceIcons.IMAGE_NAMES.contains(PlaceIcons.imageName(kind))
            )
        }
    }

    @Test
    fun `the kinds that are a place are the kinds the app says are places`() {
        assertEquals(
            "a place is a single spot, and the kinds say which those are",
            AssetKind.entries.filter { it.shape == AssetShape.POINT },
            PlaceIcons.KINDS
        )
        assertEquals(3, PlaceIcons.KINDS.size)
    }

    @Test
    fun `a picture is named after the kind, so the two cannot drift apart`() {
        assertEquals("trailday-place-hut", PlaceIcons.imageName(AssetKind.HUT))
        assertEquals("trailday-place-campsite", PlaceIcons.imageName(AssetKind.CAMPSITE))
        assertEquals("trailday-place-waypoint", PlaceIcons.imageName(AssetKind.WAYPOINT))
        assertFalse(
            "a hash in an image name is a URL fragment, not part of a picture",
            PlaceIcons.imageName(AssetKind.HUT).contains("#")
        )
    }

    @Test
    fun `no two pictures share a name`() {
        assertEquals("a picture per kind", PlaceIcons.KINDS.size, PlaceIcons.IMAGE_NAMES.size)
        assertEquals(
            "two of them sharing a name would make one of them a lie: ${PlaceIcons.IMAGE_NAMES}",
            PlaceIcons.KINDS.size,
            PlaceIcons.IMAGE_NAMES.toSet().size
        )
    }

    @Test
    fun `a kind that is not a place wears the grey ring`() {
        assertEquals(
            "a line kind has no marker at all",
            PlaceIcons.FALLBACK_IMAGE_NAME,
            PlaceIcons.imageName(AssetKind.TRACK)
        )
        assertEquals(
            "and neither does a bike track",
            PlaceIcons.FALLBACK_IMAGE_NAME,
            PlaceIcons.imageName(AssetKind.BIKE_TRACK)
        )
        assertTrue(
            "the fallback has to be a picture the style was given",
            PlaceIcons.IMAGE_NAMES.contains(PlaceIcons.FALLBACK_IMAGE_NAME)
        )
    }

    @Test
    fun `a place is drawn in the colour its row was given`() {
        PlaceIcons.KINDS.forEach { kind ->
            assertEquals(
                "a ${kind.name} is drawn in the colour the walker chose for its row",
                Look.colourOf(kind).hex,
                PlaceIcons.colorOf(kind)
            )
        }
    }

    @Test
    fun `a place is drawn at the size its row was given`() {
        // A marker is a picture rather than a line, so its size is a marker size and not one of the three
        // line widths - the two choices are separate types for exactly this reason.
        MarkerSizeChoice.entries.forEach { size ->
            val look = MapLook.DEFAULT.withSize(LookTarget.HUTS, size)

            assertEquals(
                "a hut at ${size.id} is ${size.dp}dp across",
                size.dp,
                look.sizeOf(AssetKind.HUT).dp
            )
        }
    }

    @Test
    fun `the marker's white edge is thin`() {
        val share = PlaceIcons.MARKER_OUTLINE_DP / MarkerSizeChoice.NORMAL.dp

        assertTrue(
            "an edge of ${PlaceIcons.MARKER_OUTLINE_DP}dp on a ${MarkerSizeChoice.NORMAL.dp}dp marker is " +
                "${(share * 100).toInt()}% of its width, which is not thin",
            share <= 0.05f
        )
        assertTrue(
            "and less than one dp would not be drawn at all",
            PlaceIcons.MARKER_OUTLINE_DP >= 1f
        )
    }

    @Test
    fun `a name reads back as the kind it was made from`() {
        PlaceIcons.KINDS.forEach { kind ->
            assertEquals(
                "the desk asks for a picture by name, so a name has to mean one kind",
                kind,
                PlaceIcons.ofImageName(PlaceIcons.imageName(kind))
            )
        }
        assertNull(
            "a name that is no picture reads back as nothing",
            PlaceIcons.ofImageName("trailday-place-nonsense")
        )
    }

    @Test
    fun `a place is drawn wider than the dot it replaced`() {
        assertTrue(
            "the dot was 16dp across, and these glyphs have legs, posts and a ring to read",
            MarkerSizeChoice.NORMAL.dp > 16f
        )
    }
}
