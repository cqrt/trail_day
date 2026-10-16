package nz.mckenzie.trailday.web

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.RecordingRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.tiles.Basemap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A write from the desk, through the repository the phone's own screens use, into a real database.
 *
 * The JVM tests hold the rules and the routes; what they cannot hold is that a write reaches Room -
 * that a drawn line is stored vertex
 * for vertex with its length worked out, and that a
 * version read out of `/api/state` is taken when nothing has moved and refused when something has.
 * That is what this file is for, and it is why each change was put through
 * `connectedDebugAndroidTest` rather than only the unit tests.
 *
 * The delete half is here for a reason the unit tests cannot reach either: what a delete would take
 * with it is a count of the recordings, which outlive the asset they name.
 */
@RunWith(AndroidJUnit4::class)
class WebEditorSaveTest {

    private lateinit var db: TrailDayDatabase
    private lateinit var assets: AssetRepository
    private lateinit var recordings: RecordingRepository
    private lateinit var documents: WebEditorDocuments

    /** ~111.19 m long, eastwards along the equator. */
    private val line = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001))

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TrailDayDatabase::class.java).build()
        assets = AssetRepository(db)
        recordings = RecordingRepository(db)
        documents = WebEditorDocuments(
            assets = assets,
            token = "7f3a9c7f3a9c7f3a9c7f3a9c7f3a9c7f",
            // The style is the one document nothing here reads, and a lambda that says so beats a
            // value that pretends. The fix, on the other hand, *is* read - by `state`, and not by a
            // save - so it answers honestly: this phone has no fix yet.
            basemap = { error("nothing here reads the basemap") },
            position = { null },
            readPageFile = { null },
            now = { 1_790_000_000_000L }
        )
    }

    @After
    fun tearDown() = db.close()

    /** The asset as `/api/state` carries it, which is where a desk gets its version from. */
    private suspend fun stateRecord(assetId: Long): WebEditorAssetRecord {
        val document = Json.decodeFromString(
            WebEditorDocument.serializer(),
            documents.state("phone:8799")
        )
        return document.assets.first { it.asset.id == assetId }
    }

    /**
     * The body a page sends, written out as JSON rather than built from [WebEditorEdit].
     *
     * Written by hand on purpose: a test that built its body from the class would keep passing after
     * somebody renamed a field, which is precisely the failure a page would see as "nothing saved". It
     * carries only what the desk shows: a name, a kind and the notes, because that is all a save from
     * the desk sends.
     */
    private fun editBody(
        version: String,
        name: String = "Estuary road",
        notes: String = "Both edges"
    ): String =
        """{"name":"$name","kind":"BIKE_TRACK","notes":"$notes",""" +
            """"version":"$version"}"""

    /**
     * A page from before the desk stopped drawing: the details, and a line it means to move.
     *
     * Nothing sends this any more. It is here because a laptop with the page already open - a tab from
     * before the phone was updated - sends exactly this, and the phone has to answer it rather than read
     * it as an edit of the words.
     */
    private fun editBodyWithLine(version: String, paths: List<List<GeoPoint>>): String =
        """{"name":"Estuary road","kind":"BIKE_TRACK","notes":"Both edges",""" +
            """"paths":[${paths.joinToString(",") { pointsJson(it) }}],""" +
            """"version":"$version"}"""

    /**
     * The body the desk sends when it hands a track file over: the name to keep it under, and the line
     * the phone read out of the file, with no version, because a new place has nothing to be stale
     * against.
     */
    private fun fileBody(
        name: String = "Gully track",
        paths: List<List<GeoPoint>>? = listOf(line)
    ): String {
        val parts = mutableListOf(
            """"name":"$name"""",
            """"kind":"TRACK"""",
            """"notes":null"""
        )
        paths?.let { all -> parts += """"paths":[${all.joinToString(",") { pointsJson(it) }}]""" }
        return "{" + parts.joinToString(",") + "}"
    }

    private fun pointsJson(points: List<GeoPoint>): String =
        points.joinToString(prefix = "[", postfix = "]") { """{"lat":${it.lat},"lng":${it.lng}}""" }

    private fun saved(answer: WebEditorWrite): WebEditorWrite.Saved {
        assertTrue("expected a save: $answer", answer is WebEditorWrite.Saved)
        return answer as WebEditorWrite.Saved
    }

    private fun created(answer: WebEditorWrite): WebEditorWrite.Created {
        assertTrue("expected a new asset: $answer", answer is WebEditorWrite.Created)
        return answer as WebEditorWrite.Created
    }

    private fun removed(answer: WebEditorWrite): WebEditorWrite.Removed {
        assertTrue("expected a delete: $answer", answer is WebEditorWrite.Removed)
        return answer as WebEditorWrite.Removed
    }

    private fun refused(answer: WebEditorWrite, expected: WebEditorRefusal): WebEditorWrite.Refused {
        assertTrue("expected a refusal: $answer", answer is WebEditorWrite.Refused)
        val refusal = answer as WebEditorWrite.Refused
        assertEquals("refused for the right reason: ${refusal.message}", expected, refusal.refusal)
        return refusal
    }

    @Test
    fun aSaveFromTheDeskChangesTheDetailsAndNothingElse() = runBlocking {
        val id = assets.createAsset(name = "Estuary road", geometry = line)
        val version = stateRecord(id).version

        val answer = saved(documents.save(id, editBody(version, name = "Estuary road, lower")))

        assertEquals("Estuary road, lower", answer.record.asset.name)
        assertTrue(
            "the answer carries the version the next edit quotes: ${answer.record.version}",
            answer.record.version.isNotBlank()
        )

        val stored = assets.getAsset(id)!!
        assertEquals("Estuary road, lower", stored.name)
        assertEquals("BIKE_TRACK", stored.kind)
        // Everything the page never saw, exactly as it was.
        assertTrue(stored.active)
        assertEquals("the length is still the line's own", 111.19, stored.lengthM, 0.5)
        assertEquals("and the line is still where it was", 2, assets.getAssetGeometry(id).pointCount)
    }

    @Test
    fun aVersionFromBeforeSomebodyRenamedItIsRefused() = runBlocking {
        val id = assets.createAsset(name = "Estuary road", geometry = line)
        val version = stateRecord(id).version

        // Somebody renames it on the phone while the card is open on the desk.
        assets.saveAssetEdits(assets.getAsset(id)!!.copy(name = "Estuary road, upper"))

        refused(documents.save(id, editBody(version, name = "Estuary road, lower")), WebEditorRefusal.STALE)

        assertEquals(
            "the phone's name is the one that survives",
            "Estuary road, upper",
            assets.getAsset(id)!!.name
        )
    }

    @Test
    fun aChangeTheDeskCannotWriteDoesNotRefuseTheEdit() = runBlocking {
        val id = assets.createAsset(name = "Estuary road", geometry = line)
        val version = stateRecord(id).version

        // The version is about the fields a desk may write, and a cached length is not one of them:
        // an edit refused because something the desk cannot change moved would be the feature getting
        // in the way of the work.
        assets.updateAsset(assets.getAsset(id)!!.copy(lengthM = 1234.0))

        saved(documents.save(id, editBody(version, name = "Estuary road, lower")))

        assertEquals("Estuary road, lower", assets.getAsset(id)!!.name)
    }

    @Test
    fun anAssetThePhoneDoesNotHaveIsRefusedRatherThanInvented() = runBlocking {
        refused(documents.save(4242L, editBody("whatever-version")), WebEditorRefusal.MISSING)

        assertNull("nothing was created by an edit to an id that names nothing", assets.getAsset(4242L))
    }

    @Test
    fun anArchivedAssetIsNotTheDeskToChange() = runBlocking {
        val id = assets.createAsset(name = "Old track", geometry = line)
        val version = stateRecord(id).version
        assets.updateAsset(assets.getAsset(id)!!.copy(active = false))

        // Archived assets are not in `/api/state`, so a desk holding one is holding a card written
        // against a page that has moved on - which is a refusal, not a resurrection.
        refused(documents.save(id, editBody(version, name = "Renamed anyway")), WebEditorRefusal.MISSING)

        assertEquals("Old track", assets.getAsset(id)!!.name)
    }

    @Test
    fun aLineWriteFromTheDeskIsRefusedAndThePhoneLineIsKept() = runBlocking {
        val id = assets.createAsset(name = "Estuary road", geometry = line)
        val version = stateRecord(id).version
        val longer = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001), GeoPoint(0.0, 0.002))

        val answer = refused(
            documents.save(id, editBodyWithLine(version, listOf(longer))),
            WebEditorRefusal.INVALID
        )

        assertTrue("says what happened: ${answer.message}", answer.message.contains("does not do"))
        assertEquals("and the phone's line is untouched", line, assets.getAssetGeometry(id).line)
        assertEquals("with its length, which nobody measured again", 111.19, assets.getAsset(id)!!.lengthM, 0.5)
    }

    @Test
    fun aCardFromBeforeAChangeOnThePhoneIsRefused() = runBlocking {
        val id = assets.createAsset(name = "Estuary road", geometry = line)
        val version = stateRecord(id).version
        // The phone is where a track's line is changed now, so this is the ordinary way a card goes
        // stale about geometry rather than an exotic one: somebody tidied the line on the phone.
        val tidiedOnThePhone = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.005))
        assets.replaceGeometry(id, AssetGeometry.of(tidiedOnThePhone))

        refused(documents.save(id, editBody(version, name = "Estuary road, lower")), WebEditorRefusal.STALE)

        assertEquals("the phone's line is the one that survives", tidiedOnThePhone, assets.getAssetGeometry(id).line)
    }

    @Test
    fun aTrackFileImportedOnTheDeskIsStoredLikeAnyOther() = runBlocking {
        val answer = created(documents.create(fileBody()))

        val id = answer.record.asset.id
        assertTrue("the answer is about a row that exists", id > 0)
        val stored = assets.getAsset(id)!!
        assertEquals("Gully track", stored.name)
        assertEquals("TRACK", stored.kind)
        assertTrue("a new asset is active", stored.active)
        assertEquals(line, assets.getAssetGeometry(id).line)
        assertEquals(
            "the length is worked out from the line, as it is for any track drawn on the phone",
            111.19,
            stored.lengthM,
            0.5
        )
        assertTrue("the new track has a version for the desk to quote", stateRecord(id).version.isNotBlank())
    }

    @Test
    fun aNewTrackWithNothingDrawnIsRefusedAndNothingIsStored() = runBlocking {
        val answer = refused(documents.create(fileBody(paths = emptyList())), WebEditorRefusal.INVALID)

        assertEquals("A line needs at least two points", answer.message)
        assertTrue(
            "nothing was created by a drawing with no line",
            assets.observeActiveAssets().first().isEmpty()
        )
    }

    @Test
    fun theDetailsOfATrackWithASideTrackStillSaveFromTheDesk() = runBlocking {
        val junction = GeoPoint(0.0, 0.0005)
        val id = assets.createAsset(
            name = "Gully track",
            geometry = AssetGeometry.of(
                listOf(line.first(), junction, line.last()),
                listOf(listOf(junction, GeoPoint(0.001, 0.0005)))
            )
        )
        val version = stateRecord(id).version

        val answer = saved(documents.save(id, editBody(version, name = "Gully track east")))

        assertEquals("Gully track east", answer.record.asset.name)
        assertEquals(
            "and the drawing is untouched by a write that carried no line",
            1,
            assets.getAssetGeometry(id).sideTracks.size
        )
    }

    @Test
    fun aTrackWithNothingOnItCanBeDeletedFromTheDesk() = runBlocking {
        val id = assets.createAsset(name = "Mis-drawn", geometry = line)
        val version = stateRecord(id).version

        val answer = removed(documents.remove(id, version))

        assertEquals("Mis-drawn is gone from the phone.", answer.message)
        assertNull("the row is gone", assets.getAsset(id))
        assertTrue(
            "and its points went with it, because a point belongs to an asset",
            assets.getAssetGeometry(id).paths.isEmpty()
        )
    }

    @Test
    fun aTrackWithARecordingOnItIsNotDeletedFromTheDesk() = runBlocking {
        // The case the schema makes sharp: a recording outlives its asset, so a delete would leave it
        // pointing at a number nothing answers to - which the recordings screen shows as "its asset has
        // since been deleted". Worth refusing for, and the sentence says which of the two it was.
        val id = assets.createAsset(name = "Estuary road", geometry = line)
        val sessionId = recordings.startRecording(name = "Morning run", assetId = id)
        val version = stateRecord(id).version

        val refusal = refused(documents.remove(id, version), WebEditorRefusal.IN_USE)

        assertTrue("names the trip: ${refusal.message}", refusal.message.contains("1 trip"))
        assertEquals(
            "and the recording still points at the track it was made against",
            id,
            recordings.getSession(sessionId)!!.assetId
        )
    }

    @Test
    fun aDeleteOfSomethingThatMovedIsRefusedRatherThanDone() = runBlocking {
        val id = assets.createAsset(name = "Mis-drawn", geometry = line)
        val version = stateRecord(id).version
        assets.saveAssetEdits(assets.getAsset(id)!!.copy(name = "Named on the phone"))

        refused(documents.remove(id, version), WebEditorRefusal.STALE)

        assertEquals("Named on the phone", assets.getAsset(id)!!.name)
    }

    @Test
    fun aDeleteThatDidNotSayWhatItReadIsRefusedRatherThanGuessed() = runBlocking {
        val id = assets.createAsset(name = "Mis-drawn", geometry = line)

        refused(documents.remove(id, version = null), WebEditorRefusal.INVALID)
        refused(documents.remove(id, version = ""), WebEditorRefusal.INVALID)

        assertEquals(
            "nothing was deleted by a request that did not say which track it read",
            id,
            assets.getAsset(id)!!.id
        )
    }

    @Test
    fun thePageIsServedWithTypesABrowserWillRun() = runBlocking {
        // Here rather than in a JVM test because `WebEditorDocuments` is built from repositories, and an
        // in-memory Room database is what this file has - and this is the one place the page's own file
        // types are decided.
        val pages = listOf(
            "index.html" to "text/html; charset=utf-8",
            "app.js" to "text/javascript; charset=utf-8",
            // `.mjs` is the one worth pinning: the desk's own pure modules are the same files node
            // imports to test them without a browser, and a browser refuses a module whose type it does
            // not read as JavaScript - which on a desk looks like a map that will not draw, with the
            // reason only in a console nobody opens.
            "geometry.mjs" to "text/javascript; charset=utf-8",
            // The other pure modules the page runs: what the glow under the picked-out track is made of,
            // where the desk was looking when it was last open, and the phone's own words for a figure.
            // All are imported the same way, so a type a browser will not read as JavaScript is the same
            // blank desk.
            "camera.mjs" to "text/javascript; charset=utf-8",
            "glow.mjs" to "text/javascript; charset=utf-8",
            "style.css" to "text/css; charset=utf-8",
            "vendor/maplibre-gl.js" to "text/javascript; charset=utf-8"
        )
        val serving = WebEditorDocuments(
            assets = assets,
            token = "7f3a9c7f3a9c7f3a9c7f3a9c7f3a9c7f",
            basemap = { error("nothing here reads the basemap") },
            position = { null },
            readPageFile = { name -> if (pages.any { it.first == name }) "x".toByteArray() else null },
            now = { 1_790_000_000_000L }
        )

        val root = serving.page("/")
        assertNotNull("the address with no path on the end is the page itself", root)
        assertEquals("text/html; charset=utf-8", root!!.contentType)

        for ((name, type) in pages) {
            val answer = serving.page("/$name")
            assertNotNull("the editor ships $name", answer)
            assertEquals("$name is served as the type a browser will run", type, answer!!.contentType)
        }

        assertNull("a file the editor does not ship is nothing", serving.page("/not-a-file.js"))
        assertNull(
            "and a path that walks out of the page's own directory is a path nothing ships",
            serving.page("/../../databases/trail_day.db")
        )
    }

    /**
     * What the documents say about the token, in the one place the token can be seen from outside.
     *
     * The style is where it matters: MapLibre asks for tiles and features itself, so the token has to
     * be in the URLs the style hands over or the map is blank - and a run with no token must hand
     * over URLs with no token in them, which is the same statement the bare address on the card makes.
     */
    @Test
    fun theDocumentsCarryTheTokenWhenThereIsOneAndNothingWhenThereIsNot() = runBlocking {
        fun serving(token: String?) = WebEditorDocuments(
            assets = assets,
            token = token,
            basemap = { Basemap.DEFAULT },
            position = { null },
            readPageFile = { null },
            now = { 1_790_000_000_000L }
        )

        val guarded = serving("7f3a9c7f3a9c7f3a9c7f3a9c7f3a9c7f").style("192.168.1.23:8799")
        assertTrue(
            "the tiles and the features carry the token: $guarded",
            guarded.contains("/api/assets.geojson?k=7f3a9c7f3a9c7f3a9c7f3a9c7f3a9c7f")
        )
        assertTrue(
            "and the tiles, which MapLibre fetches on its own: $guarded",
            guarded.contains("?k=7f3a9c7f3a9c7f3a9c7f3a9c7f3a9c7f")
        )

        val open = serving(null).style("192.168.1.23:8799")
        assertFalse("a run with no token hands out no token: $open", open.contains("k="))
        assertTrue(
            "and still says where the work, the tiles and a name's letters are: $open",
            open.contains("http://192.168.1.23:8799/api/assets.geojson") &&
                open.contains("http://192.168.1.23:8799/tiles/") &&
                open.contains("http://192.168.1.23:8799/fonts/{fontstack}/{range}.pbf")
        )
    }
}
