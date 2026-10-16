package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nz.mckenzie.trailday.data.RecordingRepository
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.ui.AssetEditFields
import nz.mckenzie.trailday.ui.AssetEditResult
import nz.mckenzie.trailday.ui.AssetEdits
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The per-asset details.
 *
 * The edit form is where a name, a kind and the notes are set, and what is set there is what
 * the track keeps.
 */
@RunWith(AndroidJUnit4::class)
class AssetDetailViewModelTest {

    private lateinit var context: Context
    private lateinit var db: TrailDayDatabase
    private lateinit var assetRepository: AssetRepository
    private var assetId = 0L

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TrailDayDatabase::class.java).build()
        assetRepository = AssetRepository(db)
        assetId = assetRepository.createAsset(
            name = "Home block",
            geometry = listOf(GeoPoint(-41.5000, 173.9500), GeoPoint(-41.5010, 173.9545))
        )
        Unit
    }

    @After
    fun tearDown() {
        // No db.close() here, for the reason recorded in the other view-model tests: a
        // view model that still observes a table will throw against a closed pool, and
        // the crash lands on an unrelated test.
    }

    /**
     * The edit form, filled in the way the screen fills it, so these tests exercise the
     * same path the operator does rather than a shortcut only the test knows.
     */
    private fun edit(
        asset: AssetEntity,
        name: String = asset.name,
        kind: AssetKind = AssetKind.fromStorage(asset.kind),
        notes: String = asset.notes.orEmpty()
    ) = AssetEdits.apply(
        asset,
        AssetEditFields(
            name = name,
            kind = kind,
            notes = notes
        )
    )

    @Test
    fun whatTheEditFormSetsIsWhatTheTrackKeeps() = runBlocking {
        val viewModel = viewModel()
        val track = track()
        val edited = edit(
            track,
            name = "Back paddock",
            kind = AssetKind.BIKE_TRACK,
            notes = "keep off when wet"
        )
        assertTrue(edited is AssetEditResult.Ok)

        val ok = edited as AssetEditResult.Ok
        viewModel.save(ok.asset)

        val stored = withTimeout(5_000) {
            assetRepository.observeAsset(assetId).first { it?.name == "Back paddock" }
        }!!
        assertEquals(
            "a bike track is still a bike track after a rename",
            AssetKind.BIKE_TRACK,
            AssetKind.fromStorage(stored.kind)
        )
        assertEquals("keep off when wet", stored.notes)
        // The geometry the map draws from must survive an edit.
        assertEquals(2, assetRepository.observeAssetGeometry(assetId).first().pointCount)
    }

    /**
     * A place is one coordinate, so the box its page asks a camera for has no size at all - and a
     * camera told to fit that goes past the imagery, which is what the page's own ceiling on its
     * zoom is for (`ASSET_PAGE_MAX_ZOOM`, 15).
     *
     * So the box is left as the place itself rather than opened up here.
     */
    @Test
    fun aPlaceHandsItsOwnCoordinateToThePage() = runBlocking {
        val placeId = assetRepository.createAsset(
            name = "Woolshed",
            geometry = listOf(GeoPoint(-41.5, 173.8)),
            kind = AssetKind.HUT,
            shape = AssetShape.POINT
        )
        val viewModel = AssetDetailViewModel(
            assetId = placeId,
            assetRepository = assetRepository,
            recordingsRepository = RecordingRepository(db),
            settingsRepository = SettingsRepository(context),
            context = context
        )

        val bounds = withTimeout(5_000) { viewModel.bounds.first { it != null } }!!

        assertEquals("a place is one coordinate", bounds.minLat, bounds.maxLat, 0.0)
        assertEquals(bounds.minLng, bounds.maxLng, 0.0)
        assertEquals("and it is the place's own", -41.5, bounds.minLat, 1e-12)
        assertEquals(173.8, bounds.minLng, 1e-12)
    }

    private suspend fun track(): AssetEntity = assetRepository.getAsset(assetId)!!

    private fun viewModel() = AssetDetailViewModel(
        assetId = assetId,
        assetRepository = assetRepository,
        recordingsRepository = RecordingRepository(db),
        settingsRepository = SettingsRepository(context),
        context = context
    )
}
