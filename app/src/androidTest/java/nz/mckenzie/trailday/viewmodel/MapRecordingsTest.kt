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
        SettingsRepository(context).setAllYourPlacesHidden(false)
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
        linkedTo: Long? = null
    ) {
        val id = recordings.startRecording(name = name, assetId = linkedTo)
        points.forEach { recordings.appendPoint(id, it) }
        recordings.finishRecording(id, distanceM = 100.0)
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
}
