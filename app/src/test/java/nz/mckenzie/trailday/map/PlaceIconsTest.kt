package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
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
        assertEquals(6, PlaceIcons.KINDS.size)
    }

    @Test
    fun `a picture is named after the kind, so the two cannot drift apart`() {
        assertEquals("trailday-place-building", PlaceIcons.imageName(AssetKind.BUILDING))
        assertEquals("trailday-place-sign", PlaceIcons.imageName(AssetKind.SIGN))
        assertEquals("trailday-place-bench", PlaceIcons.imageName(AssetKind.BENCH))
        assertEquals(
            "a kind with two words in it is one word in a name",
            "trailday-place-other-place",
            PlaceIcons.imageName(AssetKind.OTHER_PLACE)
        )
        assertFalse(
            "a hash in an image name is a URL fragment, not part of a picture",
            PlaceIcons.imageName(AssetKind.BUILDING).contains("#")
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
            "and neither does ground with an edge: a shape is drawn, not stuck on the map",
            PlaceIcons.FALLBACK_IMAGE_NAME,
            PlaceIcons.imageName(AssetKind.CARPARK)
        )
        assertTrue(
            "the fallback has to be a picture the style was given",
            PlaceIcons.IMAGE_NAMES.contains(PlaceIcons.FALLBACK_IMAGE_NAME)
        )
    }

    @Test
    fun `a place is drawn in its own kind's colour`() {
        PlaceIcons.KINDS.forEach { kind ->
            assertEquals(
                "a ${kind.name} is drawn in the colour the kind says",
                AssetColors.forKind(kind),
                PlaceIcons.colorOf(kind)
            )
        }
    }

    @Test
    fun `the marker's white edge is thin`() {
        val share = PlaceIcons.MARKER_OUTLINE_DP / PlaceIcons.MARKER_DP

        assertTrue(
            "an edge of ${PlaceIcons.MARKER_OUTLINE_DP}dp on a ${PlaceIcons.MARKER_DP}dp marker is " +
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
            PlaceIcons.MARKER_DP > 16f
        )
    }
}
