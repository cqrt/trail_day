package nz.mckenzie.trailday.web

import kotlinx.serialization.json.Json
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * What the phone does with an edit from a desk.
 *
 * The two things held here are the two that make the arrangement safe. The desk cannot write a field
 * it did not send, so a change made on the phone while a card was open
 * survives an edit from a laptop; and it cannot write one somebody else has already changed, which
 * is what the version is for. The wording of a refusal is not invented here either - a field out of
 * range is refused in [nz.mckenzie.trailday.ui.AssetEdits]'s own words, and one of these tests holds
 * it to that rather than to a second copy of the rules.
 */
class WebEditorEditTest {

    private val asset = AssetEntity(
        id = 7L,
        name = "Estuary road",
        kind = "BIKE_TRACK",
        shape = "LINE",
        notes = "Both edges",
        active = true,
        createdAtEpochMs = 1_700_000_000_000L,
        lengthM = 3300.0
    )

    /** The line as the phone holds it, which is half of what the version the desk quotes is made of. */
    private val path = listOf(GeoPoint(-41.5, 173.8), GeoPoint(-41.6, 173.9))

    private fun pointJson(points: List<GeoPoint>): String =
        points.joinToString(prefix = "[", postfix = "]") { point ->
            if (point.altitudeM == null) {
                """{"lat":${point.lat},"lng":${point.lng}}"""
            } else {
                """{"lat":${point.lat},"lng":${point.lng},"altitudeM":${point.altitudeM}}"""
            }
        }

    /**
     * The body a page sends back after opening this asset and changing one or two things.
     *
     * Only what the desk shows: a name, a kind and the notes. Nothing else about the asset travels - the
     * phone keeps its own - so a body here does not carry them either, which is the whole point of the
     * rule `a field the desk never sent survives the edit` holds.
     *
     * Written by hand as JSON rather than built from [WebEditorEdit]: a test that built its body from the
     * class would keep passing after somebody renamed a field, which is exactly the failure a page would
     * see as "nothing saved".
     */
    private fun body(
        version: String = WebEditorVersion.of(asset, listOf(path)),
        name: String = asset.name,
        kind: String = asset.kind,
        notes: String? = asset.notes,
        extra: String = ""
    ): String = buildString {
        append("""{"name":"$name","kind":"$kind",""")
        append(""""notes":${if (notes == null) "null" else "\"$notes\""},""")
        if (extra.isNotEmpty()) append("""$extra,""")
        append(""""version":"$version"}""")
    }

    /** The `paths` a body carries, as JSON, written the way a page writes them. */
    private fun linesJson(paths: List<List<GeoPoint>>): String =
        """"paths":[${paths.joinToString(",") { pointJson(it) }}]"""

    private fun ok(editBody: String?): WebEditorEditResult.Ok {
        val result = WebEditorEdits.apply(asset, listOf(path), editBody)
        assertTrue("expected this to be taken: $result", result is WebEditorEditResult.Ok)
        return result as WebEditorEditResult.Ok
    }

    private fun refused(editBody: String?): WebEditorEditResult.Refused {
        val result = WebEditorEdits.apply(asset, listOf(path), editBody)
        assertTrue("expected this to be refused: $result", result is WebEditorEditResult.Refused)
        return result as WebEditorEditResult.Refused
    }

    /** A new asset, as the desk's file import sends one: a name, and the line the phone read out of it. */
    private fun fileBody(
        name: String = "New track",
        kind: String = "TRACK",
        paths: List<List<GeoPoint>>? = listOf(path)
    ): String = buildString {
        append("""{"name":"$name","kind":"$kind","notes":null""")
        if (paths != null) append(""","paths":[${paths.joinToString(",") { pointJson(it) }}]""")
        append("}")
    }

    private fun drafted(createBody: String?): WebEditorDraft {
        val result = WebEditorEdits.create(createBody, nowEpochMs = 1_790_000_000_000L)
        assertTrue("expected this to be taken: $result", result is WebEditorCreateResult.Ok)
        return (result as WebEditorCreateResult.Ok).draft
    }

    private fun createRefused(createBody: String?): WebEditorCreateResult.Refused {
        val result = WebEditorEdits.create(createBody, nowEpochMs = 1_790_000_000_000L)
        assertTrue("expected this to be refused: $result", result is WebEditorCreateResult.Refused)
        return result as WebEditorCreateResult.Refused
    }

    @Test
    fun `a good edit comes back as the row to store`() {
        val result = ok(body(name = "Estuary road, lower"))

        assertEquals("Estuary road, lower", result.asset.name)
        assertEquals("the id is the phone's, not the desk's to send", 7L, result.asset.id)
    }

