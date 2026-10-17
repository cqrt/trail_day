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

        // The card fills in as the map's own flows arrive - the places from the database, then DOC's cache -
        // so the test waits for the state it is about rather than the first one that is not empty.
        val nearby = withTimeout(5_000) {
            viewModel.nearby.first { card -> card != null && card.within.any { it.what == "hut" } }
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
