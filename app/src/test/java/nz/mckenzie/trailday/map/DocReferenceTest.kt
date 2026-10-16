package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DOC's material as the map draws it.
 *
 * Two things are worth holding still here. **A DOC row is not one of the walker's places**: its features
 * carry no asset id, so nothing on the map can open, rename, move or delete one, and a tap on DOC's
 * material is a tap on the ground. And **the reference layer has layers of its own**, named apart from
 * the walker's, because it is drawn under the work, cased so it reads against any ground, and is not
 * something the layer switches speak for.
 */
class DocReferenceTest {

    private fun track(id: Long = 35239046L) = DocItem(
        source = DocSource.TRACKS,
        objectId = id,
        name = "Glory Tk",
        paths = listOf(listOf(GeoPoint(-46.6, 168.3), GeoPoint(-46.61, 168.31)))
    )

    private fun hut() = DocItem(
        source = DocSource.HUTS,
        objectId = 218L,
        name = "Angelus Hut",
        paths = listOf(listOf(GeoPoint(-41.62, 172.65))),
        facts = mapOf("bookable" to "Yes")
    )

    @Test
    fun `a line service is drawn as a line and the rest as places`() {
        assertEquals(AssetShape.LINE, DocSource.TRACKS.drawingShape())
        assertEquals(AssetShape.POINT, DocSource.HUTS.drawingShape())
        assertEquals(AssetShape.POINT, DocSource.CAMPSITES.drawingShape())
        assertEquals(AssetShape.POINT, DocSource.BIKES.drawingShape())
    }

    @Test
    fun `each service is drawn in the kind its things are`() {
        assertEquals(AssetKind.TRACK, DocSource.TRACKS.drawingKind())
        assertEquals(AssetKind.HUT, DocSource.HUTS.drawingKind())
        assertEquals(AssetKind.CAMPSITE, DocSource.CAMPSITES.drawingKind())
        // A bike location is a place, so it is drawn as one: the app's bicycle is a line kind, and a
        // marker vocabulary invented here would be a reference-layer marking in the walker's own picker.
        assertEquals(AssetKind.WAYPOINT, DocSource.BIKES.drawingKind())
        DocSource.entries.forEach { source ->
            assertEquals(
                "$source is drawn in a place's kind only if it publishes places",
                source.isLine,
                source.drawingKind().shape == AssetShape.LINE
            )
        }
    }

    @Test
    fun `a DOC track is drawn in the track's colour, as a line, with no id of its own`() {
        val json = DocGeoJson.build(listOf(track()))

        assertTrue(json.contains("\"kind\":\"${AssetKind.TRACK.name}\""))
        assertTrue(json.contains("\"shape\":\"${AssetShape.LINE.name}\""))
        assertTrue("the track's own colour", json.contains(AssetColors.forKind(AssetKind.TRACK)))
        assertTrue("and its name, which the map never writes for DOC's material", json.contains("Glory Tk"))
        assertTrue(
            "no id: nothing on the map can act on a DOC row, so nothing may look like one of the walker's",
            json.contains("\"id\":0")
        )
    }

    @Test
    fun `a DOC place is drawn as one coordinate with the kind's own marker`() {
        val json = DocGeoJson.build(listOf(hut()))

        assertTrue(json.contains("\"kind\":\"${AssetKind.HUT.name}\""))
        assertTrue(json.contains("\"shape\":\"${AssetShape.POINT.name}\""))
        assertTrue(
            "the whole of why a place is drawn as a marker: it asks for the hut's own picture",
            json.contains(PlaceIcons.imageName(AssetKind.HUT))
        )
    }

    @Test
    fun `nothing of DOC's is drawn without DOC saying so`() {
        assertEquals(
            "no material, no features - and the map draws an empty reference layer rather than nothing",
            AssetGeoJson.build(emptyList()),
            DocGeoJson.build(emptyList())
        )
    }

    @Test
    fun `the reference layer has layers of its own, apart from the walker's`() {
        val walker = AssetLayerIds.ALL
        DocLayerIds.ALL.forEach { id ->
            assertFalse("$id must not be one of the walker's layers", id in walker)
            assertTrue(
                "$id is the reference layer's, so it is named as the reference layer's: id",
                id.startsWith("trailday-doc-")
            )
        }
        assertTrue("DOC's tracks are cased", DocLayerIds.CASING in DocLayerIds.ALL)
        assertTrue(DocLayerIds.TRACKS in DocLayerIds.ALL)
        PlaceIcons.KINDS.forEach { kind ->
            assertTrue(
                "every kind DOC publishes a place as has a layer to be drawn in",
                DocLayerIds.pointOf(kind) in DocLayerIds.ALL
            )
        }
        assertEquals("and nothing is drawn twice", DocLayerIds.ALL.size, DocLayerIds.ALL.toSet().size)
    }
}
