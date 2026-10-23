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
import nz.mckenzie.trailday.domain.asset.AssetLayer
import nz.mckenzie.trailday.domain.asset.YourPlaceLayer
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
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TrailDayDatabase::class.java).build()
        assetRepository = AssetRepository(db)
        SettingsRepository(context).apply {
            setHiddenMapLayers(emptySet())
            setAllYourPlacesHidden(false)
        }
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        // The position and the switches are process-wide, so a test that sets them puts them back.
        DevicePosition.useForTest(null)
        SettingsRepository(context).apply {
            setHiddenMapLayers(emptySet())
            setAllYourPlacesHidden(false)
        }
        Unit
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
        assertNotNull(
            "the map has a frame to return to after a tab change",
            viewModel.selectionFrame.value
        )

        // A tap on empty ground lets it go: nothing tapped is no card.
        viewModel.pickDocAt(-45.5000, 169.0000, DocHitTest.DEFAULT_TOLERANCE_M)
        assertNull(viewModel.pickedDoc.value)
        assertNull(viewModel.selectionFrame.value)
    }

    @Test
    fun aTapThatOpensTheThingsOwnPageLeavesNoCardBehind() = runBlocking {
        // The map hands a tap straight to the thing's own page, which is the card at full size. The map's own
        // card must not be raised behind it - that is what put the old card back on the map when the walker
        // pressed back from the page - but the frame must be, so coming back lands on the line that was
        // tapped rather than on wherever the map happened to be.
        val track = DocItem(
            source = DocSource.TRACKS,
            objectId = 44,
            name = "Greenstone Track",
            paths = listOf(
                listOf(
                    GeoPoint(lat = -44.9000, lng = 168.1000),
                    GeoPoint(lat = -44.9050, lng = 168.1050)
                )
            )
        )
        val docs = OfflineDocStore(dao = db.offlineDocItemDao(), downloader = DocDownloader(ArcGisDoc()))
        docs.store(listOf(track))
        DevicePosition.useForTest(FakeLocation(listOf(fix(-44.9000, 168.1000))))

        val viewModel = MapViewModel(
            assetRepository = assetRepository,
            settingsRepository = SettingsRepository(context),
            locationSource = FakeLocation(emptyList()),
            docStore = docs,
            loadGeometry = { AssetGeometry.NONE }
        )

        val picked = viewModel.pickDocAt(
            -44.9010,
            168.1010,
            DocHitTest.DEFAULT_TOLERANCE_M,
            raiseCard = false
        )

        assertEquals("the track under the finger is still the answer", "Greenstone Track", picked?.name)
        assertNull("and no card is raised for it", viewModel.pickedDoc.value)
        assertNotNull("but the map still has a frame to return to", viewModel.selectionFrame.value)
    }

    @Test
    fun aLayerThatIsSwitchedOffIsNotTappable() = runBlocking {
        // A hut of the walker's and a hut DOC publishes, both under the same finger. They are on two
        // different switches now: DOC's Huts and Your Places' Spots. Each must answer only while its
        // own switch is on.
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

            settings.hideMapLayer(AssetLayer.HUTS, true)

            // The switch is a stored preference and the map reads it through its own flow, so the test waits
            // for the layer to be shut rather than for the write to return: a loaded machine can take a
            // moment between the two, and reading too early made a passing change fail in CI.
            withTimeout(5_000) {
                viewModel.hiddenLayers.first { AssetLayer.HUTS in it }
            }
            assertEquals(
                "DOC's hut switch does not take the walker's own point away",
                spotId,
                viewModel.assetAt(-41.6201, 172.6501, 5.0)
            )
            viewModel.pickDocAt(-41.6200, 172.6500, 10.0)
            assertNull("but DOC's hut is not tappable", viewModel.pickedDoc.value)

            settings.setYourPlaceHidden(YourPlaceLayer.SPOTS, true)
            withTimeout(5_000) {
                viewModel.hiddenYourPlaces.first { YourPlaceLayer.SPOTS in it }
            }
            assertNull(
                "the walker's own point goes with its own tile",
                viewModel.assetAt(-41.6201, 172.6501, 5.0)
            )
        } finally {
            watching.cancel()
            settings.hideMapLayer(AssetLayer.HUTS, false)
            settings.setYourPlaceHidden(YourPlaceLayer.SPOTS, false)
        }
    }

    @Test
    fun aRideableTrackIsTappableWhenOnlyBikeTracksAreShown() = runBlocking {
        // DOC publishes a track and a bike point sitting on it under the same name. The map draws
        // that track under the Bike trails switch, so with Tracks off it must still answer a tap.
        val track = DocItem(
            source = DocSource.TRACKS,
            objectId = 70,
            name = "Estuary ride",
            paths = listOf(
                listOf(
                    GeoPoint(lat = -41.5000, lng = 173.9600),
                    GeoPoint(lat = -41.5100, lng = 173.9700)
                )
            )
        )
        val bike = DocItem(
            source = DocSource.BIKES,
            objectId = 71,
            name = "Estuary ride",
            paths = listOf(listOf(GeoPoint(lat = -41.5000, lng = 173.9600))),
            facts = mapOf("Difficulty" to "Intermediate")
        )
        val docs = OfflineDocStore(dao = db.offlineDocItemDao(), downloader = DocDownloader(ArcGisDoc()))
        docs.store(listOf(track, bike))
        val settings = SettingsRepository(context)
        settings.hideMapLayer(AssetLayer.TRACKS, true)
        DevicePosition.useForTest(FakeLocation(listOf(fix(-41.5000, 173.9600))))

        val viewModel = MapViewModel(
            assetRepository = assetRepository,
            settingsRepository = settings,
            locationSource = FakeLocation(emptyList()),
            docStore = docs,
            loadGeometry = { AssetGeometry.NONE }
        )
        withTimeout(5_000) {
            viewModel.hiddenLayers.first { AssetLayer.TRACKS in it }
        }

        viewModel.pickDocAt(-41.5001, 173.9601, DocHitTest.DEFAULT_TOLERANCE_M)

        val pick = withTimeout(5_000) {
            viewModel.docPick.first { it != null && it.bike != null }
        }!!
        assertEquals("the rideable track answers under the bike switch", "Estuary ride", pick.item.name)
        assertEquals(DocSource.TRACKS, pick.item.source)
        assertNotNull("and the card knows it can be ridden", pick.bike)
    }

    @Test
    fun aDualUseTrackIsStillTappableWithBikeTrailsOff() = runBlocking {
        // The same pairing, but the walker is looking at Tracks with Bike trails off. The line is
        // walkable, so it must still be there and still answer a finger.
        val track = DocItem(
            source = DocSource.TRACKS,
            objectId = 80,
            name = "Valley dual use",
            paths = listOf(
                listOf(
                    GeoPoint(lat = -41.5000, lng = 173.9600),
                    GeoPoint(lat = -41.5100, lng = 173.9700)
                )
            )
        )
        val bike = DocItem(
            source = DocSource.BIKES,
            objectId = 81,
            name = "Valley dual use",
            paths = listOf(listOf(GeoPoint(lat = -41.5000, lng = 173.9600)))
        )
        val docs = OfflineDocStore(dao = db.offlineDocItemDao(), downloader = DocDownloader(ArcGisDoc()))
        docs.store(listOf(track, bike))
        val settings = SettingsRepository(context)
        settings.hideMapLayer(AssetLayer.BIKE_TRACKS, true)

        val viewModel = MapViewModel(
            assetRepository = assetRepository,
            settingsRepository = settings,
            locationSource = FakeLocation(emptyList()),
            docStore = docs,
            loadGeometry = { AssetGeometry.NONE }
        )
        withTimeout(5_000) {
            viewModel.hiddenLayers.first { AssetLayer.BIKE_TRACKS in it }
        }

        viewModel.pickDocAt(-41.5001, 173.9601, DocHitTest.DEFAULT_TOLERANCE_M)

        val pick = withTimeout(5_000) { viewModel.docPick.first { it != null } }!!
        assertEquals("the track is walked as well as ridden", "Valley dual use", pick.item.name)
    }

    @Test
    fun aWalkingOnlyTrackIsNotTappableInTheBikeOnlyView() = runBlocking {
        val track = DocItem(
            source = DocSource.TRACKS,
            objectId = 90,
            name = "Walking only",
            paths = listOf(
                listOf(
                    GeoPoint(lat = -41.5000, lng = 173.9600),
                    GeoPoint(lat = -41.5100, lng = 173.9700)
                )
            )
        )
        val docs = OfflineDocStore(dao = db.offlineDocItemDao(), downloader = DocDownloader(ArcGisDoc()))
        docs.store(listOf(track))
        val settings = SettingsRepository(context)
        settings.hideMapLayer(AssetLayer.TRACKS, true)

        val viewModel = MapViewModel(
            assetRepository = assetRepository,
            settingsRepository = settings,
            locationSource = FakeLocation(emptyList()),
            docStore = docs,
            loadGeometry = { AssetGeometry.NONE }
        )
        withTimeout(5_000) {
            viewModel.hiddenLayers.first { AssetLayer.TRACKS in it }
        }

        viewModel.pickDocAt(-41.5001, 173.9601, DocHitTest.DEFAULT_TOLERANCE_M)

        assertNull("a walking-only track is not part of the bike-only view", viewModel.pickedDoc.value)
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