    @Test
    fun `the fields the desk never sends survive the edit as the row's own`() {
        val edited = ok(body(name = "Estuary road, lower")).asset

        // The whole promise of writing through the row the phone holds now: the page changes the
        // details it shows, not records. A field the desk does not send is still the row's own.
        assertEquals(asset.active, edited.active)
        assertEquals(asset.createdAtEpochMs, edited.createdAtEpochMs)
        assertEquals(3300.0, edited.lengthM, 0.0)
    }

    @Test
    fun `the version follows the fields the desk may write, and only those`() {
        val version = WebEditorVersion.of(asset)

        assertNotEquals(version, WebEditorVersion.of(asset.copy(name = "Renamed")))
        assertNotEquals(version, WebEditorVersion.of(asset.copy(notes = "Different notes")))
        assertNotEquals(version, WebEditorVersion.of(asset.copy(kind = "TRACK")))
        assertNotEquals(version, WebEditorVersion.of(asset.copy(shape = "POINT")))

        // A field the desk cannot write must not refuse an edit that could not overwrite it: archiving
        // it or dragging a vertex does not stop somebody renaming it from the desk.
        assertEquals(version, WebEditorVersion.of(asset.copy(active = false)))
        assertEquals(version, WebEditorVersion.of(asset.copy(lengthM = 1234.0)))
    }

    @Test
    fun `an edit written against the asset as it was is refused, and says why`() {
        val stale = refused(body(version = WebEditorVersion.of(asset.copy(name = "Renamed"))))

        assertEquals(WebEditorRefusal.STALE, stale.refusal)
        assertTrue("says where it changed: ${stale.message}", stale.message.contains("phone"))
        assertTrue("says nothing was saved: ${stale.message}", stale.message.contains("nothing was saved"))
    }

    @Test
    fun `a field out of range is refused in the app's own words`() {
        // These sentences are `ui/AssetEdits`'s, word for word: the desk does not get a second set of
        // rules, and this is what holds the two together. The name is the one field those rules can
        // refuse here.
        assertEquals(
            "Give it a name so it can be found later",
            refused(body(name = "   ")).message
        )
    }

    @Test
    fun `a kind the phone does not know is refused, not defaulted`() {
        // Named states arrive already decided, so a name this build does not know is a refusal. Let
        // through, a made-up kind would quietly become a track.
        assertTrue(refused(body(kind = "PADDOCK")).message.contains("PADDOCK"))
    }

    @Test
    fun `an edit that cannot be read at all is refused rather than thrown`() {
        assertEquals(WebEditorRefusal.INVALID, refused(null).refusal)
        assertEquals(WebEditorRefusal.INVALID, refused("").refusal)
        assertEquals(WebEditorRefusal.INVALID, refused("not json at all").refusal)
        // The version is how the phone knows what the desk read, so a body without one is not an
        // edit the phone can take: there is nothing to compare it against.
        assertEquals(WebEditorRefusal.INVALID, refused("""{"name":"Estuary road"}""").refusal)
    }

    @Test
    fun `something in the body this build does not know about is ignored, not refused`() {
        // A page from a later build sending a field this one has never heard of must not be a page
        // that cannot save at all. What it changes is what this build knows how to change.
        val withExtra = ok(body().dropLast(1) + ""","geometry":[[173.8,-41.5],[173.9,-41.6]]}""")

        assertEquals(asset.name, withExtra.asset.name)
    }

    @Test
    fun `the refusal table is the contract, so it is pinned`() {
        assertEquals(404, WebEditorRefusal.MISSING.status)
        assertEquals("not-found", WebEditorRefusal.MISSING.reason)
        assertEquals(409, WebEditorRefusal.STALE.status)
        assertEquals("stale", WebEditorRefusal.STALE.reason)
        assertEquals(409, WebEditorRefusal.IN_USE.status)
        assertEquals("in-use", WebEditorRefusal.IN_USE.reason)
        assertEquals(400, WebEditorRefusal.INVALID.status)
        assertEquals("invalid", WebEditorRefusal.INVALID.reason)
    }

    @Test
    fun `a write that carries a line is refused, because the desk does not draw one`() {
        // The desk used to be able to move a vertex. It cannot now, and a page that still tries is a page
        // from before the change: refused rather than quietly ignored, because ignoring it would leave
        // the page believing it had moved a track it had not.
        val refused = refused(body(extra = linesJson(listOf(path))))

        assertTrue("says so: ${refused.message}", refused.message.contains("does not do"))
        assertTrue("and what to do: ${refused.message}", refused.message.contains("Reload the page"))
    }

    @Test
    fun `a body that says nothing about the drawing leaves the line exactly as it is`() {
        // Which is what the details form sends: the words, and nothing about the ground.
        assertNull(ok(body()).paths)
    }

