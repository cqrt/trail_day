package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetLayer
import nz.mckenzie.trailday.domain.asset.AssetShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which style layer each switch hides.
 *
 * The link between a switch and the layer behind it is the one thing here that no compiler checks:
 * the layer is built in the map view and the switch is built in a sheet, and the two only meet
 * through this table. What is pinned is that every layer a walker can hide has a layer to hide,
 * that no two switches share one - two switches hiding one layer would make one of them a lie -
 * and that the names are the ones the map itself builds.
 */
class AssetLayerIdsTest {

    @Test
    fun `every layer a walker can hide has a style layer behind it`() {
        AssetLayer.ALL.forEach { layer ->
            assertTrue(
                "${layer.displayName} would switch nothing",
                AssetLayerIds.of(layer).isNotBlank()
            )
        }
    }

    @Test
    fun `no two switches hide the same layer`() {
        val ids = AssetLayer.ALL.map { AssetLayerIds.of(it) }

        assertEquals(
            "two switches hiding one layer makes one of them a lie: $ids",
            ids.size,
            ids.toSet().size
        )
    }

    @Test
    fun `every kind of place has its own marker layer, and no line kind shares one`() {
        val placeIds = PlaceIcons.KINDS.map { AssetLayerIds.pointOf(it) }
        val lineIds = AssetLayer.ALL
            .filter { it.kind.shape == AssetShape.LINE }
            .map { AssetLayerIds.of(it) }

        assertEquals("one layer per place kind", 3, placeIds.size)
        assertTrue(
            "a place kind hidden must not take a line with it: $lineIds",
            lineIds.none { it in placeIds }
        )
        assertEquals(
            "and those three are what the place kinds' own switches hide",
            placeIds,
            AssetLayer.ALL.filter { it.kind.shape == AssetShape.POINT }.map { AssetLayerIds.of(it) }
        )
    }

    @Test
    fun `every layer the map draws is one a switch hides`() {
        val hidden = AssetLayer.ALL.flatMap { AssetLayerIds.idsOf(it) }

        // Compared as sets, because the two lists are in different orders on purpose: a switch is
        // listed where the walker looks for it, and a layer is drawn where it belongs - the lines
        // first, then the names beside the tracks, then the places.
        assertEquals(
            "a style layer with no switch is a layer a walker cannot hide: $hidden",
            AssetLayerIds.ALL.sorted(),
            hidden.sorted()
        )
        assertEquals(
            "and nothing is drawn twice: ${AssetLayerIds.ALL}",
            AssetLayerIds.ALL.size,
            AssetLayerIds.ALL.toSet().size
        )
    }

    @Test
    fun `the tracks' own switch hides the name with the track`() {
        assertEquals(
            "a track's line and the name written beside it are one thing to the walker",
            listOf(AssetLayerIds.TRACKS, AssetLayerIds.TRACK_NAMES),
            AssetLayerIds.idsOf(AssetLayer.TRACKS)
        )
        assertEquals(
            "and the line is still the layer a track is named by",
            AssetLayerIds.TRACKS,
            AssetLayerIds.of(AssetLayer.TRACKS)
        )

        val others = AssetLayer.ALL
            .filterNot { it == AssetLayer.TRACKS }
            .flatMap { AssetLayerIds.idsOf(it) }
        assertTrue(
            "no other switch hides a track's name: $others",
            others.none { it == AssetLayerIds.TRACK_NAMES }
        )
    }

    @Test
    fun `the names are the map's own, so renaming a layer without this is a failing test`() {
        assertEquals("trailday-assets-line-track", AssetLayerIds.of(AssetLayer.TRACKS))
        assertEquals("trailday-assets-line-bike-track", AssetLayerIds.of(AssetLayer.BIKE_TRACKS))
        assertEquals("trailday-assets-line-track-names", AssetLayerIds.TRACK_NAMES)
        assertEquals("trailday-assets-point-hut", AssetLayerIds.of(AssetLayer.HUTS))
        assertEquals("trailday-assets-point-campsite", AssetLayerIds.of(AssetLayer.CAMPSITES))
        assertEquals("trailday-assets-point-waypoint", AssetLayerIds.of(AssetLayer.WAYPOINTS))
    }
}
