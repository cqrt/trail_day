package nz.mckenzie.trailday.domain.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DOC's answers read as the app's own record of them.
 *
 * The answers below are **the shape the live services give**, field names and all - copied from what
 * the four actually return rather than made up, because that is the whole promise of this reader: a hut
 * keeps whether it is bookable and what is there, a bike location keeps how hard it is and how long it
 * takes, and neither is thrown away on the way in.
 *
 * The two halves worth holding still are that a **line** and a **place** both read (the tracks are
 * drawn on the ground and the other three are single points), and that a fact DOC sends is kept whether
 * or not this build has a word for it.
 */
class DocJsonTest {

    private val tracksAnswer = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","geometry":{"type":"LineString","coordinates":[
              [168.349159886,-46.6222429467],[168.349244881,-46.622198892]
          ]},"properties":{"OBJECTID":35239046,"TechObjectName":"Glory Tk","SubObjectType":"Walking Track","FlocID":"BLUFFHIL-TK02","CharName7":"WEB_TRACK_TYPE","CharValue7":"One way"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[168.3,-46.6]},
           "properties":{"OBJECTID":1,"TechObjectName":"A gate","SubObjectType":"Short Walk"}}
        ]}
    """.trimIndent()

    /** A hut as `DOC_Huts` publishes one, with its own field names. */
    private val hutsAnswer = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","geometry":{"type":"Point","coordinates":[172.65,-41.62]},
           "properties":{"OBJECTID":218,"name":"Angelus Hut","place":"Nelson Lakes National Park",
             "region":"Nelson/Marlborough","bookable":"Yes","facilities":null,
             "hasAlerts":"Refer to DOC website for current alerts",
             "introductionThumbnail":"https://www.doc.govt.nz/thumbs/large/link/2fde126d.aspx",
             "staticLink":"https://www.doc.govt.nz/link/3e768c88.aspx"}}
        ]}
    """.trimIndent()

    @Test
    fun `a line reads as a line, with its id, name, kind and length`() {
        val items = DocJson.parse(DocSource.TRACKS, tracksAnswer)

        assertEquals("the point feature is not a track", 1, items.size)
        val track = items.single()
        assertEquals(35239046L, track.objectId)
        assertEquals("Glory Tk", track.name)
        assertEquals("Walking Track", track.kind)
        assertEquals(
            "the track's type is found among the pivoted characteristics, by its name",
            "One way",
            track.trackType
        )
        assertEquals("the site key is carried, for the imported place", "BLUFFHIL-TK02", track.flocId)
        assertTrue("its own arithmetic over the vertices", track.lengthM > 0.0)
        assertEquals(2, track.pointCount)
        assertNull("a line is not a place", track.point)
    }

    @Test
    fun `a place reads as one coordinate, with what DOC says about it`() {
        val hut = DocJson.parse(DocSource.HUTS, hutsAnswer).single()

        assertEquals(DocSource.HUTS, hut.source)
        assertEquals(218L, hut.objectId)
        assertEquals("Angelus Hut", hut.name)
        assertEquals("where it is, as a place rather than a line", 1, hut.pointCount)
        assertEquals(-41.62, hut.point!!.lat, 1e-9)
        assertEquals(172.65, hut.point!!.lng, 1e-9)
        assertEquals("a place has no length", 0.0, hut.lengthM, 1e-9)

        assertEquals("Nelson Lakes National Park", hut.place)
        assertEquals("Nelson/Marlborough", hut.region)
        assertEquals("in DOC's own word, not turned into a boolean", "Yes", hut.bookable)
        assertNull("DOC says nothing about a hut with no facilities line", hut.facilities)
        assertTrue(hut.pictureUrl!!.startsWith("https://www.doc.govt.nz/"))
        assertTrue(hut.link!!.startsWith("https://www.doc.govt.nz/"))
    }

    @Test
    fun `a fact DOC sends is kept even when the app has no word for it`() {
        val hut = DocJson.parse(DocSource.HUTS, hutsAnswer).single()

        // The row is kept as it arrived, so a build that learns to read another field - or DOC that adds
        // one - needs no new reading to carry it. The id is not a fact about the place and does not
        // travel twice.
        assertEquals("Yes", hut.facts["bookable"])
        assertEquals("Nelson Lakes National Park", hut.facts["place"])
        assertNull("the id is the item's own property", hut.facts["OBJECTID"])
    }

    @Test
    fun `the words DOC sends with a link in them arrive as words`() {
        // A campsite's dog rules arrive as a sentence with a link inside it, and the live service
        // truncates mid-tag: the words are what a card draws, and half a tag is not words.
        val answer = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point","coordinates":[172.0,-41.0]},
               "properties":{"OBJECTID":7,"name":"Kiosk Creek Campsite","place":"Fiordland National Park",
                 "campsiteCategory":"Standard","numberOfPoweredSites":0,"numberOfUnpoweredSites":10,
                 "bookable":"Yes","free":null,
                 "facilities":"Fire pit/place for campfires, Non-powered/tent sites, Toilets - non-flush",
                 "dogsAllowed":"No dogs. <a href=\"https://www.doc.govt.nz/pets\">Pets on conservation land</a> <a "}}
            ]}
        """.trimIndent()

        val camp = DocJson.parse(DocSource.CAMPSITES, answer).single()

        assertEquals("Standard", camp.kind)
        assertEquals("Yes", camp.bookable)
        assertEquals(10, camp.unpoweredSites)
        assertEquals(0, camp.poweredSites)
        assertEquals(
            "the sentence, without the markup or the half tag",
            "No dogs. Pets on conservation land",
            camp.dogsAllowed
        )
        assertEquals(
            "Fire pit/place for campfires, Non-powered/tent sites, Toilets - non-flush",
            camp.facilities
        )
    }

    @Test
    fun `a bike location keeps how hard it is and how long it takes`() {
        val answer = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point","coordinates":[172.0,-41.0]},
               "properties":{"OBJECTID":3,"name":"Globe Hill Track","difficulty":"Intermediate",
                 "bikingTime":"3 hr","hasAlerts":"Refer to DOC website for current alerts",
                 "mountainBikingTrackWebPage":"https://www.doc.govt.nz/link/8389b924.aspx"}}
            ]}
        """.trimIndent()

        val bike = DocJson.parse(DocSource.BIKES, answer).single()

        assertEquals("Intermediate", bike.difficulty)
        assertEquals("3 hr", bike.bikingTime)
        assertNull("a bike location is not a walk's kind", bike.kind)
        assertTrue("a bike location has its own page", bike.link!!.contains("doc.govt.nz"))
        assertNull("and no facilities line", bike.facilities)
    }

    @Test
    fun `a hut and a track with the same number are two different things`() {
        // DOC numbers its features per service, so the number alone cannot identify one - which is why
        // the reference names the source, and why a cache row is keyed by both.
        val track = DocJson.parse(DocSource.TRACKS, tracksAnswer).first()
        val hut = DocJson.parse(DocSource.HUTS, hutsAnswer).single()

        assertEquals("doc-tracks:35239046", track.sourceRef)
        assertEquals("doc-huts:218", hut.sourceRef)
        assertNotEquals(
            "two services' numbers must not be able to collide",
            docSourceRef(DocSource.TRACKS, 218L),
            docSourceRef(DocSource.HUTS, 218L)
        )
    }

    @Test
    fun `the type is read from whichever slot holds it, and a blank one is no type`() {
        // The characteristic's slot varies by row, which is the whole reason it is found by its name.
        val text = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"LineString","coordinates":[[168.0,-46.0],[168.1,-46.1]]},
               "properties":{"OBJECTID":1,"CharName2":"WEB_TRACK_TYPE","CharValue2":"Loop"}},
              {"type":"Feature","geometry":{"type":"LineString","coordinates":[[168.2,-46.2],[168.3,-46.3]]},
               "properties":{"OBJECTID":2,"CharName4":"WEB_TRACK_TYPE","CharValue4":""}},
              {"type":"Feature","geometry":{"type":"LineString","coordinates":[[168.4,-46.4],[168.5,-46.5]]},
               "properties":{"OBJECTID":3,"CharName4":"WEB_DOGS_ALLOWED","CharValue4":"Dogs on a leash only"}}
            ]}
        """.trimIndent()

        val tracks = DocJson.parse(DocSource.TRACKS, text)
        assertEquals("Loop", tracks[0].trackType)
        assertNull("a blank value is no type", tracks[1].trackType)
        assertNull("a track with no such characteristic has no type", tracks[2].trackType)
    }

    @Test
    fun `a feature with no name is named after DOC's number for it`() {
        val text = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"LineString","coordinates":[[168.0,-46.0],[168.1,-46.1]]},
               "properties":{"OBJECTID":42,"SubObjectType":"Route"}}
            ]}
        """.trimIndent()

        val track = DocJson.parse(DocSource.TRACKS, text).single()
        assertEquals("DOC track 42", track.name)
        assertEquals("Route", track.kind)
    }

    @Test
    fun `an answer with nothing in it is nothing, not a failure`() {
        assertTrue(DocJson.parse(DocSource.TRACKS, """{"type":"FeatureCollection","features":[]}""").isEmpty())
    }

    @Test
    fun `a line with one vertex is not a line, and a place with no coordinate is nowhere`() {
        val answer = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"LineString","coordinates":[[168.0,-46.0]]},
               "properties":{"OBJECTID":1,"TechObjectName":"Not a track"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[]},
               "properties":{"OBJECTID":2,"TechObjectName":"Nowhere"}}
            ]}
        """.trimIndent()

        assertTrue("nothing that cannot be used is kept", DocJson.parse(DocSource.TRACKS, answer).isEmpty())
        assertTrue(DocJson.parse(DocSource.HUTS, answer).isEmpty())
    }

    @Test
    fun `a count is read from the number DOC answers with, and nothing else is a count`() {
        assertEquals(412, DocJson.parseCount("""{"count":412}"""))
        assertEquals("zero is a real answer", 0, DocJson.parseCount("""{"count":0}"""))

        // An error answers with an error object instead, and a body that is neither is not a number to
        // put in front of a walker: null says "not counted" rather than "none".
        assertNull(DocJson.parseCount("""{"error":{"code":400,"message":"Invalid URL"}}"""))
        assertNull(DocJson.parseCount("""{"type":"FeatureCollection","features":[]}"""))
        assertNull(DocJson.parseCount("not json at all"))
    }
}
