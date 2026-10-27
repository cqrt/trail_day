package nz.mckenzie.trailday.domain.other

import java.net.URLDecoder
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The URL that asks the national tracks dataset a question, and the filter it carries.
 *
 * The choices worth holding still are that the format asked for is the one the app already reads
 * (`f=geojson`, `outSR=4326`), that **the four exclusions are the four that were decided** - DOC's rows,
 * Te Araroa's, the removed ones and the ones nobody may walk on - and that a page can be asked for in a
 * stable order, because an unordered page is how a download caches one track twice and another not at
 * all.
 */
class OtherTracksUrlTest {

    @Test
    fun `the three exclusions are all asked for`() {
        val where = OtherTracksUrl.where()

        assertTrue("DOC's rows", where.contains("NOT (DataSupplier LIKE '%DOC%')"))
        assertTrue("the removed ones", where.contains("Status <> 'Decommisioned'"))
        assertTrue("the ones nobody may walk on", where.contains("Walk <> 0"))
    }

    @Test
    fun `Te Araroa's rows are kept, because a council's walkway travels with them`() {
        // The exclusion was there once, on the argument that the app carries the Trust's own route as a
        // bundled layer. What that cost was every local track a council and the Trust both publish - one
        // row naming both, gone - so a resident's own walkway went missing because a national trail ran
        // along it. A duplicated line where both layers are on is the cheaper mistake.
        assertFalse(
            "Te Araroa must not be excluded",
            OtherTracksUrl.where().contains("Te Araroa")
        )
    }

    @Test
    fun `the status is asked for in the dataset's own spelling, which is misspelt`() {
        // "Decommisioned" is misspelt in their coded value domain. A clause written with the app's own
        // spelling would match nothing and would quietly put 284 removed tracks back on the map.
        assertTrue(OtherTracksUrl.where().contains("'Decommisioned'"))
        assertFalse(OtherTracksUrl.where().contains("'Decommissioned'"))
    }

    @Test
    fun `no name is the exclusions alone, and a name is added to them`() {
        assertEquals(OtherTracksUrl.EXCLUSIONS, OtherTracksUrl.where(""))
        assertEquals(OtherTracksUrl.EXCLUSIONS, OtherTracksUrl.where("   "))
        assertEquals(
            "${OtherTracksUrl.EXCLUSIONS} AND UPPER(Name) LIKE UPPER('%Ocean%')",
            OtherTracksUrl.where("Ocean")
        )
    }

    @Test
    fun `a quote in a name is doubled, so it searches rather than ending the clause`() {
        assertTrue(OtherTracksUrl.where("O'Hara").endsWith("UPPER('%O''Hara%')"))
    }

    @Test
    fun `a search asks for GeoJSON in WGS84, for the key, and for no box when there is none`() {
        val url = OtherTracksUrl.of(OtherTracksQuery(nameContains = "Southern"))

        assertTrue("the service's own address", url.startsWith("${OtherTracksUrl.serviceUrl}?"))
        assertTrue("the format the phone reads", url.contains("f=geojson"))
        assertTrue("ArcGIS reprojects for us", url.contains("outSR=4326"))
        assertFalse("no envelope without a box", url.contains("geometry="))
        assertTrue("the key the cache uses", url.contains(OtherTracksUrl.GLOBAL_ID_FIELD))
        assertEquals(OtherTracksUrl.EXCLUSIONS + " AND UPPER(Name) LIKE UPPER('%Southern%')", whereOf(url))
    }

    @Test
    fun `a box is an envelope in WGS84, west and south first`() {
        val url = OtherTracksUrl.of(OtherTracksQuery(bounds = WELLINGTON))

        assertTrue(url.contains("geometryType=esriGeometryEnvelope"))
        assertTrue(url.contains("inSR=4326"))
        assertTrue(url.contains("spatialRel=esriSpatialRelIntersects"))
        assertTrue(
            "west, south, east, north",
            url.contains("geometry=173.1000000%2C-41.4000000%2C173.4000000%2C-41.2000000")
        )
    }

    @Test
    fun `a page beyond the first asks from where the last ended, in a stable order`() {
        val url = OtherTracksUrl.of(OtherTracksQuery(limit = 2000, offset = 2000))

        assertTrue(url.contains("resultOffset=2000"))
        assertTrue(url.contains("resultRecordCount=2000"))
        // Ordered by the service's own counter: the cache is keyed by GlobalID, but the order a page is
        // asked in has to be one the service can page by.
        assertTrue(url.contains("orderByFields=${OtherTracksUrl.OBJECT_ID_FIELD}"))
    }

    @Test
    fun `no request can ask for more than the service will answer with`() {
        val url = OtherTracksUrl.of(OtherTracksQuery(limit = 50_000))

        assertTrue(url.contains("resultRecordCount=${OtherTracksUrl.MAX_RECORD_COUNT}"))
    }

    @Test
    fun `the fields asked for are the ones a row and a page have words for`() {
        val url = OtherTracksUrl.of(OtherTracksQuery())
        val asked = URLDecoder.decode(url.substringAfter("outFields=").substringBefore("&"), "UTF-8").split(",")

        assertTrue(OtherTracksUrl.GLOBAL_ID_FIELD in asked)
        listOf(
            "Name", "Locality", "LocalAuthority", "DataSupplier", "ManagedBy",
            "Walk", "Bike", "DogAccess", "MobilityAccess",
            "ConditionsOfUse", "Description", "Status", "WebLink", "ImageLink"
        ).forEach { field ->
            assertTrue("a row says what its $field is", field in asked)
        }
    }

    @Test
    fun `a count asks the same question with the records left behind`() {
        val url = OtherTracksUrl.countOf(OtherTracksQuery(bounds = WELLINGTON))

        assertTrue("it asks for a number", url.contains("returnCountOnly=true"))
        assertTrue("in a shape this build can read", url.contains("f=json"))
        assertFalse("and not for the records themselves", url.contains("outFields"))
        assertFalse("nor their geometry", url.contains("returnGeometry"))
        assertTrue("over the same box", url.contains("geometryType=esriGeometryEnvelope"))
        assertEquals("and with the same exclusions", OtherTracksUrl.EXCLUSIONS, whereOf(url))
    }

    private fun whereOf(url: String): String =
        URLDecoder.decode(url.substringAfter("where=").substringBefore("&"), "UTF-8")

    private val WELLINGTON =
        LatLngBounds(minLat = -41.4, minLng = 173.1, maxLat = -41.2, maxLng = 173.4)
}