    @Test
    fun `a version quoted against the line as it was is refused once a vertex has moved`() {
        val quoted = WebEditorVersion.of(asset, listOf(path))
        val moved = path + GeoPoint(-41.7, 174.1)

        val refused = WebEditorEdits.apply(asset, listOf(moved), body(version = quoted))

        assertEquals(WebEditorRefusal.STALE, (refused as WebEditorEditResult.Refused).refusal)
    }

    @Test
    fun `the version covers the line, so a card cannot undo a move somebody else made`() {
        assertNotEquals(
            WebEditorVersion.of(asset, listOf(path)),
            WebEditorVersion.of(asset, listOf(path.dropLast(1) + GeoPoint(-41.7, 174.1)))
        )
        assertNotEquals(
            "a vertex added is a change",
            WebEditorVersion.of(asset, listOf(path)),
            WebEditorVersion.of(asset, listOf(path + GeoPoint(-41.7, 174.1)))
        )
        assertEquals(
            "and the same points hash the same whatever else was read with them",
            WebEditorVersion.of(asset, listOf(path)),
            WebEditorVersion.of(asset, listOf(path))
        )
    }

    @Test
    fun `the version covers a side track, so a card cannot undo one added on the phone`() {
        val junction = path.last()
        val spur = listOf(junction, GeoPoint(-41.55, 173.95))

        // The same line first, with and without a side track hanging off it: a desk holding the card
        // from before the spur was drawn is holding a card that is out of date.
        assertNotEquals(
            "a side track is a change to the track the desk was handed",
            WebEditorVersion.of(asset, listOf(path)),
            WebEditorVersion.of(asset, listOf(path, spur))
        )
        // And the paths are hashed with their boundaries marked, so the same vertices split two ways
        // are two versions rather than one - which is what makes the mark worth having.
        assertNotEquals(
            WebEditorVersion.of(asset, listOf(path, spur)),
            WebEditorVersion.of(
                asset,
                    listOf(listOf(path.first(), spur.last()), listOf(junction))
            )
        )
    }

    @Test
    fun `a new track comes back as a row the database will number, with its line`() {
        val draft = drafted(fileBody(name = "Gully track"))

        assertEquals("Gully track", draft.asset.name)
        assertEquals("TRACK", draft.asset.kind)
        assertTrue("a new asset is active", draft.asset.active)
        assertEquals("the id is the database's to issue", 0L, draft.asset.id)
        assertEquals(
            "and the date is the phone's clock, not the laptop's",
            1_790_000_000_000L,
            draft.asset.createdAtEpochMs
        )
        assertEquals(path, draft.geometry.line)
    }

    @Test
    fun `a file's line cannot be kept as a place, in the path rules' own words`() {
        // A file is a line. A page that asked for it to be kept as a waypoint - or a hut, or a campsite -
        // is asking for a place with two points on it, which `AssetPathEdits` refuses for every kind.
        val refused = createRefused(fileBody(name = "Water source", kind = "WAYPOINT"))

        assertTrue(
            "counts the points it was sent: ${refused.message}",
            refused.message.contains("2 points")
        )
    }

    @Test
    fun `a file with no line in it is refused, because there is nothing to keep`() {
        assertTrue(
            "a file the phone read as no paths at all: ${createRefused(fileBody(paths = null)).message}",
            createRefused(fileBody(paths = null)).message.contains("no line in that file")
        )
        assertTrue(
            "an empty path is the same thing said a different way: " +
                createRefused(fileBody(paths = emptyList())).message,
            createRefused(fileBody(paths = emptyList())).message.contains("A line needs at least two points")
        )
    }

    @Test
    fun `a new asset is judged by the same rules, so an unnamed one is refused in the app's words`() {
        assertEquals("Give it a name so it can be found later", createRefused(fileBody(name = "  ")).message)
        assertTrue(
            "and a kind this build does not know is still a refusal: " +
                createRefused("""{"name":"X","kind":"PADDOCK","paths":[]}""").message,
            createRefused("""{"name":"X","kind":"PADDOCK","paths":[]}""")
                .message.contains("PADDOCK")
        )
    }

    @Test
    fun `a new asset ignores a version, because it has nothing to be stale against`() {
        // The page builds both bodies with one function, so a create arrives carrying the version of
        // whatever card the walker had been looking at. There is no row to compare it with: the
        // only thing that version could refuse is the making of a new track, which is nobody's intent.
        val withVersion = fileBody().dropLast(1) + ""","version":"a-card-from-before"}"""

        assertEquals(path, drafted(withVersion).geometry.line)
    }

