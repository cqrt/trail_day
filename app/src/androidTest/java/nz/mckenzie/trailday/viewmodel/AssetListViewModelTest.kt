package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The place list's kind chips: which rows they leave on the screen.
 *
 * `AssetKindFilterTest` pins the rule. This pins the wiring - a filter asked the wrong question, or
 * one that hides a row the walker can still see on the map, is the kind of bug that is taken for a
 * lost place.
 */
@RunWith(AndroidJUnit4::class)
class AssetListViewModelTest {

    private lateinit var db: TrailDayDatabase
    private lateinit var repository: AssetRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TrailDayDatabase::class.java).build()
        repository = AssetRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun viewModel() = AssetListViewModel(assetRepository = repository, context = context)

    /** The names the list would draw, waited for rather than slept on. */
    private suspend fun namesShown(viewModel: AssetListViewModel, count: Int): List<String> =
        withTimeout(5_000) { viewModel.shown.first { it.size == count } }.map { it.name }

    @Test
    fun everythingIsShownUntilAChipIsTapped() = runBlocking {
        givenAHut("Tin Hut")
        givenABikeTrack("Estuary road")

        assertEquals(listOf("Estuary road", "Tin Hut"), namesShown(viewModel(), 2))
    }

    @Test
    fun aChipShowsThatKindAloneAndItsOwnChipAgainShowsEverything() = runBlocking {
        givenAHut("Tin Hut")
        givenABikeTrack("Estuary road")
        val viewModel = viewModel()
        namesShown(viewModel, 2)

        viewModel.showOnly(AssetKind.HUT)
        assertEquals(listOf("Tin Hut"), namesShown(viewModel, 1))

        viewModel.showOnly(AssetKind.HUT)
        assertEquals("the chip already showing is the way back out", 2, namesShown(viewModel, 2).size)

        viewModel.showOnly(AssetKind.CAMPSITE)
        assertEquals("and a kind with nothing in it shows nothing", 0, namesShown(viewModel, 0).size)
    }

    @Test
    fun theAllChipClearsWhateverWasShowing() = runBlocking {
        givenAHut("Tin Hut")
        givenABikeTrack("Estuary road")
        val viewModel = viewModel()
        viewModel.showOnly(AssetKind.HUT)
        namesShown(viewModel, 1)

        viewModel.showOnly(null)

        assertEquals(2, namesShown(viewModel, 2).size)
    }

    @Test
    fun aStoredWordFromALaterBuildIsFoundUnderTrack() = runBlocking {
        val id = repository.createAsset(
            name = "Home track",
            geometry = listOf(GeoPoint(-41.5, 173.8), GeoPoint(-41.5, 173.81)),
            kind = AssetKind.TRACK,
            shape = AssetShape.LINE
        )
        // A value this build has never heard of - a word from a later build, or a hand-edited row -
        // which the phone reads as a track rather than losing.
        val stored = db.assetDao().getAsset(id)!!
        db.assetDao().update(stored.copy(kind = "PADDOCK"))
        givenABikeTrack("Estuary road")

        val viewModel = viewModel()
        namesShown(viewModel, 2)

        viewModel.showOnly(AssetKind.TRACK)
        assertEquals("found under what it is read as", listOf("Home track"), namesShown(viewModel, 1))

        viewModel.showOnly(AssetKind.HUT)
        assertEquals("and not under a word it never had", 0, namesShown(viewModel, 0).size)
    }

    private suspend fun givenAHut(name: String) {
        repository.createAsset(
            name = name,
            geometry = listOf(GeoPoint(-41.5, 173.8)),
            kind = AssetKind.HUT,
            shape = AssetShape.POINT
        )
    }

    private suspend fun givenABikeTrack(name: String) {
        repository.createAsset(
            name = name,
            geometry = listOf(GeoPoint(-41.5, 173.8), GeoPoint(-41.5, 173.82)),
            kind = AssetKind.BIKE_TRACK,
            shape = AssetShape.LINE
        )
    }
}
