package nz.mckenzie.trailday.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        // A bike location is a bike trail: its row and badge wear the bicycle, even though DOC
        // publishes it as a point rather than a line.
        assertEquals(AssetKind.BIKE_TRACK, DocSource.BIKES.drawingKind())
        DocSource.entries.forEach { source ->
            assertEquals(
                "$source is drawn as a line only if it publishes lines",
                source.isLine,
                source.drawingShape() == AssetShape.LINE
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
    fun `a DOC track in more than one piece is drawn as more than one line`() {
        // "Breast Hill Track" is four paths with one of them 18 km away. What the reader keeps apart,
        // the map must draw apart: one feature per path, with no line joining them.
        val split = DocItem(
            source = DocSource.TRACKS,
            objectId = 7L,
            name = "Breast Hill Track",
            paths = listOf(
                listOf(GeoPoint(-44.448507, 169.582669), GeoPoint(-44.449097, 169.584333)),
                listOf(GeoPoint(-44.580960, 169.360818), GeoPoint(-44.581000, 169.361000))
            )
        )

        val json = DocGeoJson.build(listOf(split))

        // Parsed rather than counted as text: the name rides on a stretch of its own, so the feature
        // count is not the answer. What matters is that no line holds both pieces.
        val lines = Json.parseToJsonElement(json).jsonObject["features"]!!.jsonArray
            .map { it.jsonObject["geometry"]!!.jsonObject }
            .filter { it["type"]!!.jsonPrimitive.content == "LineString" }

        assertEquals("two pieces, two lines, and the one the name rides on", 3, lines.size)
        assertEquals(
            "and no line holds both pieces",
            2,
            lines.maxOf { it["coordinates"]!!.jsonArray.size }
        )
        assertTrue(
            "the far piece is on the map",
            json.contains("169.360818")
        )
    }

    @Test
    fun `a rideable track carries the property the bike layer filters on`() {
        val rideable = track()
        val json = DocGeoJson.build(listOf(rideable), setOf(rideable.sourceRef))

        assertTrue(
            "the bike switch hides the rideable tracks by this property",
            json.contains("\"${AssetGeoJson.BIKE_FRIENDLY_PROPERTY}\":true")
        )
        assertFalse(
            "a walking-only track must not claim it",
            DocGeoJson.build(listOf(track(id = 2))).contains(AssetGeoJson.BIKE_FRIENDLY_PROPERTY)
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
        assertTrue("the rideable tracks have their own casing", DocLayerIds.BIKE_CASING in DocLayerIds.ALL)
        assertTrue(DocLayerIds.BIKE_TRACKS in DocLayerIds.ALL)
        assertTrue("every bike point has a badge layer", DocLayerIds.BIKE_BADGES in DocLayerIds.ALL)
        DocLayerIds.REACHABLE_MARKER_KINDS.forEach { kind ->
            assertTrue(
                "every kind DOC publishes a place as has a layer to be drawn in",
                DocLayerIds.pointOf(kind) in DocLayerIds.ALL
            )
        }
        assertTrue(
            "a waypoint is the walker's own spot; DOC has no marker layer for it",
            DocLayerIds.pointOf(AssetKind.WAYPOINT) !in DocLayerIds.ALL
        )
        assertEquals("and nothing is drawn twice", DocLayerIds.ALL.size, DocLayerIds.ALL.toSet().size)
    }
}
