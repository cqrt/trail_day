package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.RecordingRepository
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.look.Look
import nz.mckenzie.trailday.domain.recording.ActivityMode
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.map.EndsGeoJson
import nz.mckenzie.trailday.tracking.LocationSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the map draws from recordings.
 *
 * A finished pass linked to one of the walker's line places must not be drawn twice: the place is
 * the line, and the raw pass is its evidence in Activities and on the place's page. A pass with no
 * line place - standalone, following DOC, or linked to an old point place - is the only line the
 * map has for it, so it is drawn.
 */
@RunWith(AndroidJUnit4::class)
class MapRecordingsTest {

    private lateinit var context: Context
    private lateinit var db: TrailDayDatabase
    private lateinit var assetRepository: AssetRepository
    private lateinit var recordings: RecordingRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TrailDayDatabase::class.java).build()
        assetRepository = AssetRepository(db)
        recordings = RecordingRepository(db)
        SettingsRepository(context).showAllYourPlaces()
    }

    @After
    fun tearDown() {
        // A view model may still be observing the tables; closing the database underneath it takes
        // the instrumentation process down. The in-memory database goes with the process.
    }

    private object NoLocation : LocationSource {
        override fun updates(): Flow<GeoPoint> = emptyFlow()
        override suspend fun currentLocation(): GeoPoint? = null
    }

    private fun viewModel() = MapViewModel(
        assetRepository = assetRepository,
        settingsRepository = SettingsRepository(context),
        locationSource = NoLocation,
        recordingRepository = recordings
    )

    private suspend fun pass(
        name: String,
        points: List<GeoPoint>,
        linkedTo: Long? = null,
        mode: ActivityMode = ActivityMode.WALK
    ) {
        val id = recordings.startRecording(name = name, assetId = linkedTo)
        points.forEach { recordings.appendPoint(id, it) }
        recordings.finishRecording(id, distanceM = 100.0, mode = mode)
    }

    @Test
    fun aPassLinkedToOneOfYourLinesIsDrawnAsThatPlaceOnly() = runBlocking {
        val trackLine = listOf(
            GeoPoint(-41.5000, 173.9500),
            GeoPoint(-41.5010, 173.9600)
        )
        val trackId = assetRepository.createAsset("Ridge track", trackLine)
        val spotId = assetRepository.createAsset(
            name = "Lookout",
            geometry = listOf(trackLine.first()),
            kind = AssetKind.WAYPOINT,
            shape = AssetShape.POINT
        )

        pass("Standalone", trackLine)
        pass("Followed track", trackLine, linkedTo = trackId)
        pass("Old spot pass", trackLine, linkedTo = spotId)

        val viewModel = viewModel()
        val drawn = withTimeout(5_000) {
            // Wait for the places list as well as the sessions: the first combine can still be
            // holding its empty seed before the database has answered.
            viewModel.yourTracksGeoJson.first {
                it.contains("\"name\":\"Standalone\"") && !it.contains("\"name\":\"Followed track\"")
            }
        }

        assertTrue("a standalone pass has no place to represent it", drawn.contains("Standalone"))
        assertTrue("a pass linked to an old point still has no line place", drawn.contains("Old spot pass"))
        assertFalse(
            "the line place already represents this ground",
            drawn.contains("Followed track")
        )

        val counts = withTimeout(5_000) {
            viewModel.yourPlaceCounts.first { it.recordings == 2 }
        }
        assertEquals("the tile counts the two raw lines it controls", 2, counts.recordings)
        assertEquals("and the place is counted as a track", 1, counts.tracks)
    }

    @Test
    fun aRideIsDrawnAsARideAndAWalkAsAWalk() = runBlocking {        val line = listOf(
            GeoPoint(-41.5000, 173.9500),
            GeoPoint(-41.5010, 173.9600)
        )
        pass("Taupo ride", line, mode = ActivityMode.RIDE)
        pass("Home block", line, mode = ActivityMode.WALK)

        val drawn = withTimeout(5_000) {
            viewModel().yourTracksGeoJson.first {
                it.contains("\"name\":\"Taupo ride\"") && it.contains("\"name\":\"Home block\"")
            }
        }

        val bikeColour = Look.colourOf(AssetKind.BIKE_TRACK).hex
        assertTrue("a ride is drawn in the walker's bike colour: $drawn", drawn.contains("\"stroke\":\"$bikeColour\""))
        assertTrue("and as a bike track, so the map dashes it", drawn.contains("\"kind\":\"BIKE_TRACK\""))
        assertTrue("a walk keeps the recorded green", drawn.contains("\"stroke\":\"${AssetColors.RECORDED}\""))
        assertTrue("and its own kind", drawn.contains("\"kind\":\"TRACK\""))
        assertTrue("both say they are recorded, which is what edges them in gold", drawn.contains("\"recorded\":true"))
        assertFalse(
            "and neither is cut for a name: a recorded line is not named on the map",
            drawn.contains("carriesName")
        )
        assertEquals(
            "both lines are whole: two features, one per trip",
            2,
            Regex("\"type\":\"Feature\",").findAll(drawn).count()
        )
    }

    /**
     * The two marks an activity's page puts on its own map, read through the page's own view model.
     *
     * A trip's ends are its first and last fixes and nothing in between - the days in the middle of a
     * walk are the line, not ends - and a trip with one fix has no two ends to tell apart.
     */
    @Test
    fun aTripsEndsAreItsFirstFixAndItsLast() = runBlocking {
        val points = listOf(
            GeoPoint(-41.5000, 173.9500),
            GeoPoint(-41.5010, 173.9600),
            GeoPoint(-41.5020, 173.9700)
        )
        val id = recordings.startRecording(name = "Three fixes")
        points.forEach { recordings.appendPoint(id, it) }
        recordings.finishRecording(id, distanceM = 100.0)

        val ends = withTimeout(5_000) {
            detailViewModel(id).endsGeoJson.first { it != EndsGeoJson.EMPTY }
        }

        assertEquals("one mark per end", 2, Regex("\"type\":\"Feature\",").findAll(ends).count())
        assertTrue("the start is the first fix: $ends", ends.contains("[173.9500000,-41.5000000]"))
        assertTrue("the finish is the last", ends.contains("[173.9700000,-41.5020000]"))
        assertFalse(
            "the fix in the middle is not an end",
            ends.contains("[173.9600000,-41.5010000]")
        )
        assertTrue("the dot and the flag are told apart", ends.contains("\"part\":\"start\""))
        assertTrue(ends.contains("\"part\":\"finish\""))
    }

    @Test
    fun aTripOfOneFixHasNoEnds() = runBlocking {
        val id = recordings.startRecording(name = "One fix")
        recordings.appendPoint(id, GeoPoint(-41.5000, 173.9500))

        val viewModel = detailViewModel(id)
        val detail = withTimeout(5_000) { viewModel.detail.first { it != null } }

        assertEquals("the page has its one fix", 1, detail!!.points.size)
        assertEquals(
            "one point has no two ends to tell apart",
            EndsGeoJson.EMPTY,
            viewModel.endsGeoJson.first()
        )
    }

    /** One activity's own page, with no publisher cache: nothing here followed anything. */
    private fun detailViewModel(sessionId: Long) = RecordingDetailViewModel(
        sessionId = sessionId,
        recordings = recordings,
        assetRepository = assetRepository,
        settingsRepository = SettingsRepository(context)
    )
}
