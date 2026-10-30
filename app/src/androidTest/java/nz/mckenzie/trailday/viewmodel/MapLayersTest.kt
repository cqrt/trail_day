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
import nz.mckenzie.trailday.domain.asset.AssetLayer
import nz.mckenzie.trailday.domain.asset.YourPlaceLayer
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.tracking.LocationSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The switches that leave a layer of the work off the map.
 *
 * What matters is not the switch on the screen but what it leaves behind: the choice is stored, so
 * it is still there when the sheet is opened tomorrow, and it is stored as what is **hidden** - which
 * is what makes the map an install has never touched the map it always was.
 *
 * The settings DataStore is one per device rather than one per test, so this clears the choice
 * before and after itself: a run that left a layer hidden would change the next run, and would
 * change the emulator somebody then looks at.
 */
@RunWith(AndroidJUnit4::class)
class MapLayersTest {

    private lateinit var context: Context
    private lateinit var db: TrailDayDatabase
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TrailDayDatabase::class.java).build()
        settings = SettingsRepository(context)
        settings.setHiddenMapLayers(emptySet())
        settings.showAllYourPlaces()
    }

    @After
    fun tearDown() = runBlocking {
        settings.setHiddenMapLayers(emptySet())
        settings.showAllYourPlaces()
        // And the one switch in that sheet that starts off goes back to off: a run that left the
        // place names on would change the map the next run looks at, and the emulator somebody then
        // looks at.
        settings.setPlaceNames(false)
    }

    /** A phone that will not say where it is: these tests are not about the fix. */
    private object NoLocation : LocationSource {
        override fun updates(): Flow<GeoPoint> = emptyFlow()
        override suspend fun currentLocation(): GeoPoint? = null
    }

    private fun viewModel() = MapViewModel(
        assetRepository = AssetRepository(db),
        settingsRepository = settings,
        locationSource = NoLocation,
        loadGeometry = { AssetGeometry.NONE }
    )

    @Test
    fun nothingIsHiddenUntilSomebodySaysSo() = runBlocking {
        assertTrue("a stored nothing hides nothing", settings.hiddenMapLayers.first().isEmpty())
        assertTrue(settings.hiddenYourPlaces.first().isEmpty())

        val viewModel = viewModel()

        assertEquals(emptySet<AssetLayer>(), viewModel.hiddenLayers.first())
        assertEquals(emptySet<YourPlaceLayer>(), viewModel.hiddenYourPlaces.first())
    }

    @Test
    fun hidingOneLayerLeavesTheOthersWhereTheyAre() = runBlocking {
        val viewModel = viewModel()

        viewModel.setLayerHidden(AssetLayer.BIKE_TRACKS, hidden = true)
        val one = withTimeout(5_000) { viewModel.hiddenLayers.first { it.isNotEmpty() } }
        assertEquals(setOf(AssetLayer.BIKE_TRACKS), one)

        viewModel.setYourPlaceHidden(YourPlaceLayer.SPOTS, hidden = true)
        val two = withTimeout(5_000) { viewModel.hiddenYourPlaces.first { it.isNotEmpty() } }
        assertEquals(setOf(YourPlaceLayer.SPOTS), two)

        viewModel.setYourPlaceHidden(YourPlaceLayer.SPOTS, hidden = false)
        val back = withTimeout(5_000) { viewModel.hiddenYourPlaces.first { it.isEmpty() } }
        assertEquals(emptySet<YourPlaceLayer>(), back)
        assertEquals("DOC's bike switch is untouched by a Your Places tile", setOf(AssetLayer.BIKE_TRACKS), viewModel.hiddenLayers.first())
    }

    @Test
    fun twoSwitchesInARowBothStaySwitched() = runBlocking {
        val viewModel = viewModel()

        // One after the other without waiting in between, which is how a screen of switches is
        // used: reading the choice out of a value the view model was holding made the second tap
        // undo the first, and this is the test that says so.
        viewModel.setLayerHidden(AssetLayer.TRACKS, hidden = true)
        viewModel.setLayerHidden(AssetLayer.HUTS, hidden = true)

        val both = withTimeout(5_000) { viewModel.hiddenLayers.first { it.size == 2 } }

        assertEquals(setOf(AssetLayer.TRACKS, AssetLayer.HUTS), both)
    }

    @Test
    fun theChoiceIsWaitingForTheNextMapThatIsDrawn() = runBlocking {
        viewModel().setLayerHidden(AssetLayer.TRACKS, hidden = true)
        withTimeout(5_000) { settings.hiddenMapLayers.first { it.isNotEmpty() } }

        // A second view model, as a second visit to the tab is. Read through the flow rather than
        // taken as it stands: a StateFlow answers with its seed until the store has been read.
        val next = viewModel()

        assertEquals(
            setOf(AssetLayer.TRACKS),
            withTimeout(5_000) { next.hiddenLayers.first { it.isNotEmpty() } }
        )
    }

    @Test
    fun showEverythingPutsBackWhatTheSwitchesTookAway() = runBlocking {
        val viewModel = viewModel()
        viewModel.setLayerHidden(AssetLayer.TRACKS, hidden = true)
        viewModel.setLayerHidden(AssetLayer.HUTS, hidden = true)
        // Hiding all of the walker's own work is four rows now that the master switch has gone, and
        // "Show everything" still has to put back what four taps took away.
        YourPlaceLayer.ALL.forEach { viewModel.setYourPlaceHidden(it, hidden = true) }
        withTimeout(5_000) { viewModel.hiddenLayers.first { it.size == 2 } }
        withTimeout(5_000) { viewModel.hiddenYourPlaces.first { it.size == YourPlaceLayer.ALL.size } }

        viewModel.showEveryLayer()

        withTimeout(5_000) { viewModel.hiddenLayers.first { it.isEmpty() } }
        withTimeout(5_000) { viewModel.hiddenYourPlaces.first { it.isEmpty() } }
        assertTrue(settings.hiddenMapLayers.first().isEmpty())
        assertTrue(settings.hiddenYourPlaces.first().isEmpty())
    }

    /**
     * The one switch in that sheet that starts **off**, and the one that fetches something new.
     *
     * Everything else there is a layer of the work, which is why the app was opened; the names are
     * furniture over the walker's own ground, and their tiles come from the internet on the way
     * past - so the map draws what it already holds until somebody asks for more.
     */
    @Test
    fun thePlaceNamesAreOffUntilSomebodyAsksAndThenTheyStayOn() = runBlocking {
        val viewModel = viewModel()

        assertEquals(
            "nothing is fetched for a switch nobody has thrown",
            false,
            withTimeout(5_000) { viewModel.placeNames.first() }
        )

        viewModel.setPlaceNames(true)

        assertTrue(
            "the switch wrote it, and the map follows",
            withTimeout(5_000) { viewModel.placeNames.first { it } }
        )
        assertEquals("the choice is the phone's rather than the screen's", true, settings.placeNames.first())

        // A second view model, as a second visit to the tab is: the answer is read back rather than
        // remembered from the tap.
        assertTrue(withTimeout(5_000) { viewModel().placeNames.first { it } })
    }
}
