package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.doc.ArcGisDoc
import nz.mckenzie.trailday.doc.DocDownloader
import nz.mckenzie.trailday.domain.doc.DocHitTest
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.offline.OfflineDocStore
import nz.mckenzie.trailday.tracking.DevicePosition
import nz.mckenzie.trailday.tracking.LocationSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The card at the bottom of the map, as the map's own view model fills it in.
 *
 * The card's arithmetic is held still by the domain's own tests; what this pins is the **wiring**: that a tap
 * asks DOC's cache rather than the map's drawing, that the distance is the phone's rather than the finger's,
 * and that a tap on nothing puts the card away.
 */
@RunWith(AndroidJUnit4::class)
class MapDocPickTest {

    private lateinit var context: Context
    private lateinit var db: TrailDayDatabase
    private lateinit var assetRepository: AssetRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TrailDayDatabase::class.java).build()
        assetRepository = AssetRepository(db)
    }

    @After
    fun tearDown() {
        // The position is a process-wide holder, so a test that sets it has to put it back.
        DevicePosition.useForTest(null)
    }

    /** A source that hands over the fixes it was given. */
    private class FakeLocation(private val fixes: List<GeoPoint>) : LocationSource {
        override fun updates(): Flow<GeoPoint> = fixes.asFlow()
        override suspend fun currentLocation(): GeoPoint? = fixes.lastOrNull()
    }

    private fun fix(lat: Double, lng: Double) = GeoPoint(lat = lat, lng = lng, accuracyM = 5f)

    @Test
    fun pickingOneOfDocsTracksPutsItOnTheCard() = runBlocking {
        // A track DOC publishes through the walker's own feet, and a hut a kilometre off: a tap on the line
        // is a tap on the track, not on the hut.
        val track = DocItem(
            source = DocSource.TRACKS,
            objectId = 42,
            name = "Routeburn Track",
            paths = listOf(
                listOf(
                    GeoPoint(lat = -44.9000, lng = 168.1000),
                    GeoPoint(lat = -44.9050, lng = 168.1050)
                )
            ),
            facts = mapOf("SubObjectType" to "Great Walk", "Region" to "Fiordland")
        )
        val hut = DocItem(
            source = DocSource.HUTS,
            objectId = 43,
            name = "Mackenzie Hut",
            paths = listOf(listOf(GeoPoint(lat = -44.9090, lng = 168.1090))),
            facts = mapOf("place" to "Fiordland")
        )
        val docs = OfflineDocStore(dao = db.offlineDocItemDao(), downloader = DocDownloader(ArcGisDoc()))
        docs.store(listOf(track, hut))
        DevicePosition.useForTest(FakeLocation(listOf(fix(-44.9000, 168.1000))))

        val viewModel = MapViewModel(
            assetRepository = assetRepository,
            settingsRepository = SettingsRepository(context),
            locationSource = FakeLocation(emptyList()),
            docStore = docs,
            loadGeometry = { AssetGeometry.NONE }
        )

        // Halfway along the line, with a walking-scale radius.
        viewModel.pickDocAt(-44.9010, 168.1010, DocHitTest.DEFAULT_TOLERANCE_M)

        // The picked thing and the phone's own fix arrive separately, so the test waits for the card it is
        // asserting about - the one with the distance worked out - rather than for the first thing to turn up.
        val pick = withTimeout(5_000) {
            viewModel.docPick.first { it != null && it.distanceM != null }
        }!!
        assertEquals("the track under the finger, not the hut nearby", "Routeburn Track", pick.item.name)
        assertEquals("with what DOC says about it", DocSource.TRACKS, pick.item.source)
        assertTrue(
            "measured from where the phone is, not from where the finger was",
            (pick.distanceM ?: Double.MAX_VALUE) < 200.0
        )
        assertNotNull("and how long getting there takes on foot", pick.onFootSeconds())

        // A tap on empty ground lets it go: nothing tapped is no card.
        viewModel.pickDocAt(-45.5000, 169.0000, DocHitTest.DEFAULT_TOLERANCE_M)
        assertNull(viewModel.pickedDoc.value)
    }

    @Test
    fun aLayerThatIsSwitchedOffIsNotTappable() = runBlocking {
        // A hut of the walker's and a hut DOC publishes, both under the same finger. With the huts switched
        // off, neither answers: a tap where nothing is drawn must not open something the walker cannot see.
        val hut = DocItem(
            source = DocSource.HUTS,
            objectId = 1,
            name = "Angelus Hut",
            paths = listOf(listOf(GeoPoint(lat = -41.6200, lng = 172.6500))),
            facts = mapOf("place" to "Nelson Lakes")
        )
        val docs = OfflineDocStore(dao = db.offlineDocItemDao(), downloader = DocDownloader(ArcGisDoc()))
        docs.store(listOf(hut))
        val spot = AssetGeometry(paths = listOf(listOf(GeoPoint(lat = -41.6201, lng = 172.6501))))
        val spotId = assetRepository.createAsset(
            name = "My hut",
            geometry = spot,
            kind = nz.mckenzie.trailday.domain.asset.AssetKind.HUT,
            shape = nz.mckenzie.trailday.domain.asset.AssetShape.POINT
        )
        DevicePosition.useForTest(FakeLocation(listOf(fix(-41.6200, 172.6500))))

        val settings = SettingsRepository(context)
        val viewModel = MapViewModel(
            assetRepository = assetRepository,
            settingsRepository = settings,
            locationSource = FakeLocation(emptyList()),
            docStore = docs,
            loadGeometry = { spot }
        )

        // The places and their lines arrive as the map's own flows do, so the test watches them until the
        // place is there to be tapped - see `assets` and `geometryByTrack`.
        val watching = launch { viewModel.assets.collect { } }
        try {
            withTimeout(5_000) {
                while (viewModel.assetAt(-41.6201, 172.6501, 5.0) == null) delay(50)
            }
            assertEquals("with its layer on, the place is tappable", spotId, viewModel.assetAt(-41.6201, 172.6501, 5.0))
            viewModel.pickDocAt(-41.6200, 172.6500, 10.0)
            assertNotNull("and DOC's hut is too", viewModel.pickedDoc.value)

            settings.hideMapLayer(nz.mckenzie.trailday.domain.asset.AssetLayer.HUTS, true)

            // The switch is a stored preference and the map reads it through its own flow, so the test waits
            // for the layer to be shut rather than for the write to return: a loaded machine can take a
            // moment between the two, and reading too early made a passing change fail in CI.
            withTimeout(5_000) {
                while (viewModel.assetAt(-41.6201, 172.6501, 5.0) != null) delay(50)
            }
            assertNull("switched off, the place is not tappable", viewModel.assetAt(-41.6201, 172.6501, 5.0))
            viewModel.pickDocAt(-41.6200, 172.6500, 10.0)
            assertNull("and neither is DOC's hut", viewModel.pickedDoc.value)
        } finally {
            watching.cancel()
        }
    }

    @Test
    fun withNothingInTheCacheATapPutsTheCardAway() = runBlocking {
        DevicePosition.useForTest(FakeLocation(listOf(fix(-46.6000, 168.3000))))

        val viewModel = MapViewModel(
            assetRepository = assetRepository,
            settingsRepository = SettingsRepository(context),
            locationSource = FakeLocation(emptyList()),
            loadGeometry = { AssetGeometry.NONE }
        )

        viewModel.pickDocAt(-46.6000, 168.3000, DocHitTest.DEFAULT_TOLERANCE_M)

        assertNull("nothing in the cache is nothing on the card", viewModel.pickedDoc.value)
    }
}
