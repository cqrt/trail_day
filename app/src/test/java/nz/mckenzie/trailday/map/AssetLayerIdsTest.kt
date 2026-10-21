package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.YourPlaceLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which style layer each switch hides.
 *
 * The link between a switch and the layer behind it is the one thing here that no compiler checks:
 * the layer is built in the map view and the switch is built in a sheet, and the two only meet
 * through these tables. What is pinned is that every tile a walker can hide has a layer to hide,
 * that no two tiles share one - two switches hiding one layer would make one of them a lie - and
 * that the names are the ones the map itself builds.
 */
class AssetLayerIdsTest {

    @Test
    fun `every Your Places tile has a style layer behind it`() {
        YourPlaceLayer.ALL.forEach { layer ->
            assertTrue(
                "${layer.displayName} would switch nothing",
                YourPlaceLayerIds.idsOf(layer).isNotEmpty()
            )
        }
    }

    @Test
    fun `no two Your Places tiles hide the same layer`() {
        val ids = YourPlaceLayer.ALL.flatMap { YourPlaceLayerIds.idsOf(it) }

        assertEquals(
            "two tiles hiding one layer makes one of them a lie: $ids",
            ids.size,
            ids.toSet().size
        )
    }

    @Test
    fun `every kind of place has its own marker layer, and no line kind shares one`() {
        val placeIds = PlaceIcons.KINDS.map { AssetLayerIds.pointOf(it) }
        val lineIds = listOf(AssetLayerIds.TRACKS, AssetLayerIds.BIKE_TRACKS)

        assertEquals("one layer per place kind", 3, placeIds.size)
        assertTrue(
            "a place kind hidden must not take a line with it: $lineIds",
            lineIds.none { it in placeIds }
        )
        assertEquals(
            "and those three are what the spots tile hides",
            placeIds,
            YourPlaceLayerIds.idsOf(YourPlaceLayer.SPOTS)
        )
    }

    @Test
    fun `every own line and place layer is one of the Your Places tiles`() {
        val hidden = YourPlaceLayer.ALL.flatMap { YourPlaceLayerIds.idsOf(it) }.toSet()

        AssetLayerIds.ALL.forEach { id ->
            assertTrue(
                "$id is drawn on the map but no Your Places tile can hide it",
                id in hidden
            )
        }
    }

    @Test
    fun `the tracks tile hides the name and the recorded edge with the track`() {
        assertEquals(
            "a track's line, its name and the gold under a recorded one are one thing to the walker",
            listOf(
                AssetLayerIds.TRACKS,
                AssetLayerIds.TRACK_NAMES,
                YourPlaceLayerIds.RECORDED_TRACK_CASING
            ),
            YourPlaceLayerIds.idsOf(YourPlaceLayer.TRACKS)
        )

    }

    @Test
    fun `the bike tile hides its recorded edge with the bike line`() {
        assertEquals(
            listOf(AssetLayerIds.BIKE_TRACKS, YourPlaceLayerIds.RECORDED_BIKE_CASING),
            YourPlaceLayerIds.idsOf(YourPlaceLayer.BIKE_TRACKS)
        )
    }

    @Test
    fun `the recordings tile hides the raw recorded line and its edge`() {
        assertEquals(
            listOf(
                YourPlaceLayerIds.YOUR_TRACKS_CASING,
                YourPlaceLayerIds.YOUR_TRACKS_LINE
            ),
            YourPlaceLayerIds.idsOf(YourPlaceLayer.RECORDINGS)
        )
    }

    @Test
    fun `the names are the map's own, so renaming a layer without this is a failing test`() {
        assertEquals("trailday-assets-line-track", AssetLayerIds.TRACKS)
        assertEquals("trailday-assets-line-bike-track", AssetLayerIds.BIKE_TRACKS)
        assertEquals("trailday-assets-line-track-names", AssetLayerIds.TRACK_NAMES)
        assertEquals("trailday-assets-point-hut", AssetLayerIds.pointOf(AssetKind.HUT))
        assertEquals("trailday-assets-point-campsite", AssetLayerIds.pointOf(AssetKind.CAMPSITE))
        assertEquals("trailday-assets-point-waypoint", AssetLayerIds.pointOf(AssetKind.WAYPOINT))
    }
}
