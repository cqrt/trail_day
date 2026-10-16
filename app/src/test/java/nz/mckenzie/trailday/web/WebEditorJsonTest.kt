package nz.mckenzie.trailday.web

import kotlinx.serialization.json.Json
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetPhrase
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.track.TrackInterchange
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The document the page is handed.
 *
 * Two things are being held here. One is that the desk is told enough to draw and to answer a
 * question: the kind, the line, the box the work is in and where the phone is. The other is that
 * nothing the walker typed on the phone is quietly dropped on the way out - the mapping into the
 * backup vocabulary is written out in [WebEditorJson], and a column forgotten there is a column the
 * desk never sees.
 *
 * What is *not* in the document is held as firmly: the fields the app no longer has never travel
 * to the desk at all.
 */
class WebEditorJsonTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    /** An asset with every field a value of its own, so a dropped column cannot hide. */
    private val everyField = AssetEntity(
        id = 7L,
        name = "Estuary road",
        kind = "BIKE_TRACK",
        shape = "LINE",
        notes = "Both edges, twice a year",
        active = true,
        createdAtEpochMs = 1_700_000_000_000L,
        lengthM = 3300.0
    )

    private fun roundTrip(document: WebEditorDocument): WebEditorDocument =
        json.decodeFromString(WebEditorDocument.serializer(), WebEditorJson.build(document))

    /** The line the phone holds for this asset, which the version the desk quotes is hashed over. */
    private val path = listOf(GeoPoint(-41.5, 173.8), GeoPoint(-41.6, 173.9))

    private fun documentWith(
        asset: AssetEntity,
        recordings: Int = 0
    ) = WebEditorDocument(
        assets = listOf(
            WebEditorJson.record(
                asset = asset,
                paths = listOf(path),
                recordingCount = recordings
            )
        )
    )

    @Test
    fun `a stored word this build does not know reaches the desk as a track`() {
        // A value from a later build, or a database edited by hand: the phone reads it back once,
        // here, so the page is handed a kind it can compare with the kinds in its own filter list.
        val unknown = everyField.copy(kind = "PADDOCK", shape = "LINE")

        assertEquals(
            AssetKind.TRACK.name,
            roundTrip(documentWith(unknown)).assets.single().asset.kind
        )
    }

    @Test
    fun `every column of an asset survives the trip to the desk`() {
        val restored = roundTrip(documentWith(everyField)).assets.single().asset

        assertEquals(everyField.id, restored.id)
        assertEquals(everyField.name, restored.name)
        assertEquals(everyField.kind, restored.kind)
        assertEquals(everyField.shape, restored.shape)
        assertEquals(everyField.notes, restored.notes)
        assertEquals(everyField.active, restored.active)
        assertEquals(everyField.createdAtEpochMs, restored.createdAtEpochMs)
        assertEquals(everyField.lengthM, restored.lengthM, 0.0)
    }

    @Test
    fun `the asset the desk is handed carries the paths a write has to bring back`() {
        // The geometry a page draws comes from the GeoJSON, but a line write has to carry every path -
        // so the paths are in the record beside the version, and a page that ignores them still works:
        // its writes carry one path, and the phone refuses those for a track that has side tracks
        // rather than dropping them.
        val spur = listOf(path.last(), GeoPoint(-41.55, 173.85))
        val record = WebEditorDocument(
            assets = listOf(
                WebEditorJson.record(
                    asset = everyField,
                    paths = listOf(path, spur),
                    recordingCount = 0
                )
            )
        ).let { roundTrip(it) }.assets.single()

        assertEquals("the line first, then its side tracks", 2, record.paths.size)
        assertEquals(path, record.paths[0].map { GeoPoint(it.lat, it.lng) })
        assertEquals(spur, record.paths[1].map { GeoPoint(it.lat, it.lng) })
    }

    @Test
    fun `an asset with nothing drawn carries no paths at all`() {
        val record = WebEditorDocument(
            assets = listOf(
                WebEditorJson.record(
                    asset = everyField,
                    paths = emptyList(),
                    recordingCount = 0
                )
            )
        ).let { roundTrip(it) }.assets.single()

        assertTrue("the desk sees an empty list rather than a guess", record.paths.isEmpty())
    }

    @Test
    fun `nothing the old app knew about a place reaches the desk`() {
        val text = WebEditorJson.build(documentWith(everyField))

        // The fields the old app had are gone, and a document that still carried them would be
        // one the page could show without meaning to.
        assertTrue("no method: $text", !text.contains("method"))
        assertTrue("no interval: $text", !text.contains("intervalDays"))
        assertTrue("no swath width: $text", !text.contains("swathWidthM"))
        assertTrue("no count of passes: $text", !text.contains("passesRequired"))
        assertTrue("no last-walked date: $text", !text.contains("lastSprayedAtEpochMs"))
    }

    @Test
    fun `the geometry is left out of the state, because the geojson carries it once`() {
        val text = WebEditorJson.build(documentWith(everyField))

        assertTrue(roundTrip(documentWith(everyField)).assets.single().asset.points.isEmpty())
        assertTrue("the length still says how long it is: $text", text.contains("\"lengthM\":3300"))
        assertTrue("said empty rather than left to guess: $text", text.contains("\"points\":[]"))
    }

    @Test
    fun `the length a card is read for travels with the record`() {
        // The card says how long the line is, and it is the phone's own number: worked out again on
        // the desk from the vertices it can see would be a second answer to a question the phone has
        // already answered.
        val ridden = everyField.copy(
            kind = AssetKind.BIKE_TRACK.name,
            shape = AssetKind.BIKE_TRACK.shape.name,
            lengthM = 855.0
        )

        val record = roundTrip(documentWith(ridden)).assets.single()
        assertEquals(855.0, record.asset.lengthM, 1e-9)
    }

    @Test
    fun `the heights a file gave a line reach the desk, and a line with none says nothing`() {
        val climbed = listOf(
            GeoPoint(-45.35, 168.12, altitudeM = 553.6),
            GeoPoint(-45.36, 168.13, altitudeM = 663.6)
        )
        val withHeights = WebEditorJson.record(
            asset = everyField,
            paths = listOf(climbed),
            recordingCount = 0
        )

        assertEquals(553.6, withHeights.paths[0][0].altitudeM!!, 1e-9)
        assertEquals(663.6, withHeights.paths[0][1].altitudeM!!, 1e-9)
        // The range is the phone's own figures, worked out once, so the two cards cannot disagree.
        assertEquals(553.6, withHeights.elevation!!.lowestM, 1e-9)
        assertEquals(663.6, withHeights.elevation!!.highestM, 1e-9)
        assertEquals(110.0, withHeights.elevation!!.climbM, 1e-9)

        // A line drawn on a map has no heights, and the document says nothing about a height rather than
        // a null on every point.
        val flat = documentWith(everyField)
        val text = WebEditorJson.build(flat)
        assertTrue("no height on the wire: $text", !text.contains("altitudeM"))
        assertTrue("and no range: $text", !text.contains("elevation"))
        assertNull(flat.assets.single().elevation)
    }

    @Test
    fun `the box the work is in and the phone's fix are both offered`() {
        val document = WebEditorDocument(
            bounds = WebEditorBounds.of(
                LatLngBounds(minLat = -41.6, minLng = 173.8, maxLat = -41.4, maxLng = 174.1)
            ),
            position = WebEditorPosition(lat = -41.5, lng = 173.9)
        )

        val restored = roundTrip(document)

        assertEquals(WebEditorBounds(-41.6, 173.8, -41.4, 174.1), restored.bounds)
        assertEquals(WebEditorPosition(-41.5, 173.9), restored.position)
    }

    @Test
    fun `the record carries the version an edit has to quote back`() {
        val record = roundTrip(documentWith(everyField)).assets.single()

        // Worked out here from the row and the line, exactly as the phone will work it out
        // again when the edit comes back: a desk quoting this cannot have been reading another version
        // of the asset, and the version survives the trip to the page as the rest of the record does.
        assertEquals(WebEditorVersion.of(everyField, listOf(path)), record.version)
    }

    @Test
    fun `the record carries what deleting it would take, in the phone's own words`() {
        // A track nothing has ever been recorded against, which is the only kind the desk may delete.
        val clean = roundTrip(documentWith(everyField, recordings = 0)).assets.single()

        assertTrue("a track nothing is recorded against may be deleted", clean.removal.allowed)
        assertTrue(
            "and the sentence says so: ${clean.removal.sentence}",
            clean.removal.sentence.contains("nothing else with it")
        )

        // A recording outlives its asset, and this is the sentence that has to say so before anything
        // goes: the count is read by the same rule the delete uses.
        val recorded = roundTrip(documentWith(everyField, recordings = 1)).assets.single()
        assertFalse(recorded.removal.allowed)
        assertTrue(
            "counts the trip: ${recorded.removal.sentence}",
            recorded.removal.sentence.contains("1 trip")
        )
        assertTrue(
            "and sends the walker to the phone: ${recorded.removal.sentence}",
            recorded.removal.sentence.contains("Delete it on the phone")
        )
    }

    @Test
    fun `the desk's form is offered the phone's own kinds`() {
        val choices = roundTrip(documentWith(everyField)).choices

        // Not a second copy of the vocabulary: the phone's own phrase table, value and label, so a
        // kind added on the phone appears on the desk with nothing to remember here. The kinds are
        // all the form offers: nothing else about a place travels to the desk.
        assertEquals(
            AssetPhrase.kinds.map { it.name to AssetPhrase.kind(it) },
            choices.kinds.map { it.value to it.label }
        )
    }

    @Test
    fun `a kind carries the shape it makes, so the desk draws a point as a point`() {
        val kinds = WebEditorChoices.ofApp().kinds.associateBy { it.value }

        // The phone's own answer, not a list of kind names kept in JavaScript: the desk places a
        // point with one click and offers it no side tracks, and a page that decided which kinds
        // those are from their names would drift the day a new kind ships.
        assertEquals(AssetKind.HUT.shape.name, kinds.getValue(AssetKind.HUT.name).shape)
        assertEquals("POINT", kinds.getValue(AssetKind.HUT.name).shape)
        assertEquals("LINE", kinds.getValue(AssetKind.TRACK.name).shape)
        assertEquals("LINE", kinds.getValue(AssetKind.BIKE_TRACK.name).shape)
        assertEquals("POINT", kinds.getValue(AssetKind.WAYPOINT.name).shape)
    }

    @Test
    fun `a new asset is offered the phone's own starting kind`() {
        val fresh = roundTrip(documentWith(everyField)).newAsset

        // What a track somebody draws from scratch starts as: the phone's own default kind, rather
        // than a name written into the page.
        assertEquals(AssetKind.TRACK.name, fresh.kind)
    }

    @Test
    fun `an empty phone is a document with no places, not a missing document`() {
        val text = WebEditorJson.build(WebEditorDocument())

        val restored = json.decodeFromString(WebEditorDocument.serializer(), text)

        assertTrue(restored.assets.isEmpty())
        // The page is handed every key every time, so nothing it reads is ever ambiguous.
        assertTrue(text.contains("\"bounds\":null"))
        assertTrue(text.contains("\"position\":null"))
        assertTrue(text.contains("\"assets\":[]"))
    }

    @Test
    fun `the page is told what a dropped GPX file is called and how big one may be`() {
        val gpx = roundTrip(documentWith(everyField)).gpx

        // The name a file travels under, and the size the socket will read. Both the phone's answers:
        // a page that guessed the name would send a file the phone read as absent, which looks exactly
        // like a GPX file with nothing in it, and one that guessed the size would pass on a refusal
        // about a request that never arrived.
        assertEquals(WebEditorServer.GPX_FIELD, gpx.field)
        assertEquals(WebEditorServer.MAX_GPX_BYTES, gpx.maxBytes)
        assertEquals(2 * 1024 * 1024, gpx.maxBytes)
    }

    @Test
    fun `a reading answers with the paths, the line first, and what became of the segments`() {
        val line = listOf(GeoPoint(-41.5, 173.8), GeoPoint(-41.6, 173.9))
        val sideTrack = listOf(GeoPoint(-41.6, 173.9), GeoPoint(-41.7, 173.9))

        val text = WebEditorJson.gpx(
            track = TrackInterchange.Track(
                name = null,
                reading = TrackInterchange.Reading(
                    paths = listOf(line, sideTrack),
                    sideTracks = 1,
                    segmentsDidNotMeet = false
                )
            ),
            otherTracks = 0
        )
        val read = json.decodeFromString(WebEditorGpx.serializer(), text)

        // The vertices in the same shape the write that follows carries them back in, so a drawing read
        // out of a file is the same kind of thing as one traced over the imagery - which is what lets
        // the desk tidy it and save it by the one path every drawing takes.
        assertEquals(2, read.paths.size)
        assertEquals(WebEditorPoint(lat = -41.5, lng = 173.8), read.paths[0].first())
        assertEquals("the line is first, and the side track hangs off its own last vertex",
            read.paths[0].last(), read.paths[1].first())
        assertEquals(1, read.sideTracks)
        assertEquals(false, read.segmentsDidNotMeet)
    }

    @Test
    fun `a file whose segments did not meet says so, rather than being left to be worked out`() {
        val text = WebEditorJson.gpx(
            track = TrackInterchange.Track(
                name = null,
                reading = TrackInterchange.Reading(
                    paths = listOf(listOf(GeoPoint(-41.5, 173.8), GeoPoint(-42.0, 174.0))),
                    sideTracks = 0,
                    segmentsDidNotMeet = true
                )
            ),
            otherTracks = 0
        )

        // The page cannot see this from a count of paths: a file with one segment and a file whose
        // segments could not be joined are both one line, and only the second is worth saying out loud.
        assertTrue("the page is told: $text", text.contains("\"segmentsDidNotMeet\":true"))
    }

    @Test
    fun `a file of several tracks tells the page how many more there were`() {
        val text = WebEditorJson.gpx(
            track = TrackInterchange.Track(
                name = "Boundary Fence/Ocean Beach Tk",
                reading = TrackInterchange.Reading(
                    paths = listOf(listOf(GeoPoint(-41.5, 173.8), GeoPoint(-41.6, 173.9))),
                    sideTracks = 0,
                    segmentsDidNotMeet = false
                )
            ),
            otherTracks = 3
        )

        // The desk draws the first; the page has to know the file held three more so it can say so
        // rather than let them vanish without a word.
        assertTrue("the page is told: $text", text.contains("\"otherTracks\":3"))
    }

    @Test
    fun `a DOC search carries each track with its geometry, kind and the phone's own figures`() {
        val text = WebEditorJson.docSearch(
            tracks = listOf(
                DocItem(
                    source = DocSource.TRACKS,
                    objectId = 42,
                    name = "Glory Tk",
                    paths = listOf(listOf(GeoPoint(-46.6, 168.3), GeoPoint(-46.61, 168.31))),
                    facts = mapOf(
                        "SubObjectType" to "Walking Track",
                        "CharName7" to DocSource.TRACK_TYPE_CHARACTERISTIC,
                        "CharValue7" to "One way",
                        "FlocID" to "BLUFFHIL-TK02"
                    )
                )
            ),
            importedRefs = setOf("doc-tracks:99"),
            from = GeoPoint(-46.62, 168.32),
            message = null,
            capped = false
        )
        val read = json.decodeFromString(WebEditorDocSearch.serializer(), text)

        // The geometry travels to the desk because these tracks are not on the phone yet - the desk
        // draws them, and ticking one has to be able to make it without asking DOC again.
        assertEquals(1, read.tracks.size)
        assertEquals(42L, read.tracks[0].id)
        assertEquals("Glory Tk", read.tracks[0].name)
        assertEquals(
            "the name the import will give it travels too, so a wrong place is seen before it lands",
            "Bluff Hill Glory Tk",
            read.tracks[0].assetName
        )
        assertEquals("Walking Track", read.tracks[0].kind)
        assertEquals("how the track is walked travels so the desk can show and filter it", "One way", read.tracks[0].trackType)
        assertEquals("the site travels so the phone can prefix the place", "BLUFFHIL-TK02", read.tracks[0].flocId)
        assertEquals(2, read.tracks[0].points)
        assertEquals(1, read.tracks[0].paths.size)
        assertEquals(2, read.tracks[0].paths[0].size)
        assertEquals(WebEditorPoint(lat = -46.6, lng = 168.3), read.tracks[0].paths[0].first())
        assertEquals("a track the phone does not have is not marked imported", false, read.tracks[0].imported)
        assertTrue("and its distance to the phone travels so the desk can order by it",
            (read.tracks[0].distanceM ?: 0.0) > 0.0)
    }

    @Test
    fun `with no fix the distance is left out, and the desk falls back to by name`() {
        val track = DocItem(
            source = DocSource.TRACKS,
            objectId = 42,
            name = "Glory Tk",
            paths = listOf(listOf(GeoPoint(-46.6, 168.3), GeoPoint(-46.61, 168.31))),
            facts = mapOf("SubObjectType" to "Walking Track")
        )

        val text = WebEditorJson.docSearch(
            tracks = listOf(track),
            importedRefs = emptySet(),
            from = null,
            message = null,
            capped = false
        )
        val read = json.decodeFromString(WebEditorDocSearch.serializer(), text)

        assertTrue("no fix means no distance", read.tracks[0].distanceM == null)
    }

    @Test
    fun `a DOC track the phone already has is marked imported, so the desk will not import it twice`() {
        val track = DocItem(
            source = DocSource.TRACKS,
            objectId = 42,
            name = "Glory Tk",
            paths = listOf(listOf(GeoPoint(-46.6, 168.3), GeoPoint(-46.61, 168.31))),
            facts = mapOf("SubObjectType" to "Walking Track")
        )

        val text = WebEditorJson.docSearch(
            tracks = listOf(track),
            importedRefs = setOf(track.sourceRef),
            from = null,
            message = null,
            capped = false
        )

        assertTrue("the page is told: $text", text.contains("\"imported\":true"))
    }

    @Test
    fun `a DOC import answers with the count and the phone's sentence`() {
        val text = WebEditorJson.docImported(count = 3, message = "Imported 3 DOC tracks.")

        assertTrue("the page is told: $text", text.contains("\"imported\":3"))
        assertTrue("and the phone's words: $text", text.contains("Imported 3 DOC tracks."))
    }
}
