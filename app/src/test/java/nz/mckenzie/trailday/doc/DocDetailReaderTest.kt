package nz.mckenzie.trailday.doc

import nz.mckenzie.trailday.domain.doc.DocCompanionJson
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.doc.DocTrackCompanion
import nz.mckenzie.trailday.domain.doc.DocTrackCompanions
import nz.mckenzie.trailday.domain.doc.withCompanion
import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The small pieces the DOC detail page leans on, kept still without the network. */
class DocDetailReaderTest {

    private fun forksFlat() = DocItem(
        source = DocSource.TRACKS,
        objectId = 35239127,
        name = "Forks Flat Tk",
        paths = listOf(
            listOf(
                GeoPoint(-46.0, 168.0),
                GeoPoint(-46.01, 168.01)
            )
        ),
        facts = mapOf(
            "SubObjectType" to "Walking Track",
            "CharName2" to "USER_STATUS",
            "CharValue2" to "OPEN",
            "CharName4" to "WEB_WALKING_DURATION",
            "CharValue4" to "1-4 hours",
            "CharName6" to "WEB_DOGS_ALLOWED",
            "CharValue6" to "Dogs on a leash only",
            "CharName7" to "WEB_TRACK_TYPE",
            "CharValue7" to "Loop"
        )
    )

    @Test
    fun `track characteristics are read from the slots they arrived in`() {
        val track = forksFlat()

        assertEquals("1-4 hours", track.walkingDuration)
        assertEquals("Dogs on a leash only", track.dogsAllowed)
        assertEquals("OPEN", track.status)
        assertEquals("Loop", track.trackType)
    }

    @Test
    fun `the companion layer fills the website facts without replacing the line facts`() {
        val track = forksFlat().withCompanion(
            DocTrackCompanion(
                introduction = "A gentle walk through the Hokonui Forest.",
                difficulty = "Easy",
                completionTime = "1 hour",
                webPage = "https://www.doc.govt.nz/link/4e69d472da1a41ecb79e7f9557534e3b.aspx"
            )
        )

        assertEquals("A gentle walk through the Hokonui Forest.", track.introduction)
        assertEquals("Easy", track.difficulty)
        assertEquals("1 hour", track.completionTime)
        assertEquals("Loop", track.trackType)
        assertEquals(
            "https://www.doc.govt.nz/link/4e69d472da1a41ecb79e7f9557534e3b.aspx",
            track.link
        )
    }

    @Test
    fun `the companion search drops a trailing kind from the name`() {
        assertEquals("Forks Flat", DocWebDetails.searchStem("Forks Flat Tk"))
        assertEquals("Forks Flat", DocWebDetails.searchStem("Forks Flat Track"))
        assertEquals("Routeburn", DocWebDetails.searchStem("Routeburn Track"))
        assertEquals("Mount Robert", DocWebDetails.searchStem("Mount Robert"))
    }

    @Test
    fun `the alert guid comes out of both compact and dashed page links`() {
        assertEquals(
            "4e69d472-da1a-41ec-b79e-7f9557534e3b",
            DocWebDetails.alertGuid(
                "https://www.doc.govt.nz/link/4e69d472da1a41ecb79e7f9557534e3b.aspx"
            )
        )
        assertEquals(
            "4e69d472-da1a-41ec-b79e-7f9557534e3b",
            DocWebDetails.alertGuid(
                "https://www.doc.govt.nz/link/4e69d472-da1a-41ec-b79e-7f9557534e3b.aspx"
            )
        )
        assertNull(DocWebDetails.alertGuid(null))
        assertNull(DocWebDetails.alertGuid("https://www.doc.govt.nz/no-guid-here/"))
    }

    @Test
    fun `the website layer is read as points with its summary fields`() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point","coordinates":[168.0,-46.0]},
               "properties":{"name":"Forks Flat Track","introduction":"A gentle walk.",
                 "difficulty":"Easy","completionTime":"1 hour",
                 "walkingAndTrampingWebPage":"https://www.doc.govt.nz/link/example.aspx",
                 "introductionThumbnail":"https://www.doc.govt.nz/images/example.jpg"}}
            ]}
        """.trimIndent()

        val companions = DocCompanionJson.parse(json)
        val companion = DocTrackCompanions.bestFor(forksFlat(), companions)

        assertEquals("Forks Flat Track", companion?.name)
        assertEquals("A gentle walk.", companion?.introduction)
        assertEquals("Easy", companion?.difficulty)
        assertEquals("1 hour", companion?.completionTime)
        assertEquals("https://www.doc.govt.nz/images/example.jpg", companion?.thumbnail)
    }
}
