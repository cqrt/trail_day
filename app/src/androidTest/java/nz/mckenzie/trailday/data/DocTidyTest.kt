package nz.mckenzie.trailday.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.viewmodel.AssetListViewModel
import nz.mckenzie.trailday.viewmodel.docPlacesMovedMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The places that came from DOC leaving the walker's own list.
 *
 * DOC's material is the map's reference layer now rather than something kept as a place - it is DOC's,
 * drawn under the work, and not the walker's to change. What this holds still is that the copies an
 * earlier build made go, that **the walker's own places do not**, that both forms of the reference an
 * earlier build might have written are recognised, and that the tidy says how many went and happens
 * once.
 */
@RunWith(AndroidJUnit4::class)
class DocTidyTest {

    private lateinit var db: TrailDayDatabase
    private lateinit var context: Context
    private lateinit var assets: AssetRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TrailDayDatabase::class.java).build()
        assets = AssetRepository(db)
        SettingsRepository(context).setDocPlacesDropped(false)
    }

    @After
    fun tearDown() = runBlocking {
        SettingsRepository(context).setDocPlacesDropped(false)
        db.close()
    }

    private val line = AssetGeometry(
        listOf(listOf(GeoPoint(-46.6, 168.3), GeoPoint(-46.61, 168.31)))
    )

    @Test
    fun thePlacesFromDOCGoAndTheWalkersOwnStay(): Unit = runBlocking {
        val pulled = assets.createAsset(name = "Glory Tk", geometry = line, sourceRef = "doc-tracks:1")
        // The form an install wrote before the sources were namespaced. It is DOC's too.
        val older = assets.createAsset(name = "Old DOC Tk", geometry = line, sourceRef = "doc:2")
        val mine = assets.createAsset(name = "My track", geometry = line)

        assertEquals("both forms of the reference are DOC's", 2, assets.placesFromDoc())
        assertEquals(2, assets.dropPlacesFromDoc())

        assertNull("the copied one is gone", assets.getAsset(pulled))
        assertNull("and so is the one from the older form", assets.getAsset(older))
        assertNotNull("the walker's own is untouched", assets.getAsset(mine))
        assertTrue(
            "and its line went nowhere",
            assets.getAssetGeometry(mine).pointCount == 2
        )
        assertEquals("and there is nothing left to drop", 0, assets.placesFromDoc())
    }

    @Test
    fun theTidySaysHowManyWentAndHappensOnce(): Unit = runBlocking {
        assets.createAsset(name = "Glory Tk", geometry = line, sourceRef = "doc-tracks:1")
        assets.createAsset(name = "Cupola Hut", geometry = line, sourceRef = "doc-huts:2")

        // Built off the main thread, as the screen's own view model is: the seeding and the tidy are
        // coroutines, and this only has to see what they leave behind.
        val viewModel = withContext(Dispatchers.Default) {
            AssetListViewModel(assets, context, SettingsRepository(context))
        }

        withTimeout(TIMEOUT_MS) { SettingsRepository(context).docPlacesDropped.first { it } }
        assertEquals(0, assets.placesFromDoc())
        assertEquals(
            "the number gone is what the walker is told",
            docPlacesMovedMessage(2),
            withTimeout(TIMEOUT_MS) { viewModel.message.first { it != null } }
        )

        // And the second time the list is opened there is nothing to say: the tidy is written down, so
        // an install does not count DOC's rows again on every visit.
        assets.createAsset(name = "Another from DOC", geometry = line, sourceRef = "doc-tracks:9")
        val reopened = withContext(Dispatchers.Default) {
            AssetListViewModel(assets, context, SettingsRepository(context))
        }
        withTimeout(TIMEOUT_MS) { reopened.busy.first() }
        assertNull("nothing is said a second time", reopened.message.value)
        assertEquals(
            "and nothing is dropped a second time - the tidy is a one-off, not a rule about references",
            1,
            assets.placesFromDoc()
        )
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