    @Test
    fun `a file's heights are kept, and a height that moved is a different version`() {
        // The heights a file gave a line travel with it, so keeping the file does not flatten a line that
        // came off a ride. The desk hands a line back exactly once - when it hands the file over - and
        // this is that write.
        val climbed = listOf(
            GeoPoint(-41.5, 173.8, altitudeM = 553.6),
            GeoPoint(-41.6, 173.9, altitudeM = 663.6)
        )

        val draft = drafted(fileBody(paths = listOf(climbed)))

        assertEquals("the height on the line is the file's own", 553.6, draft.geometry.line.first().altitudeM!!, 1e-9)

        // A vertex that moved only in height is a vertex that moved: a card that never saw it is stale.
        assertNotEquals(
            WebEditorVersion.of(asset, listOf(climbed)),
            WebEditorVersion.of(asset, listOf(listOf(climbed[0].copy(altitudeM = 600.0), climbed[1])))
        )
    }

    /* ---- A file that carries side tracks ------------------------------------------------ */

    /** A side track off the far end of the line, which is where one leaves a track. */
    private val spur = listOf(path.last(), GeoPoint(-41.55, 173.85))

    @Test
    fun `a file with a side track keeps every path in it`() {
        val draft = drafted(fileBody(paths = listOf(path, spur)))

        assertEquals("the line and its side track", 2, draft.geometry.paths.size)
        assertEquals(path, draft.geometry.paths[0])
        assertEquals("the side track goes back exactly as it came", spur, draft.geometry.paths[1])
    }

    @Test
    fun `a file's side track that does not start on the line is refused in the app's own words`() {
        val refused = createRefused(
            fileBody(paths = listOf(path, listOf(GeoPoint(-41.2, 173.2), GeoPoint(-41.3, 173.3))))
        )

        assertTrue("says what to do: ${refused.message}", refused.message.contains("start on the track"))
    }

    /* ---- A track file dropped on the desk ----------------------------------------------- */

    /**
     * The body `POST /api/gpx` carries: the file's own bytes, base64-encoded, under the name the
     * state document gave. Base64 because a KMZ is a zip and not text, so every file travels the same
     * way rather than two wires for two kinds of file.
     */
    private fun gpxBody(file: String): String =
        Json.encodeToString(mapOf(WebEditorServer.GPX_FIELD to base64(file)))

    private fun base64(file: String): String =
        Base64.getEncoder().encodeToString(file.toByteArray(Charsets.UTF_8))

    @Test
    fun `a GPX file arrives as the bytes it is, newlines and quotes and all`() {
        // The file travels as base64 of its own bytes, so what comes back out has to be the file
        // exactly as it was: a track's name with a quote in it, the XML's own line breaks, and every
        // coordinate, none of which a second encoding may quietly change.
        val file = "<?xml version=\"1.0\"?>\n<gpx version=\"1.1\"><trk><name>Gully \"track\"</name>\n" +
            "<trkseg><trkpt lat=\"-41.0\" lon=\"174.0\"/><trkpt lat=\"-41.1\" lon=\"174.1\"/>" +
            "</trkseg></trk></gpx>\n"

        assertArrayEquals(file.toByteArray(Charsets.UTF_8), WebEditorEdits.readTrackFile(gpxBody(file)))
    }

    @Test
    fun `a body with no track file in it is nothing to read, rather than an empty file`() {
        // Null rather than an empty array: the caller refuses both, and a missing one is what a page
        // that sent the wrong field name would produce - which is a page to be told about, not a file.
        assertNull(WebEditorEdits.readTrackFile(null))
        assertNull(WebEditorEdits.readTrackFile(""))
        assertNull(WebEditorEdits.readTrackFile("not json at all"))
        assertNull(WebEditorEdits.readTrackFile("""{"gpx":""}"""))
        assertNull(WebEditorEdits.readTrackFile("""{"gpx":"   "}"""))
        assertNull(
            "the edit bodies' own field name is not this one",
            WebEditorEdits.readTrackFile("""{"points":[]}""")
        )
        assertNull(
            "and text that is not base64 is not a file either",
            WebEditorEdits.readTrackFile("""{"gpx":"not base64 !!"}""")
        )
    }

    /* ---- A field a later page sends ----------------------------------------------------- */

    @Test
    fun `a field this build has never heard of does not stop a file being read`() {
        // A page from a later build may carry more than this one knows about, and a file that is
        // perfectly readable must not be refused over a field nothing here reads.
        val file = "<gpx version=\"1.1\"><trk><trkseg><trkpt lat=\"-41.0\" lon=\"174.0\"/></trkseg></trk></gpx>"
        val body = Json.encodeToString(
            mapOf(WebEditorServer.GPX_FIELD to base64(file), "somethingNew" to "7")
        )

        assertArrayEquals(file.toByteArray(Charsets.UTF_8), WebEditorEdits.readTrackFile(body))
    }
}
