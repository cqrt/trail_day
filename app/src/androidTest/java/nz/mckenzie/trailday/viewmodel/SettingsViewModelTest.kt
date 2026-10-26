package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.domain.settings.ThemeChoice
import nz.mckenzie.trailday.offline.OfflineTileStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/**
 * The settings screen's own behaviour: what a tap writes down, and what it tells the running service.
 *
 * There used to be a key entered here, and a suite of tests around it. There is no key any more -
 * LINZ's tiles come through the app's own proxy, which holds it - so what is left is the housekeeping:
 * the desk's switches, the light, and the seeding of the fields the screen is built with.
 *
 * Every wait here is on a *specific* value rather than on "something non-null", and the timeouts are
 * generous, because DataStore round trips on a CI emulator are slower than on a desk - an earlier
 * version of these tests read a stale message and called it a failure.
 */
@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {

    private lateinit var context: Context
    private lateinit var store: OfflineTileStore

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        store = OfflineTileStore(File(context.cacheDir, "settings-test-tiles"))
        store.deleteAll()
    }

    @After
    fun tearDown(): Unit = runBlocking {
        // The repository field goes back to blank, so a test that set one cannot leave the next
        // one reading somebody else's destination.
        SettingsRepository(context).setBackupRepo("")
        // And the editor's switches go back to what an untouched install has - served, no token asked
        // for, and the phone left to sleep - so a test that left any of them the other way cannot
        // leave the next one reading somebody else's decision.
        SettingsRepository(context).setWebEditorTokenRequired(false)
        SettingsRepository(context).setWebEditorEnabled(true)
        SettingsRepository(context).setWebEditorKeepAwake(false)
        // And the light goes back to following the phone, so a test that chose one outright cannot
        // leave the next one reading somebody else's screen.
        SettingsRepository(context).setThemeChoice(ThemeChoice.DEFAULT)
        store.deleteAll()
    }

    private fun viewModel() = SettingsViewModel(
        settings = SettingsRepository(context),
        store = store
    )

    /**
     * Polls [read] until it gives [expected], then returns it.
     *
     * Polling rather than `first { }` so a failure can report what was actually seen:
     * a bare timeout says nothing about whether the write never happened, the wrong
     * value was written, or a flow simply stayed on its initial value.
     */
    private suspend fun <T> awaitValue(what: String, expected: T, read: suspend () -> T): T {
        var seen: T = read()
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (seen != expected && System.currentTimeMillis() < deadline) {
            delay(100)
            seen = read()
        }
        assertEquals(what, expected, seen)
        return seen
    }

    // `: Unit` throughout: JUnit4 only runs methods that return void, and a
    // `= runBlocking { ... }` whose last statement has a value does not. Two of these
    // were not void when first written, and the run failed as "invalid test class"
    // rather than as a test failure.

    /**
     * The off-site fields are seeded from the same repository, and the seeding has to survive a read
     * that answers **immediately**.
     *
     * Kotlin constructs a class top to bottom, and the seeding `init` block sits above the repository
     * and token fields it writes. `viewModelScope` is `Dispatchers.Main.immediate`, so when the
     * constructor runs off the main thread the seeding coroutine is *posted* to the main thread - and
     * a stored flow that has already been read once in this process can answer without a round trip,
     * so nothing stops that body running while the constructor is still working its way down to those
     * fields. On a CI emulator that was an NPE on a `MutableStateFlow` from
     * `SettingsViewModel$6.invokeSuspend`, and it took the whole instrumented run with it.
     *
     * Warming the reads and building the view model off the main thread is what makes the window
     * open every time rather than once in a hundred runs: the reads answer at once, and the main
     * thread is free to run the body while the constructor is still going.
     */
    @Test
    fun theOffsiteFieldsAreSeededWhenTheStoredReadAnswersAtOnce(): Unit = runBlocking {
        val settings = SettingsRepository(context)
        settings.setBackupRepo("cqrt/trail-day-backups")
        // Warm every read the constructor's own seeding makes.
        settings.backupRepo.first()
        settings.backupToken.first()

        val viewModel = withContext(Dispatchers.Default) { SettingsViewModel(settings, store) }

        awaitValue("the repository field should be seeded", "cqrt/trail-day-backups") {
            viewModel.repoText.value
        }

        settings.setBackupRepo("")
    }

    /**
     * The token switch: the setting, and the run that has to be built again for it to mean anything.
     *
     * Two claims in one test because they are the two halves of the same tap - and the second is the
     * one worth having, because a run is built with the answer it started from. What it leaves behind
     * does not matter: `tearDown` puts both of the card's switches back to the defaults an untouched
     * install has, so the next test in this suite does not inherit a decision made here.
     */
    @Test
    fun theTokenSwitchIsWrittenAndServesTheEditorAgainOnlyWhileItIsServing(): Unit = runBlocking {
        val settings = SettingsRepository(context)
        settings.setWebEditorTokenRequired(true)

        val asked = Collections.synchronizedList(mutableListOf<String>())
        var serving = true
        val viewModel = SettingsViewModel(
            settings = settings,
            store = store,
            webEditor = object : WebEditorSwitch {
                override fun setEnabled(enabled: Boolean) {
                    asked += if (enabled) "turned on" else "turned off"
                }

                override fun restart() {
                    asked += "served again"
                }
            },
            isEditorServing = { serving }
        )

        viewModel.setWebEditorTokenRequired(false)

        awaitValue("the switch written", false, { settings.webEditorTokenRequired.first() })
        awaitValue("the run served again", listOf("served again"), { asked.toList() })

        // With the editor off there is nothing to serve again: the setting is written, and the
        // service is left alone - which is also why the pause is here, so a late call would be seen.
        asked.clear()
        serving = false
        viewModel.setWebEditorTokenRequired(true)

        awaitValue("the switch written back", true, { settings.webEditorTokenRequired.first() })
        delay(500)
        assertEquals("nothing to serve again when nothing is being served", emptyList<String>(), asked.toList())
    }

    /**
     * The awake switch: the setting, and the run that has to be built again for it to mean anything.
     *
     * The same two halves as the token switch above, and the second is again the one worth having: a
     * run holds the phone awake in the way it was built with, so a switch thrown while one is going
     * has to build that run again. Turning it on is the walker's own answer to a phone that sleeps
     * through the desk's browsing, and it costs battery - which is exactly why the answer is theirs.
     */
    @Test
    fun theAwakeSwitchIsWrittenAndServesTheEditorAgainOnlyWhileItIsServing(): Unit = runBlocking {
        val settings = SettingsRepository(context)
        settings.setWebEditorKeepAwake(false)

        val asked = Collections.synchronizedList(mutableListOf<String>())
        var serving = true
        val viewModel = SettingsViewModel(
            settings = settings,
            store = store,
            webEditor = object : WebEditorSwitch {
                override fun setEnabled(enabled: Boolean) = Unit

                override fun restart() {
                    asked += "served again"
                }
            },
            isEditorServing = { serving }
        )

        viewModel.setWebEditorKeepAwake(true)

        awaitValue("the switch written", true, { settings.webEditorKeepAwake.first() })
        awaitValue("the run served again", listOf("served again"), { asked.toList() })

        // With nothing being served the setting is written and the service is left alone - which is
        // also why the pause is here, so a late call would be seen.
        asked.clear()
        serving = false
        viewModel.setWebEditorKeepAwake(false)

        awaitValue("the switch written back", false, { settings.webEditorKeepAwake.first() })
        delay(500)
        assertEquals("nothing to serve again when nothing is being served", emptyList<String>(), asked.toList())
    }

    /**
     * The serving switch: the one that decides whether the desk comes up on its own.
     *
     * On when unset, and turning it off has to be **written down** - a phone that came back serving
     * after the walker had said no would be the app putting itself back on the network against
     * their answer, which is the one thing a default may not do. The service is told in the same tap
     * and told *after* the write, so what is on disk is never behind what was just asked for: a phone
     * put down mid-press comes back agreeing with the switch.
     */
    @Test
    fun theServingSwitchIsRememberedSoTheDeskStaysOffWhenTheAppIsOpenedAgain(): Unit = runBlocking {
        val settings = SettingsRepository(context)
        settings.setWebEditorEnabled(true)

        val asked = Collections.synchronizedList(mutableListOf<String>())
        val viewModel = SettingsViewModel(
            settings = settings,
            store = store,
            webEditor = object : WebEditorSwitch {
                override fun setEnabled(enabled: Boolean) {
                    asked += if (enabled) "turned on" else "turned off"
                }

                override fun restart() = Unit
            }
        )

        viewModel.setWebEditor(false)

        awaitValue("the switch written", false, { settings.webEditorEnabled.first() })
        awaitValue("the service told", listOf("turned off"), { asked.toList() })

        // What the next launch reads is the answer rather than the default, which is the whole
        // reason the choice is stored at all.
        assertEquals(
            "a launch after the walker said no must not find the desk on",
            false,
            settings.webEditorEnabled.first()
        )

        viewModel.setWebEditor(true)
        awaitValue("the switch written back", true, { settings.webEditorEnabled.first() })
    }

    /**
     * The light setting: written down, and published to every screen that draws.
     *
     * Stored rather than held in the view model, because it is a property of this phone rather than
     * of this visit - a phone put away and opened again in the dark has to come back as it was left.
     *
     * Collected here as a screen collects it, and that is the point rather than a detail: the
     * published flow reads the preference **while something is watching** and not otherwise, which
     * is what keeps a settings screen from holding a preference open when it is not on screen. A
     * subscription is therefore what makes the flow answer at all - so the test subscribes by asking
     * the flow for its next value, rather than reading the last one and hoping something was watching.
     */
    @Test
    fun theLightSettingIsWrittenAndPublishedToTheScreens(): Unit = runBlocking {
        val settings = SettingsRepository(context)
        val viewModel = viewModel()

        viewModel.setThemeChoice(ThemeChoice.DARK)

        awaitValue("the choice written", ThemeChoice.DARK) { settings.themeChoice.first() }
        awaitPublished("the choice published", ThemeChoice.DARK, viewModel.themeChoice)

        viewModel.setThemeChoice(ThemeChoice.DEFAULT)

        awaitValue("the choice written back", ThemeChoice.DEFAULT) { settings.themeChoice.first() }
        awaitPublished("the choice published back", ThemeChoice.DEFAULT, viewModel.themeChoice)
    }

    /**
     * Waits for [flow] to say [expected], **by collecting it** - the very act a screen performs.
     *
     * Reading the flow's last value instead depends on another coroutine having started collecting first,
     * and on a loaded machine that coroutine can be the last thing to run: the flow then sits at its seed
     * value while the preference has long been written, and a passing change reads as a failure. Asking the
     * flow itself is both the honest test and the reliable one.
     */
    private suspend fun <T> awaitPublished(what: String, expected: T, flow: Flow<T>): T =
        withTimeout(TIMEOUT_MS) {
            val seen = flow.first { it == expected }
            assertEquals(what, expected, seen)
            seen
        }

    private companion object {
        /** DataStore round trips on a CI emulator are not instant. */
        const val TIMEOUT_MS = 20_000L
    }
}
