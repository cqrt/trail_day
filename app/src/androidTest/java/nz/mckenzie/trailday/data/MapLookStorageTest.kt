package nz.mckenzie.trailday.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nz.mckenzie.trailday.domain.look.LineWidthChoice
import nz.mckenzie.trailday.domain.look.LookColour
import nz.mckenzie.trailday.domain.look.LookTarget
import nz.mckenzie.trailday.domain.look.MapLook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The look, as the phone keeps it: **a choice survives, and the default leaves nothing behind.**
 *
 * The rules themselves are unit tests (`MapLookTest`); what is here is the one thing they cannot see -
 * that the repository writes the string the look makes and reads it back, and that going back to how
 * the app came genuinely clears the preference rather than storing the default as a choice. That
 * second part is why an install that has never opened the screen draws exactly what it always did.
 *
 * The settings store is one per device rather than one per test, so this puts the look back to the
 * default before and after itself: a run that left a colour chosen would change the next run, and
 * would change the emulator somebody then looks at.
 */
@RunWith(AndroidJUnit4::class)
class MapLookStorageTest {

    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        settings = SettingsRepository(context)
        settings.setMapLook(MapLook.DEFAULT)
    }

    @After
    fun tearDown() = runBlocking {
        settings.setMapLook(MapLook.DEFAULT)
    }

    private fun stored(): MapLook = runBlocking {
        withTimeout(TIMEOUT_MS) { settings.mapLook.first() }
    }

    @Test
    fun aChosenColourAndWidthComeBack() {
        val chosen = MapLook.DEFAULT
            .withColour(LookTarget.TRACKS, LookColour.CRIMSON)
            .withWidth(LookTarget.TRACKS, LineWidthChoice.THICK)

        runBlocking { settings.setMapLook(chosen) }

        val back = stored()
        assertEquals(LookColour.CRIMSON, back.colourOf(LookTarget.TRACKS))
        assertEquals(LineWidthChoice.THICK, back.widthOf(LookTarget.TRACKS))
        // And the rows nobody touched are still the app's own: one row changed is one row written.
        assertEquals(MapLook.DEFAULT.colourOf(LookTarget.HUTS), back.colourOf(LookTarget.HUTS))
    }

    @Test
    fun goingBackToHowItCameIsTheSameAsNeverHavingChosen() {
        runBlocking {
            settings.setMapLook(
                MapLook.DEFAULT.withColour(LookTarget.CAMPSITES, LookColour.MAGENTA)
            )
            settings.setMapLook(MapLook.DEFAULT)
        }

        assertEquals(MapLook.DEFAULT, stored())
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
