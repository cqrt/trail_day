package nz.mckenzie.trailday.domain.doc

import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The URL that asks one of DOC's services a question.
 *
 * The choices worth holding still are that the format asked for is the one the app already reads
 * (`f=geojson`, `outSR=4326`), that a name goes into the `where` clause as a value rather than as SQL -
 * a quote in a name must not be able to end the clause - and that **each service is asked in its own
 * terms**: the name is in `TechObjectName` for the tracks and in `name` for the other three, and a
 * clause written for one service searches the wrong field in the others.
 */
class DocUrlTest {

    @Test
    fun `no name is everything, and a name is a case-insensitive contains`() {
        assertEquals("1=1", DocUrl.where(DocSource.TRACKS, ""))
        assertEquals("1=1", DocUrl.where(DocSource.TRACKS, "   "))
        assertEquals(
            "UPPER(TechObjectName) LIKE UPPER('%Ocean%')",
            DocUrl.where(DocSource.TRACKS, "Ocean")
        )
    }

    @Test
    fun `a hut is searched in the field DOC keeps a hut's name in`() {
        // `TechObjectName` is the tracks service's own field and the other three do not have it, so a
        // clause written once for all four would find nothing on three of them - and a search that finds
        // nothing looks exactly like a place DOC has never heard of.
        assertEquals("UPPER(name) LIKE UPPER('%Angelus%')", DocUrl.where(DocSource.HUTS, "Angelus"))
        assertEquals("UPPER(name) LIKE UPPER('%Angelus%')", DocUrl.where(DocSource.CAMPSITES, "Angelus"))
        assertEquals("UPPER(name) LIKE UPPER('%Angelus%')", DocUrl.where(DocSource.BIKES, "Angelus"))
    }

    @Test
    fun `a quote in a name is doubled, so it searches rather than ending the clause`() {
        assertEquals(
            "UPPER(TechObjectName) LIKE UPPER('%O''Hara%')",
            DocUrl.where(DocSource.TRACKS, "O'Hara")
        )
    }

    @Test
    fun `each service is asked at its own address, on the one host they share`() {
        DocSource.entries.forEach { source ->
            val url = DocUrl.of(DocQuery(source = source))
            assertTrue(
                "$source should be asked at its own service: $url",
                url.startsWith("${DocSource.HOST}/${source.service}/${DocSource.LAYER}/query?")
            )
        }
        assertEquals(4, DocSource.entries.map { it.service }.toSet().size)
    }

    @Test
    fun `each service is asked for its own fields, and for the key and the name it uses`() {
        DocSource.entries.forEach { source ->
            val asked = DocUrl.of(DocQuery(source = source))
                .substringAfter("outFields=")
                .substringBefore("&")
            val fields = java.net.URLDecoder.decode(asked, "UTF-8").split(",")

            assertTrue("$source needs the key DOC numbers it with", DocSource.OBJECT_ID_FIELD in fields)
            assertTrue("$source's own name field", source.nameField in fields)
            source.fields.forEach { fact ->
                assertTrue("$source says what its $fact is", fact in fields)
            }
        }
    }

    @Test
    fun `a name search asks for GeoJSON in WGS84 and no geometry filter`() {
        val url = DocUrl.of(DocQuery(source = DocSource.HUTS, nameContains = "Angelus"))

        assertTrue("the format the phone reads", url.contains("f=geojson"))
        assertTrue("ArcGIS reprojects for us", url.contains("outSR=4326"))
        assertFalse("no envelope when there is no place", url.contains("geometry="))
    }

    @Test
    fun `a near-me search carries an envelope in WGS84`() {
        val url = DocUrl.of(DocQuery(source = DocSource.TRACKS, near = GeoPoint(lat = -46.6, lng = 168.35)))

        assertTrue(url.contains("geometry="))
        assertTrue(url.contains("geometryType=esriGeometryEnvelope"))
        assertTrue(url.contains("inSR=4326"))
        assertTrue(url.contains("spatialRel=esriSpatialRelIntersects"))
        assertTrue("the request is capped", url.contains("resultRecordCount="))
    }

    @Test
    fun `an envelope is the near square, one degree at the equator`() {
        assertEquals(
            "-1.0000000,-1.0000000,1.0000000,1.0000000",
            DocUrl.envelope(GeoPoint(lat = 0.0, lng = 0.0), radiusKm = 111.32)
        )
    }

    @Test
    fun `a map's own view is the envelope, west and south first`() {
        assertEquals(
            "168.1000000,-46.7000000,168.6000000,-46.5000000",
            DocUrl.envelope(DocBounds(minLat = -46.7, minLng = 168.1, maxLat = -46.5, maxLng = 168.6))
        )
    }

    @Test
    fun `a map-view search carries an envelope and needs no place of its own`() {
        val url = DocUrl.of(
            DocQuery(
                source = DocSource.TRACKS,
                bounds = DocBounds(minLat = -46.7, minLng = 168.1, maxLat = -46.5, maxLng = 168.6)
            )
        )

        assertTrue(url.contains("geometry=168.1000000%2C-46.7000000%2C168.6000000%2C-46.5000000"))
        assertTrue(url.contains("geometryType=esriGeometryEnvelope"))
    }

    @Test
    fun `a page beyond the first asks from where the last ended`() {
        val url = DocUrl.of(
            DocQuery(
                source = DocSource.HUTS,
                bounds = DocBounds(minLat = -46.7, minLng = 168.1, maxLat = -46.5, maxLng = 168.6),
                limit = 2000,
                offset = 2000
            )
        )

        assertTrue(url.contains("resultOffset=2000"))
        assertTrue(url.contains("resultRecordCount=2000"))
        // Ordered by DOC's own number, so two pages of one search cannot overlap or skip a row.
        assertTrue(url.contains("orderByFields=${DocSource.OBJECT_ID_FIELD}"))
    }

    @Test
    fun `the same radius reaches further in longitude the further south you are`() {
        // A degree of longitude is shorter at this latitude, so the box is wider east-west.
        val envelope = DocUrl.envelope(GeoPoint(lat = -46.6, lng = 168.35), radiusKm = 25.0)
        val (minLng, minLat, maxLng, maxLat) = envelope.split(",").map { it.toDouble() }

        assertTrue("a box around the phone", minLng < 168.35 && maxLng > 168.35)
        assertTrue(minLat < -46.6 && maxLat > -46.6)
        assertTrue("wider than tall, far south", (maxLng - minLng) > (maxLat - minLat))
    }

    @Test
    fun `a count asks the same question with the records left behind`() {
        val query = DocQuery(
            source = DocSource.HUTS,
            bounds = DocBounds(minLat = -46.7, minLng = 168.1, maxLat = -46.5, maxLng = 168.6)
        )

        val url = DocUrl.countOf(query)

        assertTrue("it asks for a number", url.contains("returnCountOnly=true"))
        assertTrue("and not for the records themselves", !url.contains("returnGeometry"))
        assertTrue("or their fields", !url.contains("outFields"))
        assertTrue("over the same box", url.contains("geometryType=esriGeometryEnvelope"))
        assertTrue("against the service the query names", url.contains(DocSource.HUTS.service))
        assertTrue("in a shape this build can read", url.contains("f=json"))
    }

    @Test
    fun `a count can be narrowed to the records with something the app cares about`() {
        val query = DocQuery(source = DocSource.CAMPSITES)

        val url = DocUrl.countOf(query, DocSource.CAMPSITES.pictureWhere()!!)

        assertTrue("the narrowed question goes in the where clause", url.contains("introductionThumbnail"))
        assertTrue("and the count is still a count", url.contains("returnCountOnly=true"))
    }
}
