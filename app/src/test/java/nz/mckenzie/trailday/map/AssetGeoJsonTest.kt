package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetGeoJsonTest {

    private val wellingtonLine = listOf(
        GeoPoint(-41.2865, 174.7762),
        GeoPoint(-41.2866, 174.7763)
    )

    private fun line(
        name: String = "Track 4",
        color: String = AssetColors.TRACK_KIND,
        kind: AssetKind = AssetKind.TRACK,
        shape: AssetShape = AssetShape.LINE,
        id: Long = 7L,
        points: List<GeoPoint> = wellingtonLine,
        sideTracks: List<List<GeoPoint>> = emptyList()
    ) = AssetLine(
        assetId = id,
        name = name,
        colorHex = color,
        points = points,
        kind = kind,
        shape = shape,
        sideTracks = sideTracks
    )

    @Test
    fun `no tracks produces an empty feature collection`() {
        assertEquals("{\"type\":\"FeatureCollection\",\"features\":[]}", AssetGeoJson.build(emptyList()))
    }

    @Test
    fun `tracks with fewer than two points are not drawn`() {
        val single = AssetLine(1L, "Stub", AssetColors.TRACK_KIND, listOf(GeoPoint(-41.0, 174.0)))

        assertFalse(AssetGeoJson.build(listOf(single)).contains("Feature\",\"propert"))
    }

    @Test
    fun `a track becomes a linestring feature with its id and colour`() {
        val json = AssetGeoJson.build(listOf(line()))

        assertTrue(json.contains("\"type\":\"LineString\""))
        assertTrue(json.contains("\"id\":7"))
        assertTrue(json.contains("\"stroke\":\"${AssetColors.TRACK_KIND}\""))
        assertTrue(json.contains("\"name\":\"Track 4\""))
    }

    @Test
    fun `coordinates are written longitude first as geojson requires`() {
        val json = AssetGeoJson.build(listOf(line()))

        assertTrue(json.contains("[174.7762000,-41.2865000]"))
    }

    @Test
    fun `both points of the line are present - and the named stretch repeats them`() {
        val json = AssetGeoJson.build(listOf(line()))

        assertEquals(
            "the line's own two points, and the same pair again for the stretch the name is written on",
            4,
            Regex("\\[-?\\d+\\.\\d+,-?\\d+\\.\\d+\\]").findAll(json).count()
        )
    }

    @Test
    fun `multiple tracks are comma separated features`() {
        // Nothing is named here: a named track draws the stretch its name goes on as well, and this test
        // is about the commas between features.
        val json = AssetGeoJson.build(
            listOf(line(name = ""), line(name = "", color = AssetColors.RECORDED))
        )

        assertEquals(2, Regex("\"type\":\"Feature\",").findAll(json).count())
    }

    @Test
    fun `quotes in track names are escaped`() {
        val json = AssetGeoJson.build(listOf(line(name = "Block \"4\" \\ east")))

        assertTrue(json.contains("Block \\\"4\\\" \\\\ east"))
    }

    @Test
    fun `a track carries its name once, on a stretch of its own line`() {
        val track = line(
            name = "Woolshed block track",
            points = listOf(GeoPoint(-41.0, 174.0), GeoPoint(-41.0, 174.001), GeoPoint(-41.0, 174.002))
        )

        val json = AssetGeoJson.build(listOf(track))

        assertEquals(
            "one stretch carries the name - not one per point, and not one per drawn piece",
            1,
            Regex("\"carriesName\":true").findAll(json).count()
        )
        assertEquals(
            "and the track's own line is still drawn",
            2,
            Regex("\"type\":\"Feature\",").findAll(json).count()
        )
    }

    @Test
    fun `only a track is named beside the line`() {
        val bikeTrack = line(name = "Estuary bike track", kind = AssetKind.BIKE_TRACK)
        val place = AssetLine(
            assetId = 9L,
            name = "Tin Hut",
            colorHex = AssetColors.HUT_KIND,
            points = listOf(GeoPoint(-41.0, 174.0)),
            kind = AssetKind.HUT,
            shape = AssetShape.POINT
        )

        listOf(bikeTrack, place).forEach { place ->
            assertFalse(
                "a ${place.kind} has no name written beside it",
                AssetGeoJson.build(listOf(place)).contains("carriesName")
            )
        }
    }

    @Test
    fun `a line with no name - one being drawn - carries none`() {
        val preview = line(name = "", kind = AssetKind.TRACK)

        assertFalse(AssetGeoJson.build(listOf(preview)).contains("carriesName"))
    }

    @Test
    fun `a half-recorded track is still named once, from the whole track`() {
        val partSprayed = line(
            name = "Pump paddock track",
            points = listOf(GeoPoint(-41.0, 174.0), GeoPoint(-41.0, 174.002))
        ).copy(
            stretches = listOf(
                AssetStretch(
                    colorHex = AssetColors.TRACK_KIND,
                    points = listOf(GeoPoint(-41.0, 174.0), GeoPoint(-41.0, 174.001))
                ),
                AssetStretch(
                    colorHex = AssetColors.RECORDED,
                    points = listOf(GeoPoint(-41.0, 174.001), GeoPoint(-41.0, 174.002))
                )
            )
        )

        val json = AssetGeoJson.build(listOf(partSprayed))

        assertEquals(
            "the stretch of the name is drawn as well as the two halves of the job",
            3,
            Regex("\"type\":\"Feature\",").findAll(json).count()
        )
        assertEquals(
            "and the name itself is written once",
            1,
            Regex("\"carriesName\":true").findAll(json).count()
        )
    }

    @Test
    fun `a feature says what it is, so the map can draw each kind differently`() {
        val json = AssetGeoJson.build(listOf(line(kind = AssetKind.BIKE_TRACK)))

        assertTrue(json.contains("\"kind\":\"BIKE_TRACK\""))
        assertTrue(json.contains("\"shape\":\"LINE\""))
    }

    @Test
    fun `a place is a point feature, not a line with one vertex in it`() {
        val place = AssetLine(
            assetId = 9L,
            name = "Tin Hut",
            colorHex = AssetColors.HUT_KIND,
            points = listOf(GeoPoint(-41.2865, 174.7762)),
            kind = AssetKind.HUT,
            shape = AssetShape.POINT
        )

        val json = AssetGeoJson.build(listOf(place))

        assertTrue("a place is drawn where it is: $json", json.contains("\"type\":\"Point\""))
        assertTrue(json.contains("[174.7762000,-41.2865000]"))
        assertTrue(json.contains("\"shape\":\"POINT\""))
    }

    @Test
    fun `a place needs only one point, but a line still needs two`() {
        val onePoint = listOf(GeoPoint(-41.0, 174.0))
        val place = AssetLine(9L, "Shelter", AssetColors.TRACK_KIND, onePoint, shape = AssetShape.POINT)
        val stub = AssetLine(1L, "Stub", AssetColors.TRACK_KIND, onePoint, shape = AssetShape.LINE)

        assertTrue(AssetGeoJson.build(listOf(place)).contains("\"type\":\"Point\""))
        assertFalse(
            "a one-point line is still not a line",
            AssetGeoJson.build(listOf(stub)).contains("\"type\":\"Feature\"")
        )
    }

    @Test
    fun `a place carries the marker it is to be drawn with, in its kind's colour`() {
        val place = AssetLine(
            assetId = 9L,
            name = "Tin Hut",
            colorHex = AssetColors.HUT_KIND,
            points = listOf(GeoPoint(-41.2865, 174.7762)),
            kind = AssetKind.HUT,
            shape = AssetShape.POINT
        )

        val json = AssetGeoJson.build(listOf(place))

        assertTrue(
            "a place is drawn with its kind's own picture: $json",
            json.contains(
                "\"${AssetGeoJson.ICON_PROPERTY}\":\"" +
                    "${PlaceIcons.imageName(AssetKind.HUT)}\""
            )
        )
    }

    @Test
    fun `a track that comes back to its own start is still a line`() {
        val loop = listOf(
            GeoPoint(-41.2865, 174.7762),
            GeoPoint(-41.2866, 174.7763),
            GeoPoint(-41.2867, 174.7762),
            GeoPoint(-41.2865, 174.7762)
        )
        val json = AssetGeoJson.build(listOf(line(points = loop)))

        assertTrue("a track is a line wherever it runs: $json", json.contains("\"type\":\"LineString\""))
        assertFalse("and a loop is never a shape the map fills", json.contains("\"type\":\"Polygon\""))
    }

    @Test
    fun `a line is not given a picture, because its own layer draws it`() {
        val json = AssetGeoJson.build(listOf(line(), line(kind = AssetKind.BIKE_TRACK)))

        assertFalse(
            "only a place is drawn as a picture: $json",
            json.contains(AssetGeoJson.ICON_PROPERTY)
        )
    }

    @Test
    fun `a half-recorded track is two features, one per stretch, opening the same asset`() {
        val halfSprayed = AssetLine(
            assetId = 7L,
            name = "Track 4",
            colorHex = AssetColors.TRACK_KIND,
            points = wellingtonLine,
            stretches = listOf(
                AssetStretch(colorHex = AssetColors.TRACK_KIND, points = wellingtonLine),
                AssetStretch(
                    colorHex = AssetColors.RECORDED,
                    points = listOf(GeoPoint(-41.2867, 174.7764), GeoPoint(-41.2868, 174.7765))
                )
            )
        )

        val json = AssetGeoJson.build(listOf(halfSprayed))

        assertEquals(
            "one feature per stretch, and one for the stretch the name is written on",
            3,
            Regex("\"type\":\"Feature\",").findAll(json).count()
        )
        assertTrue(json.contains("\"stroke\":\"${AssetColors.TRACK_KIND}\""))
        assertTrue(json.contains("\"stroke\":\"${AssetColors.RECORDED}\""))
        assertEquals(
            "every part is the same asset, so a tap on any of them opens it",
            3,
            Regex("\"id\":7").findAll(json).count()
        )
    }

    @Test
    fun `a stretch too short to be a line is dropped, and the whole line drawn instead`() {
        val shortStretch = AssetLine(
            assetId = 7L,
            name = "Track 4",
            colorHex = AssetColors.TRACK_KIND,
            points = wellingtonLine,
            stretches = listOf(
                AssetStretch(colorHex = AssetColors.RECORDED, points = listOf(GeoPoint(-41.2865, 174.7762)))
            )
        )

        val json = AssetGeoJson.build(listOf(shortStretch))

        assertEquals(
            "the track is not lost off the map: drawn whole, with the stretch its name is written on",
            2,
            Regex("\"type\":\"Feature\",").findAll(json).count()
        )
        assertTrue(json.contains("\"stroke\":\"${AssetColors.TRACK_KIND}\""))
        assertFalse("a dot where the line should be is worse than the line", json.contains(AssetColors.RECORDED))
    }

    @Test
    fun `a track with a side track is drawn as one feature per path`() {
        // The map's own rule, and the one the drawing screen's own pixels were read against: the line is
        // a feature, the side track is a feature, and the junction is in both - so a tap on the spur and
        // a tap on the line open the same track.
        val junction = GeoPoint(0.0, 0.0005)
        val json = AssetGeoJson.build(
            listOf(
                line(
                    id = 6L,
                    // Nothing is named here, so what is drawn is the paths alone: the name of a track with
                    // side tracks has a test of its own, below.
                    name = "",
                    points = listOf(GeoPoint(0.0, 0.0), junction, GeoPoint(0.0, 0.001)),
                    sideTracks = listOf(listOf(junction, GeoPoint(0.001, 0.0005)))
                )
            )
        )

        assertEquals("two lines drawn", 2, Regex("\"type\":\"LineString\"").findAll(json).count())
        assertEquals(
            "and both carry the asset's id, so a tap on either opens the track",
            2,
            Regex("\"id\":6").findAll(json).count()
        )
        assertEquals(
            "the junction is a vertex of both features, at the same two numbers: [lng,lat]",
            2,
            Regex(Regex.escape("[0.0005000,0.0000000]")).findAll(json).count()
        )
    }

    @Test
    fun `a track with a side track is named once, not once per path`() {
        val junction = GeoPoint(0.0, 0.0005)
        val json = AssetGeoJson.build(
            listOf(
                line(
                    id = 6L,
                    name = "Pump paddock track",
                    points = listOf(GeoPoint(0.0, 0.0), junction, GeoPoint(0.0, 0.001)),
                    sideTracks = listOf(listOf(junction, GeoPoint(0.001, 0.0005)))
                )
            )
        )

        assertEquals(
            "the line, the side track, and the one stretch carrying the name",
            3,
            Regex("\"type\":\"LineString\"").findAll(json).count()
        )
        assertEquals(
            "the name is written once, and from the whole track rather than from each path",
            1,
            Regex("\"carriesName\":true").findAll(json).count()
        )
    }

    @Test
    fun `a side track with one point is not drawn, because there is no line in it`() {
        val junction = GeoPoint(0.0, 0.0005)
        val json = AssetGeoJson.build(
            listOf(
                line(
                    id = 6L,
                    name = "",
                    points = listOf(GeoPoint(0.0, 0.0), junction),
                    sideTracks = listOf(listOf(junction))
                )
            )
        )

        assertEquals(
            "one line, and the single point is not a second",
            1,
            Regex("\"type\":\"LineString\"").findAll(json).count()
        )
    }
}
