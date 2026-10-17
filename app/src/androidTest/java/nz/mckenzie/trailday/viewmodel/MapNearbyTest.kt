package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.doc.ArcGisDoc
import nz.mckenzie.trailday.doc.DocDownloader
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
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
 * The card at the bottom of the map, as the map's own view model builds it.
 *
 * The card's arithmetic is held still by the domain's own test; what this pins is the **wiring**, which is
 * the part that could quietly lose half the answer: that the walker's places and DOC's published material
 * are one list, that DOC's side is the cache the reference layer draws from, and that each thing keeps what
 * the card needs - the place DOC puts it in, and the walker's own id for opening it.
 */
@RunWith(AndroidJUnit4::class)
class MapNearbyTest {

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
    fun theCardTakesInTheWalkersPlacesAndDocsHutsTogether() = runBlocking {
        // A spot of the walker's fifty metres away, and a hut DOC publishes a kilometre off.
        val spot = AssetGeometry(paths = listOf(listOf(GeoPoint(lat = -41.5005, lng = 173.9505))))
        assetRepository.createAsset(
            name = "My camp",
            geometry = spot,
            kind = AssetKind.CAMPSITE,
            shape = AssetShape.POINT
        )
        val docs = OfflineDocStore(dao = db.offlineDocItemDao(), downloader = DocDownloader(ArcGisDoc()))
        docs.store(
            listOf(
                DocItem(
                    source = DocSource.HUTS,
                    objectId = 1,
                    name = "Angelus Hut",
                    paths = listOf(listOf(GeoPoint(lat = -41.5100, lng = 173.9500))),
                    facts = mapOf("place" to "Nelson Lakes")
                )
            )
        )
        DevicePosition.useForTest(FakeLocation(listOf(fix(-41.5000, 173.9500))))

        val viewModel = MapViewModel(
            assetRepository = assetRepository,
            settingsRepository = SettingsRepository(context),
            locationSource = FakeLocation(emptyList()),
            docStore = docs,
            loadGeometry = { spot }
        )

        // The card fills in as the map's own flows arrive - the walker's places and their lines from the
        // database, DOC's cache separately, the fix separately again - so the test waits for **both things it
        // is about**, not for the first one that turns up. Waiting for the hut alone let the assertion below
        // run before the camp's line had been read, and a slower machine failed it.
        val nearby = withTimeout(5_000) {
            viewModel.nearby.first { card -> card != null && card.within.size == 2 }
        }!!

        assertEquals("the walker's own place is nearest", "My camp", nearby.nearest.name)
        assertNotNull("and the card can open it", nearby.nearest.assetId)
        assertEquals(
            "DOC's hut is in the same list, not a second card",
            setOf("campsite", "hut"),
            nearby.within.map { it.what }.toSet()
        )
        assertEquals(
            "with the place DOC puts it in",
            "Nelson Lakes",
            nearby.within.first { it.what == "hut" }.place
        )
        assertEquals(
            "and everything inside five kilometres counted, the nearest included",
            listOf("1 campsite", "1 hut"),
            nearby.counted
        )
        assertTrue(
            "and the distances are real ground, not the same number twice",
            nearby.within.first { it.what == "hut" }.distanceM >
                nearby.within.first { it.what == "campsite" }.distanceM
        )
    }

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

        // A tap on empty ground lets it go, and the card is about what is nearest again.
        viewModel.pickDocAt(-45.5000, 169.0000, DocHitTest.DEFAULT_TOLERANCE_M)
        assertNull(viewModel.pickedDoc.value)
    }

    @Test
    fun withNothingAroundThereIsNoCardRatherThanAnEmptyOne() = runBlocking {
        // A fix in the middle of the sea, with nothing in the cache and no places.
        DevicePosition.useForTest(FakeLocation(listOf(fix(-46.6000, 168.3000))))

        val viewModel = MapViewModel(
            assetRepository = assetRepository,
            settingsRepository = SettingsRepository(context),
            locationSource = FakeLocation(emptyList()),
            loadGeometry = { AssetGeometry.NONE }
        )

        // Wait for the fix to arrive and settle: the card is null before it and staying null after it.
        val firstFix = withTimeout(5_000) { viewModel.docGeoJson.first() }
        assertNotNull(firstFix)
        assertEquals("nothing in range is nothing to show", null, viewModel.nearby.value)
    }
}
