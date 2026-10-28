package nz.mckenzie.trailday.domain.other

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the national tracks dataset answered, read into the app's own record of a row.
 *
 * The choices worth holding still are that a row is keyed by its `GlobalID` and that the key does not
 * then travel twice as a fact, that a row the dataset named nothing is named by **its locality** rather
 * than by the dataset's own placeholder, and that a row which cannot be read - no key, or no line - is
 * dropped rather than invented.
 */
class OtherTracksJsonTest {

    @Test
    fun `a line is read with its key, its name and its facts`() {
        val tracks = OtherTracksJson.parse(feature())

        assertEquals(1, tracks.size)
        assertEquals("abc-123", tracks[0].globalId)
        assertEquals("Southern Walkway", tracks[0].name)
        assertEquals(1, tracks[0].paths.size)
        assertEquals(2, tracks[0].paths[0].size)
        assertEquals("Wellington City Council", tracks[0].facts["DataSupplier"])
        assertEquals("a number on the wire arrives as text", "3", tracks[0].facts["Walk"])
        assertTrue("and the line has a length", tracks[0].lengthM > 0.0)
    }

    @Test
    fun `the key is not also kept as a fact`() {
        // The id is a field like any other on the wire and is not a fact about the track, so it does not
        // travel twice: once as the key and once as something the page might show.
        assertNull(OtherTracksJson.parse(feature())[0].facts[OtherTracksUrl.GLOBAL_ID_FIELD])
    }

    @Test
    fun `a row with no key is dropped, because nothing could ever recognise it again`() {
        assertTrue(OtherTracksJson.parse(feature(globalId = null)).isEmpty())
        assertTrue(OtherTracksJson.parse(feature(globalId = "  ")).isEmpty())
    }

    @Test
    fun `a line of one vertex is not a line`() {
        val onePoint = """{"type":"LineString","coordinates":[[173.1,-41.3]]}"""

        assertTrue(OtherTracksJson.parse(feature(geometry = onePoint)).isEmpty())
    }

    @Test
    fun `each of a row's paths is kept apart`() {
        // A service that publishes a track in pieces means it. Joining paths that do not meet draws a
        // line across whatever is between them, and counts metres the track does not have.
        val twoPaths =
            """{"type":"MultiLineString","coordinates":[[[173.1,-41.3],[173.2,-41.4]],[[173.5,-41.5],[173.6,-41.6]]]}"""

        val track = OtherTracksJson.parse(feature(geometry = twoPaths)).single()

        assertEquals(2, track.paths.size)
    }

    @Test
    fun `a row the dataset gave no name is dropped, locality or not`() {
        // It used to be renamed after its locality, so a list could at least tell one from another. A row
        // with no name is not a track a walker can ask for, recognise, or say out loud - and if a council
        // names one next month it comes back.
        assertEquals(
            0,
            OtherTracksJson.parse(feature(name = "Unnamed Track", locality = "Mount Victoria")).size
        )
    }

    @Test
    fun `the placeholder is a value rather than a name, however it is spelled`() {
        // Measured: 2,699 rows of the app's set say "Unnamed Track", 676 say exactly "Track", 97 say
        // "Unnamed", four say "Track unnamed" and 22 say "Track 7" and the like.
        listOf(
            "Unnamed Track", "unnamed track", "UNNAMED TRACK", "Unnamed", "unnamed",
            "Track", "track", "Track unnamed", "Track 7", "track 16"
        ).forEach { placeholder ->
            assertEquals(
                "\"$placeholder\" is a placeholder, not a name",
                0,
                OtherTracksJson.parse(feature(name = placeholder, locality = "Richmond")).size
            )
        }
    }

    @Test
    fun `a name that merely starts with the placeholder is a name`() {
        // Anchored, so a track genuinely called "Trackway" or "Track to Crows Nest" keeps the name it was
        // given - which is why the numbered placeholders are dropped here rather than by a query pattern.
        listOf("Trackway", "Track to Crows Nest", "Track - Woodchipped Pathway").forEach { named ->
            assertEquals(
                named,
                OtherTracksJson.parse(feature(name = named, locality = "Richmond")).single().name
            )
        }
    }

    @Test
    fun `a blank name is no name either`() {
        assertEquals(0, OtherTracksJson.parse(feature(name = "   ", locality = "Richmond")).size)
        assertEquals(0, OtherTracksJson.parse(feature(name = "", locality = null)).size)
    }

    @Test
    fun `a count is read, and an answer that is not one is not counted`() {
        assertEquals(412, OtherTracksJson.parseCount("""{"count":412}"""))
        assertNull(OtherTracksJson.parseCount("""{"error":{"code":400}}"""))
        assertNull(OtherTracksJson.parseCount("not json at all"))
    }

    @Test
    fun `a document of several rows is read whole`() {
        val document = document(
            feature(globalId = "one", name = "Southern Walkway"),
            feature(globalId = "two", name = "Crazy Horse"),
            feature(globalId = null, name = "dropped, because it has no key")
        )

        val tracks = OtherTracksJson.parse(document)

        assertEquals("the readable ones, and only those", 2, tracks.size)
        assertEquals(listOf("one", "two"), tracks.map { it.globalId })
    }

    /** Several features as the service writes a page of them. */
    private fun document(vararg features: String): String =
        """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    /** One feature, as the service writes it, with only the fields a test cares about set. */
    private fun feature(
        globalId: String? = "abc-123",
        name: String? = "Southern Walkway",
        locality: String? = "Mount Victoria",
        geometry: String = """{"type":"LineString","coordinates":[[173.1,-41.3],[173.2,-41.4]]}"""
    ): String {
        val properties = buildList {
            globalId?.let { add(""""GlobalID":"$it"""") }
            name?.let { add(""""Name":"$it"""") }
            locality?.let { add(""""Locality":"$it"""") }
            add(""""DataSupplier":"Wellington City Council"""")
            add(""""Walk":3""")
        }.joinToString(",")
        return """{"type":"Feature","geometry":$geometry,"properties":{$properties}}"""
    }
}
