package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.doc.ArcGisDoc
import nz.mckenzie.trailday.doc.DocDownloader
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.reference.HiddenTrack
import nz.mckenzie.trailday.domain.reference.HiddenTracks
import nz.mckenzie.trailday.offline.OfflineDocStore
import nz.mckenzie.trailday.tracking.DevicePosition
import nz.mckenzie.trailday.tracking.LocationSource
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A track the walker has hidden, as the map's own view model reads it.
 *
 * The domain's tests hold the rule still - a reference hides its own line and nothing else. What this pins
 * is the **wiring around it**: that a hidden track leaves the map's drawing while the rest of DOC's
 * material stays, and that the line comes back when it is shown again. That is what a walker actually
 * sees, and it is the part a later build could quietly break by filtering in the wrong place.
 *
 * A live [MutableStateFlow] stands in for the preference, so the test can hide and show a track the way the
 * page and Settings do, rather than only at construction.
 */
@RunWith(AndroidJUnit4::class)
class MapHiddenTrackTest {

    private lateinit var context: Context
    private lateinit var db: TrailDayDatabase
    private lateinit var assetRepository: AssetRepository

    private class NoFixes : LocationSource {
        override fun updates(): Flow<GeoPoint> = emptyFlow()
        override suspend fun currentLocation(): GeoPoint? = null
    }

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TrailDayDatabase::class.java).build()
        assetRepository = AssetRepository(db)
        SettingsRepository(context).showEveryHiddenTrack()
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        DevicePosition.useForTest(null)
        SettingsRepository(context).showEveryHiddenTrack()
        Unit
    }

    private fun line(id: Long, name: String) = DocItem(
        source = DocSource.TRACKS,
        objectId = id,
        name = name,
        paths = listOf(
            listOf(
                GeoPoint(lat = -44.9000, lng = 168.1000),
                GeoPoint(lat = -44.9050, lng = 168.1050)
            )
        )
    )

    @Test
    fun aHiddenTrackLeavesTheMapsDrawingAndComesBackWhenItIsShown() = runBlocking {
        val wrong = line(1, "Wrong Track")
        val good = line(2, "Good Track")
        val docs = OfflineDocStore(dao = db.offlineDocItemDao(), downloader = DocDownloader(ArcGisDoc()))
        docs.store(listOf(wrong, good))

        val hidden = MutableStateFlow(HiddenTracks.NONE)
        val viewModel = MapViewModel(
            assetRepository = assetRepository,
            settingsRepository = SettingsRepository(context),
            locationSource = NoFixes(),
            docStore = docs,
            loadGeometry = { AssetGeometry.NONE },
            hiddenTracks = hidden
        )

        val before = withTimeout(10_000) {
            viewModel.docGeoJson.first { it.contains("Good Track") }
        }
        assertTrue("both lines are drawn to begin with", before.contains("Wrong Track"))

        hidden.value = HiddenTracks.NONE.hiding(HiddenTrack(wrong.sourceRef, wrong.name))

        val after = withTimeout(10_000) {
            viewModel.docGeoJson.first { !it.contains("Wrong Track") }
        }
        assertTrue("the line beside it is untouched", after.contains("Good Track"))

        hidden.value = HiddenTracks.NONE

        val again = withTimeout(10_000) {
            viewModel.docGeoJson.first { it.contains("Wrong Track") }
        }
        assertTrue("and it comes back when it is shown again", again.contains("Good Track"))
    }

    @Test
    fun aHiddenTrackCannotBeTappedOnTheMap() = runBlocking {
        val track = line(7, "Wrong Track")
        val docs = OfflineDocStore(dao = db.offlineDocItemDao(), downloader = DocDownloader(ArcGisDoc()))
        docs.store(listOf(track))

        val hidden = MutableStateFlow(HiddenTracks.NONE)
        val viewModel = MapViewModel(
            assetRepository = assetRepository,
            settingsRepository = SettingsRepository(context),
            locationSource = NoFixes(),
            docStore = docs,
            loadGeometry = { AssetGeometry.NONE },
            hiddenTracks = hidden
        )

        // Let the map read the cache once, so the tap is answered from a settled list.
        withTimeout(10_000) { viewModel.docGeoJson.first { it.contains("Wrong Track") } }
        hidden.value = HiddenTracks.NONE.hiding(HiddenTrack(track.sourceRef, track.name))
        withTimeout(10_000) { viewModel.docGeoJson.first { !it.contains("Wrong Track") } }

        val picked = viewModel.pickDocAt(-44.9010, 168.1010, 200.0)
        assertFalse("a line nobody can see is not a line under the finger", picked?.name == "Wrong Track")
    }
}
