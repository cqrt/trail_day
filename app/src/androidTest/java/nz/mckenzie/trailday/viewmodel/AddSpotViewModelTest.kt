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
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.GeoPoint
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
 * Adding a spot: what it makes, and the signal it raises when it has made it.
 *
 * One tap is the whole of it, so these are about the tap - where it lands, that a second one moves
 * the spot rather than making another, and that what the walker chose is what gets stored. The
 * "the screen flashes and closes on its second use" bug has its regression cover here too: a view
 * model outlives its screen, so a "we saved it" flag left set would navigate away the instant the
 * screen was opened again. The signal has to be one-shot: raised on save, consumed once acted on.
 */
@RunWith(AndroidJUnit4::class)
class AddSpotViewModelTest {

    private lateinit var db: TrailDayDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TrailDayDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun viewModel(at: GeoPoint? = null) = AddSpotViewModel(
        assetRepository = AssetRepository(db),
        settingsRepository = SettingsRepository(context),
        locationSource = FixedLocation(at)
    )

    /**
     * Whether the spot has been placed, waited for the way the screen waits for it.
     *
     * `placed` is a `WhileSubscribed` flow, so a test that reaches for `.value` without collecting
     * is asserting about the value it was seeded with rather than the one the walker sees: the flow
     * only runs while something is watching it, which on the phone is the screen itself. Waiting for
     * it is therefore both the honest reading and the race-free one.
     */
    private suspend fun placedBecomes(viewModel: AddSpotViewModel, placed: Boolean): Boolean =
        runCatching { withTimeout(5_000) { viewModel.placed.first { it == placed } } }.isSuccess

    /** A phone that knows where it is, or one that cannot say. */
    private class FixedLocation(private val fix: GeoPoint?) : LocationSource {
        override fun updates(): Flow<GeoPoint> = emptyFlow()

        override suspend fun currentLocation(): GeoPoint? = fix
    }

    @Test
    fun theMapOpensAroundThePhoneRatherThanOnTheCountry(): Unit = runBlocking {
        val viewModel = viewModel(at = GeoPoint(lat = -46.4132, lng = 168.3538))

        val frame = withTimeout(5_000) { viewModel.initialFrame.first { it != null } }!!

        assertEquals("the frame should be around the phone", -46.4132, frame.minLat, 0.02)
        assertEquals(168.3538, frame.minLng, 0.02)
    }

    @Test
    fun aPhoneThatCannotSayWhereItIsLeavesTheMapOnItsNeutralView(): Unit = runBlocking {
        val viewModel = viewModel(at = null)

        // Nothing to frame on, so the map keeps its country-wide default rather than the screen
        // waiting for a fix that may never come.
        assertNull(withTimeout(5_000) { viewModel.initialFrame.first() })
    }

    @Test
    fun aSpotIsOneTapAndSavesWhereItIs(): Unit = runBlocking {
        val viewModel = viewModel()

        viewModel.chooseKind(AssetKind.HUT)
        viewModel.dropSpot(-41.5100, 173.9600)
        assertTrue("one tap is enough for a place", placedBecomes(viewModel, true))

        viewModel.save("Angelus bivouac")

        val saved = withTimeout(5_000) { viewModel.savedAssetId.first { it != null } }!!
        val repository = AssetRepository(db)
        val asset = repository.getAsset(saved)!!
        assertEquals(
            "the kind chosen is the kind stored",
            AssetKind.HUT,
            AssetKind.fromStorage(asset.kind)
        )
        assertEquals(AssetShape.POINT, AssetShape.fromStorage(asset.shape))
        assertEquals("a place has no length", 0.0, asset.lengthM, 1e-9)

        val placed = repository.getAssetGeometry(saved)
        assertEquals("one tap places one coordinate", 1, placed.pointCount)
        assertEquals(-41.5100, placed.points.single().lat, 1e-9)
        assertEquals(173.9600, placed.points.single().lng, 1e-9)
    }

    @Test
    fun aSecondTapMovesTheSpotRatherThanMakingAnother(): Unit = runBlocking {
        val viewModel = viewModel()

        viewModel.dropSpot(-41.5100, 173.9600)
        viewModel.dropSpot(-41.5200, 173.9800)

        assertTrue("a place is still one coordinate", placedBecomes(viewModel, true))
        viewModel.save("Moved")

        val saved = withTimeout(5_000) { viewModel.savedAssetId.first { it != null } }!!
        val placed = AssetRepository(db).getAssetGeometry(saved)
        assertEquals("two taps, one stored coordinate", 1, placed.pointCount)
        assertEquals(-41.5200, placed.points.single().lat, 1e-9)
    }

    @Test
    fun choosingADifferentKindMovesNoGround(): Unit = runBlocking {
        val viewModel = viewModel()

        viewModel.dropSpot(-41.5100, 173.9600)
        viewModel.chooseKind(AssetKind.CAMPSITE)

        assertTrue("the spot is still placed", placedBecomes(viewModel, true))
        viewModel.save("Camp")

        val saved = withTimeout(5_000) { viewModel.savedAssetId.first { it != null } }!!
        val repository = AssetRepository(db)
        assertEquals(AssetKind.CAMPSITE, AssetKind.fromStorage(repository.getAsset(saved)!!.kind))
        assertEquals(-41.5100, repository.getAssetGeometry(saved).points.single().lat, 1e-9)
    }

    @Test
    fun withNothingPlacedThereIsNothingToSaveAndItSaysWhy() {
        val viewModel = viewModel()

        viewModel.save("Nowhere")

        assertNull("there is nothing to keep", viewModel.savedAssetId.value)
        assertNotNull("the walker should be told why", viewModel.message.value)
    }

    @Test
    fun savingRaisesTheNavigationSignalAndConsumingClearsIt(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.dropSpot(-41.5100, 173.9600)

        viewModel.save("Water source")
        val saved = withTimeout(5_000) { viewModel.savedAssetId.first { it != null } }
        assertNotNull("saving should raise the signal", saved)
        assertTrue("the signal should carry the new place's id", saved!! > 0L)

        viewModel.consumeSaveResult()

        assertNull("after consuming, the screen must not navigate again", viewModel.savedAssetId.value)
    }

    @Test
    fun theSignalCanBeRaisedAgainByASubsequentSave(): Unit = runBlocking {
        val viewModel = viewModel()

        viewModel.dropSpot(-41.5100, 173.9600)
        viewModel.save("First")
        withTimeout(5_000) { viewModel.savedAssetId.first { it != null } }
        viewModel.consumeSaveResult()

        viewModel.dropSpot(-41.5200, 173.9800)
        viewModel.save("Second")

        assertNotNull(
            "consuming one save must not disable the signal for the next",
            withTimeout(5_000) { viewModel.savedAssetId.first { it != null } }
        )
    }
}
