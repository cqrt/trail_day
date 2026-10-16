package nz.mckenzie.trailday.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.track.GpxParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetDataTest {

    private lateinit var db: TrailDayDatabase
    private lateinit var assetRepository: AssetRepository

    /** ~111.19 m long, eastwards along the equator. */
    private val line = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001))

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TrailDayDatabase::class.java).build()
        assetRepository = AssetRepository(db)
    }

    @After
    fun tearDown() = db.close()

    /** The id of the asset made last. */
    private suspend fun newestAssetId(): Long = assetRepository
        .observeActiveAssets()
        .first()
        .maxOf { it.id }

    /**
     * A track with a side track off it, stored and read back as the two paths it is.
     *
     * The claim this pins is the length: **each path counted once**. The same ground drawn the old way
     * - up the spur and back down it as part of the line - is a whole spur too long.
     */
    @Test
    fun aTrackWithASideTrackIsStoredAsPathsAndItsLengthCountedOnce() = runBlocking {
        val junction = GeoPoint(0.0, 0.0005)
        val spurEnd = GeoPoint(0.001, 0.0005)
        val lineWithSpur = listOf(GeoPoint(0.0, 0.0), junction, GeoPoint(0.0, 0.001))
        val spur = listOf(junction, spurEnd)

        val id = assetRepository.createAsset(
            name = "Gully track",
            geometry = AssetGeometry.of(lineWithSpur, listOf(spur))
        )

        val stored = assetRepository.getAssetGeometry(id)
        assertEquals("the line and its side track", 2, stored.paths.size)
        assertEquals(lineWithSpur, stored.line)
        assertEquals(listOf(spur), stored.sideTracks)
        assertEquals(
            "the junction is in both paths, at the same two numbers",
            stored.line[1],
            stored.sideTracks[0].first()
        )
        assertEquals("every vertex of every path", 5, stored.pointCount)
        assertEquals(
            "222 m of track, not the 333 m walking the spur twice would read as",
            222.4,
            assetRepository.getAsset(id)!!.lengthM,
            0.5
        )

        // And the map reads the same thing: one asset, both of its paths, so a tap on the spur opens
        // the track it hangs off.
        assertEquals(2, assetRepository.allAssetGeometry()[id]!!.paths.size)
    }

    @Test
    fun createAssetStoresGeometryAndLength() = runBlocking {
        val id = assetRepository.createAsset(name = "Track 4", geometry = line)

        val geometry = assetRepository.getAssetGeometry(id)
        assertEquals(2, geometry.pointCount)
        assertEquals(0.001, geometry.line[1].lng, 1e-9)
        assertEquals(111.19, assetRepository.getAsset(id)!!.lengthM, 1.0)
    }

    @Test
    fun deletingATrackCascadesItsGeometry() = runBlocking {
        val id = assetRepository.createAsset(name = "Track 4", geometry = line)

        assetRepository.deleteAsset(id)

        assertNull(assetRepository.getAsset(id))
        assertTrue(assetRepository.getAssetGeometry(id).paths.isEmpty())
    }

    @Test
    fun exportedGpxRoundTripsThroughTheParser() = runBlocking {
        val id = assetRepository.createAsset(name = "Block 4 & 5", geometry = line)

        val gpx = assetRepository.exportAssetGpx(id)
        assertNotNull(gpx)
        assertTrue(gpx!!.contains("Block 4 &amp; 5"))

        val parsed = GpxParser.parse(gpx)
        assertEquals(2, parsed.size)
        assertEquals(0.001, parsed[1].lng, 1e-7)
    }

    @Test
    fun importingGpxCreatesATrackWithTheSameGeometry() = runBlocking {
        val source = assetRepository.createAsset(name = "Planned", geometry = line)
        val gpx = assetRepository.exportAssetGpx(source)!!

        val imported = assetRepository.importAssetTracks(fileName = "Imported", file = gpx.toByteArray())
        val id = newestAssetId()

        assertEquals(2, assetRepository.getAssetGeometry(id).pointCount)
        assertEquals(111.19, assetRepository.getAsset(id)!!.lengthM, 1.0)
        assertEquals(1, imported.size)
        assertEquals("one line, and nothing else was in the file", 0, imported.single().reading.sideTracks)
        assertFalse(imported.single().reading.segmentsDidNotMeet)
    }

    /**
     * A file's heights are kept, and a track exported again still carries them.
     */
    @Test
    fun aTrackImportedWithHeightsKeepsThemAndExportsThemAgain() = runBlocking {
        val gpx = """
            <?xml version="1.0"?>
            <gpx version="1.1"><trk><trkseg>
              <trkpt lat="0.0" lon="0.0"><ele>100.0</ele></trkpt>
              <trkpt lat="0.001" lon="0.0"><ele>160.0</ele></trkpt>
              <trkpt lat="0.002" lon="0.0"><ele>130.0</ele></trkpt>
            </trkseg></trk></gpx>
        """.trimIndent()

        assetRepository.importAssetTracks(fileName = "Kiwi Burn", file = gpx.toByteArray())
        val id = newestAssetId()

        val stored = assetRepository.getAssetGeometry(id)
        assertEquals(100.0, stored.line[0].altitudeM!!, 1e-9)
        assertEquals(160.0, stored.line[1].altitudeM!!, 1e-9)
        assertEquals(130.0, stored.line[2].altitudeM!!, 1e-9)

        val again = assetRepository.exportAssetGpx(id)!!
        assertTrue("the exported file still says how high it is", again.contains("<ele>160"))
    }

    /**
     * The file a tool writes for a track with a side track, read back as the two paths it is.
     */
    @Test
    fun importingATrackWithASideTrackGivesBackBothPaths() = runBlocking {
        val junction = GeoPoint(0.0, 0.0005)
        val spur = listOf(junction, GeoPoint(0.001, 0.0005))
        val source = assetRepository.createAsset(
            name = "Gully track",
            geometry = AssetGeometry.of(listOf(GeoPoint(0.0, 0.0), junction, GeoPoint(0.0, 0.001)), listOf(spur))
        )
        val gpx = assetRepository.exportAssetGpx(source)!!

        val imported = assetRepository.importAssetTracks(fileName = "Gully track again", file = gpx.toByteArray())
        val id = newestAssetId()

        val stored = assetRepository.getAssetGeometry(id)
        assertEquals("the line and its side track", 2, stored.paths.size)
        assertEquals(listOf(spur), stored.sideTracks)
        assertEquals("and the side track starts on the line's own vertex", stored.line[1], stored.sideTracks[0].first())
        assertEquals(1, imported.single().reading.sideTracks)
        assertFalse(imported.single().reading.segmentsDidNotMeet)
        assertEquals(
            "222 m of track, each path counted once",
            222.3,
            assetRepository.getAsset(id)!!.lengthM,
            1.0
        )
    }

    @Test
    fun importingAFileWhoseSegmentsDoNotMeetReadsItAsOneLineAndSaysSo() = runBlocking {
        // Two segments that never touch: a tool that cuts a line up for its own reasons. That is not a
        // refusal - it is what this app has always imported - but the answer says what happened, because
        // a track with a jump in it is worth knowing about.
        val gpx = """
            <?xml version="1.0"?>
            <gpx version="1.1"><trk>
              <trkseg><trkpt lat="-41.0" lon="174.0"/><trkpt lat="-41.1" lon="174.1"/></trkseg>
              <trkseg><trkpt lat="-42.0" lon="175.0"/><trkpt lat="-42.1" lon="175.1"/></trkseg>
            </trk></gpx>
        """.trimIndent()

        val imported = assetRepository.importAssetTracks(fileName = "Two fences", file = gpx.toByteArray())

        val stored = assetRepository.getAssetGeometry(newestAssetId())
        assertEquals("one line, every point in it", 1, stored.paths.size)
        assertEquals(4, stored.pointCount)
        assertEquals(0, imported.single().reading.sideTracks)
        assertTrue("and the answer says the file's segments did not meet", imported.single().reading.segmentsDidNotMeet)
    }

    @Test
    fun importingASinglePointGpxIsRejected() = runBlocking {
        val gpx = """
            <?xml version="1.0"?>
            <gpx version="1.1"><trk><trkseg><trkpt lat="-41.0" lon="174.0"/></trkseg></trk></gpx>
        """.trimIndent()

        val failure = runCatching { assetRepository.importAssetTracks(fileName = "Bad", file = gpx.toByteArray()) }

        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }
}

