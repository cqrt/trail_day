package nz.mckenzie.trailday.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.BuildConfig
import nz.mckenzie.trailday.backup.BackupTargets
import nz.mckenzie.trailday.backup.FileBackupTarget
import nz.mckenzie.trailday.data.BackupController
import nz.mckenzie.trailday.data.BackupRepository
import nz.mckenzie.trailday.data.OffsiteBackup
import nz.mckenzie.trailday.data.OffsiteResult
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.backup.BackupDestination
import nz.mckenzie.trailday.domain.backup.BackupSummary
import nz.mckenzie.trailday.domain.backup.OffsiteBackupRules
import nz.mckenzie.trailday.domain.backup.StoredBackup
import nz.mckenzie.trailday.domain.settings.ThemeChoice
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.LinzAccess
import nz.mckenzie.trailday.map.BasemapPreviewCache
import nz.mckenzie.trailday.offline.KeyCheck
import nz.mckenzie.trailday.offline.LinzKeyProbe
import nz.mckenzie.trailday.offline.OfflineTileStore
import nz.mckenzie.trailday.offline.TileServerHolder
import nz.mckenzie.trailday.offline.TileStoreSummary
import nz.mckenzie.trailday.offline.maskKey
import nz.mckenzie.trailday.web.WebEditorService
import nz.mckenzie.trailday.web.WebEditorState

/**
 * The editor's switch: the two things it does.
 *
 * `restart` rather than a stop followed by a start from the screen: the editor is a foreground
 * service, and asking it to go and then asking it to come back is a race - the second ask can arrive
 * before the first has been carried out, and the run it builds is then torn down by the stop that is
 * still in flight. One ask that means "serve again, from what the settings say now" leaves no window
 * for that, and it keeps the knowledge of what a run is in the service.
 */
interface WebEditorSwitch {

    /** Turns the editor on, or off. */
    fun setEnabled(enabled: Boolean)

    /** Serves it again from scratch, for a setting a run was built from having changed. */
    fun restart()
}

/** What the **Developer options** section's Check button is doing, and what it last said. */
sealed interface KeyCheckState {
    data object Idle : KeyCheckState
    data object Checking : KeyCheckState
    data object Worked : KeyCheckState
    data class Failed(val message: String) : KeyCheckState
}

/** What the off-site "Test" button found. */
sealed interface OffsiteCheckState {
    data object Idle : OffsiteCheckState
    data object Checking : OffsiteCheckState

    /**
     * The destination answered. [warning] is set when it will work but should not be used
     * as it is - a public repository being the one that matters.
     */
    data class Worked(val note: String, val warning: String? = null) : OffsiteCheckState

    data class Failed(val message: String) : OffsiteCheckState
}

/**
 * The app's settings: the basemap, the update checks and the backups.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val store: OfflineTileStore,
    /** Backup files: chosen by the walker, read and written by the app. */
    private val backup: BackupController? = null,
    /**
     * The off-site copy, built fresh from the settings each time it is used so that a
     * destination changed on this screen takes effect at once. Null in a test that has no
     * destination, which is also what the screen reports as "not set up yet".
     */
    private val offsite: (suspend () -> OffsiteBackup?)? = null,
    /** Takes a lasting permission on the file the walker chose, so it survives a restart. */
    private val keepFileAccess: (Uri) -> Unit = {},
    /** The chosen file's name, for the screen to say where the copy goes. */
    private val fileLabel: (Uri) -> String = { uri -> uri.lastPathSegment.orEmpty() },
    /** Serving the editor to a computer on the Wi-Fi: see [WebEditorState]. */
    private val webEditor: WebEditorSwitch? = null,
    /**
     * Whether the editor is being served at this moment.
     *
     * Injected for the same reason the switch is: a setting a run is built from has to serve the
     * editor again when it changes, and that decision is worth a test without a service behind it.
     */
    private val isEditorServing: () -> Boolean = { WebEditorState.isOn },
    /**
     * Asks LINZ whether it accepts a key, for the **Developer options** section's Check button.
     *
     * Injected so a test never reaches LINZ: see [nz.mckenzie.trailday.offline.LinzKeyProbe], which also
     * explains why the probe is a style document rather than a tile.
     */
    private val checkKey: suspend (String) -> KeyCheck = { LinzKeyProbe().check(it) }
) : ViewModel() {

    /**
     * How many times the version line has been tapped, for the door.
     *
     * Deliberately **not** a preference: a count that survived a restart would be a count nobody could
     * reason about, and the door's own state is what is remembered. See [onVersionTap].
     */
    private var versionTaps = 0

    /**
     * Whether the **Developer options** section is showing.
     *
     * Read by this screen and by nothing else: everything the section sets is read through the preference
     * behind it, so the door being open changes nothing about the app.
     */
    val developerOptions: StateFlow<Boolean> = settings.developerOptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /**
     * What to say under the version line while the walker is tapping it.
     *
     * The same courtesy Android pays: a hint that something is happening before the door opens, rather
     * than seven silent taps and then a section appearing from nowhere.
     */
    private val _doorMessage = MutableStateFlow<String?>(null)
    val doorMessage: StateFlow<String?> = _doorMessage

    /**
     * The version line was tapped.
     *
     * Seven times opens the door, which is Android's own number and Android's own joke: nobody arrives
     * there by accident, and anybody who wants it can be told how. Four taps in, it starts saying how many
     * are left.
     */
    fun onVersionTap() {
        if (developerOptions.value) return

        versionTaps++
        when {
            versionTaps >= TAPS_TO_UNLOCK -> {
                viewModelScope.launch { settings.setDeveloperOptions(true) }
                _doorMessage.value = "Developer options unlocked"
            }

            versionTaps >= TAPS_TO_UNLOCK - COUNTDOWN_FROM -> _doorMessage.value =
                "You are now ${TAPS_TO_UNLOCK - versionTaps} steps away"

            else -> _doorMessage.value = null
        }
    }

    /** Opens or closes the section. Closing it hides the controls and changes nothing they set. */
    fun setDeveloperOptions(unlocked: Boolean) {
        versionTaps = 0
        _doorMessage.value = null
        _message.value = null
        viewModelScope.launch { settings.setDeveloperOptions(unlocked) }
    }

    /**
     * Which basemap the walker has chosen for their maps.
     *
     * Seeded with the default rather than left empty, like every other preference here: DataStore
     * answers in a frame, and a card whose selection is briefly wrong is better than one that is
     * briefly blank.
     */
    val basemap: StateFlow<Basemap> = settings.basemap
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Basemap.DEFAULT)

    /** Chooses a basemap. Every map reads the same preference, so this is visible at once. */
    fun setBasemap(value: Basemap) {
        viewModelScope.launch { settings.setBasemap(value) }
    }

    /**
     * The key as typed in the **Developer options** section, seeded from what is stored.
     *
     * The field rather than the stored value: a key half-typed must not be read as the key in force, and
     * the masked [ownKeyLabel] is the only form the walker ever sees written back.
     */
    private val _keyText = MutableStateFlow("")
    val keyText: StateFlow<String> = _keyText

    /** The key actually in force, masked: enough to tell two keys apart, no more. */
    val ownKeyLabel: StateFlow<String> = settings.developerLinzKey
        .map { key -> if (key.isBlank()) "" else maskKey(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    /**
     * Whether the walker's own key is what fetches LINZ's tiles, rather than the app's own proxy.
     *
     * [LinzAccess]'s own flow rather than a second read of the preference: it is what every URL in the
     * app is built from, so a screen that reported anything else could disagree with the map.
     */
    val ownKeyInForce: StateFlow<Boolean> = LinzAccess.ownKeyInUse

    /** What the Check button is doing, and what it last said. */
    private val _check = MutableStateFlow<KeyCheckState>(KeyCheckState.Idle)
    val check: StateFlow<KeyCheckState> = _check

    /** What the section has to say about what was just done, or null when it has nothing to say. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** The field changed, so any verdict about the old text is no longer about this one. */
    fun setKeyText(text: String) {
        _keyText.value = text
        _check.value = KeyCheckState.Idle
        _message.value = null
    }

    /**
     * Saves the key, or clears it when the field is empty.
     *
     * Written to the preference that [LinzAccess] follows, so the next tile the app asks for goes to the
     * host this key belongs to rather than at the next launch. The basemap picker's pictures are the one
     * thing that has to be fetched again, because they were fetched from the other host.
     */
    fun saveKey() {
        viewModelScope.launch {
            val value = _keyText.value.trim()
            settings.setDeveloperLinzKey(value)
            BasemapPreviewCache.forget()
            _check.value = KeyCheckState.Idle
            _message.value = if (value.isBlank()) {
                "Cleared: the app's own proxy fetches LINZ again."
            } else {
                "Saved. Press Check to make sure LINZ accepts it."
            }
        }
    }

    /** Asks LINZ whether it accepts the key, which is the only honest answer to give. */
    fun check() {
        viewModelScope.launch {
            _check.value = KeyCheckState.Checking
            _message.value = null
            val key = _keyText.value.trim().ifBlank { settings.developerLinzKey.first() }
            _check.value = when {
                key.isBlank() -> KeyCheckState.Failed("There is no key to check")
                else -> when (
                    val result = runCatching { checkKey(key) }
                        .getOrElse { failure ->
                            KeyCheck.Unreachable(failure.message ?: "the check could not run")
                        }
                ) {
                    KeyCheck.Works -> KeyCheckState.Worked
                    is KeyCheck.Failed -> KeyCheckState.Failed(result.message)
                    // The check could not ask LINZ anything, which is worth saying too - it is just
                    // not a statement about the key.
                    is KeyCheck.Unreachable -> KeyCheckState.Failed(result.message)
                }
            }
        }
    }

    /**
     * How the app is lit: light, dark, or whatever this phone is set to.
     *
     * Seeded with the default rather than left empty, like the basemap above: DataStore answers in
     * a frame, and a card briefly showing the wrong choice is better than one briefly showing none.
     */
    val themeChoice: StateFlow<ThemeChoice> = settings.themeChoice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ThemeChoice.DEFAULT)

    /**
     * Chooses how the app is lit.
     *
     * The screens above this one read the same preference, so the change is visible at once -
     * including on the screen the choice was made on.
     */
    fun setThemeChoice(value: ThemeChoice) {
        viewModelScope.launch { settings.setThemeChoice(value) }
    }

    /**
     * Whether the editor is being served, and at what address; null while it is off.
     *
     * Read from the holder rather than kept here, because the address *is* the state: a screen
     * rebuilt while the editor is running still shows the truth, and a switch that says on with
     * nothing listening is not a state this can get into.
     */
    val webEditorUrl: StateFlow<String?> = WebEditorState.url

    /**
     * The switch.
     *
     * Turning it on starts a foreground service, which Android only allows from an app the walker
     * is actually looking at - which is here, and is why the editor is started from the launcher
     * activity and from nowhere else. The answer is written before the service is told, so a phone
     * put down mid-press agrees with the switch rather than with the default when it comes back.
     */
    fun setWebEditor(enabled: Boolean) {
        viewModelScope.launch {
            settings.setWebEditorEnabled(enabled)
            webEditor?.setEnabled(enabled)
        }
    }

    /**
     * Whether a computer has to bring the token to reach the editor.
     *
     * Seeded with `false` rather than left to DataStore's first answer, like the basemap above: the
     * card shows the default an untouched install has - no token is asked for - instead of a switch
     * that sits blank for a frame and then moves.
     */
    val webEditorTokenRequired: StateFlow<Boolean> = settings.webEditorTokenRequired
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /**
     * The token switch.
     *
     * Writes the setting, and then serves the editor again if it is being served: a run is built
     * with the answer it started from, so the one already going has to be built again for this to
     * mean anything. The address on the card changes with it - a run with no token is a bare address
     * - which is the honest way to show what has just been chosen, including that it is now open.
     */
    fun setWebEditorTokenRequired(required: Boolean) {
        viewModelScope.launch {
            settings.setWebEditorTokenRequired(required)
            if (isEditorServing()) webEditor?.restart()
        }
    }

    /**
     * Whether the phone is kept awake - its screen on, dimmed - while the editor is being served.
     *
     * Off when unset, seeded like the switch above so the card shows an untouched install's answer
     * rather than a switch that sits blank for a frame. Turned on it costs battery, which is why the
     * card says so; turned on it is also the one thing that answers the desk's imagery going blank a
     * few minutes after the phone's screen goes off.
     */
    val webEditorKeepAwake: StateFlow<Boolean> = settings.webEditorKeepAwake
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /**
     * The awake switch.
     *
     * Written, and then the run is served again if one is going - the same two halves as the token
     * switch, and for the same reason: a run holds the phone awake in the way it was built with, so
     * the run already going has to be built again for this to mean anything.
     */
    fun setWebEditorKeepAwake(keepAwake: Boolean) {
        viewModelScope.launch {
            settings.setWebEditorKeepAwake(keepAwake)
            if (isEditorServing()) webEditor?.restart()
        }
    }

    private val _storedTiles = MutableStateFlow(TileStoreSummary(0, 0))
    val storedTiles: StateFlow<TileStoreSummary> = _storedTiles

    /** What the last backup or restore said. One line, because it is one card. */
    private val _dataMessage = MutableStateFlow<String?>(null)
    val dataMessage: StateFlow<String?> = _dataMessage

    private val _dataBusy = MutableStateFlow(false)
    val dataBusy: StateFlow<Boolean> = _dataBusy

    /** A chosen file waiting to be restored, with both sides of the trade in numbers. */
    private val _pendingRestore = MutableStateFlow<PendingRestore?>(null)
    val pendingRestore: StateFlow<PendingRestore?> = _pendingRestore

    /** A backup that has been read, and what restoring it would replace. */
    data class PendingRestore(
        val source: RestoreSource,
        val file: BackupSummary,
        val current: BackupSummary
    ) {
        /** "The file" or "The copy in GitHub, cqrt/trail-day-backups", for the dialog. */
        val heldBy: String
            get() = when (source) {
                is RestoreSource.File -> "The file"
                is RestoreSource.Offsite -> "The copy in ${source.targetLabel}"
            }

        /**
         * The line about the copy itself: which file, from where, and how old.
         *
         * The age is the number that matters before replacing everything - a copy from
         * three days ago is a different decision from one from March - and it comes from
         * inside the file rather than from when this phone last wrote one.
         */
        fun provenance(nowEpochMs: Long = System.currentTimeMillis()): String? =
            (source as? RestoreSource.Offsite)?.let { copy ->
                "${copy.name} in ${copy.targetLabel} was " +
                    OffsiteBackupRules.ageOf(copy.savedAtEpochMs, nowEpochMs)
            }
    }

    /** Where a copy being restored came from. */
    sealed interface RestoreSource {
        /** The file the walker chose in the system picker. */
        data class File(val uri: Uri) : RestoreSource

        /** A copy in the off-site destination, by the name it is kept under. */
        data class Offsite(
            val reference: String,
            val name: String,
            val targetLabel: String,
            val savedAtEpochMs: Long
        ) : RestoreSource
    }

    val versionLabel: String = "version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    /** "trail-day-backup-2026-09-14.json" - offered as the file name in the picker. */
    fun backupFileName(): String = backup?.suggestedFileName() ?: "trail-day-backup.json"

    /** Writes everything the app holds to the file the walker chose. */
    fun exportBackupTo(uri: Uri) {
        val controller = backup ?: return
        viewModelScope.launch {
            _dataBusy.value = true
            _dataMessage.value = runCatching { controller.exportTo(uri) }
                .map { summary -> "Backed up ${summary.describe()}." }
                .getOrElse { "Nothing was written: ${it.message}" }
            _dataBusy.value = false
        }
    }

    /**
     * Reads the chosen file and asks first. Restoring replaces everything, so the
     * numbers on both sides are shown before anything is touched.
     */
    fun chooseBackupToRestore(uri: Uri) {
        val controller = backup ?: return
        viewModelScope.launch {
            _dataBusy.value = true
            _dataMessage.value = null
            runCatching {
                PendingRestore(
                    source = RestoreSource.File(uri),
                    file = controller.inspect(uri),
                    current = controller.currentSummary()
                )
            }
                .onSuccess { _pendingRestore.value = it }
                .onFailure { _dataMessage.value = it.message }
            _dataBusy.value = false
        }
    }

    fun confirmRestore() {
        val controller = backup ?: return
        val pending = _pendingRestore.value ?: return
        viewModelScope.launch {
            _dataBusy.value = true
            _dataMessage.value = runCatching {
                when (val source = pending.source) {
                    is RestoreSource.File -> controller.restoreFrom(source.uri)
                    is RestoreSource.Offsite -> {
                        val copy = offsite?.invoke()
                            ?: throw IllegalStateException("that destination is no longer set up")
                        copy.restore(source.reference)
                    }
                }
            }
                .map { summary -> "Restored ${summary.describe()}." }
                .getOrElse { "Nothing was restored: ${it.message}" }
            _pendingRestore.value = null
            _dataBusy.value = false
        }
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }

    /**
     * The off-site copy's own state.
     *
     * Separate from the file card's message and busy flag because the two ask different
     * questions, and a message about one must never appear under the other.
     */
    private val _offsiteMessage = MutableStateFlow<String?>(null)
    val offsiteMessage: StateFlow<String?> = _offsiteMessage

    private val _offsiteBusy = MutableStateFlow(false)
    val offsiteBusy: StateFlow<Boolean> = _offsiteBusy

    private val _offsiteCheck = MutableStateFlow<OffsiteCheckState>(OffsiteCheckState.Idle)
    val offsiteCheck: StateFlow<OffsiteCheckState> = _offsiteCheck

    /** Set only when the destination holds more than one copy: the walker picks. */
    private val _copiesToChoose = MutableStateFlow<List<StoredBackup>?>(null)
    val copiesToChoose: StateFlow<List<StoredBackup>?> = _copiesToChoose

    /** The repository field, seeded from what is stored, with the key field's race guard. */
    private val _repoText = MutableStateFlow("")
    val repoText: StateFlow<String> = _repoText

    private val _tokenText = MutableStateFlow("")
    val tokenText: StateFlow<String> = _tokenText

    private var typedRepo = false
    private var typedToken = false

    val backupDestination: StateFlow<BackupDestination> = settings.backupDestination.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        BackupDestination.OFF
    )

    /** The file the copy goes to, by name. Empty when none has been chosen. */
    val backupFileName: StateFlow<String> = settings.backupFileName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    val backUpAutomatically: StateFlow<Boolean> = settings.backUpAutomatically
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** "Last off-site copy: saved 5 days ago, on 12 Sep 2026", or null when there is none. */
    val lastBackupLabel: StateFlow<String?> = settings.lastOffsiteBackupAt
        .map { at ->
            if (at <= 0L) {
                null
            } else {
                "Last off-site copy: " + OffsiteBackupRules.ageOf(at, System.currentTimeMillis())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Seeding the fields from what is stored, and **where this sits matters**.
     *
     * Kotlin initialises a class top to bottom, and `viewModelScope` is `Dispatchers.Main.immediate`:
     * a seeding coroutine can run *before the constructor has finished* - on CI it did, and the write
     * above landed on a null field: `SettingsViewModel$6.invokeSuspend` at line 318, an NPE on a
     * `MutableStateFlow`, and the whole instrumented run behind it (v0.6.41). The window is as wide as
     * whatever the constructor has left to do, so any construction off the main thread is a chance to
     * fall through it.
     *
     * Below every field it touches, there is no window: the assignments happen before the launch that
     * can read them. A field this block seeds belongs above it, not below.
     */
    init {
        viewModelScope.launch {
            // The key the field starts from, read before anything can be typed into it - see the note
            // above this block for why that order matters.
            _keyText.value = settings.developerLinzKey.first()
            _storedTiles.value = TileStoreSummary(store.storedTileCount(), store.storedBytes())
        }

        // The off-site fields are seeded the same way, and for the same reason: pasting a
        // token takes seconds on a slow device, and a late read would wipe half of one.
        viewModelScope.launch {
            val repo = settings.backupRepo.first()
            val token = settings.backupToken.first()
            if (!typedRepo) _repoText.value = repo
            if (!typedToken) _tokenText.value = token
        }
    }

    fun setRepoText(text: String) {
        typedRepo = true
        _repoText.value = text
        _offsiteCheck.value = OffsiteCheckState.Idle
        _offsiteMessage.value = null
    }

    fun setTokenText(text: String) {
        typedToken = true
        _tokenText.value = text
        _offsiteCheck.value = OffsiteCheckState.Idle
        _offsiteMessage.value = null
    }

    /**
     * Saves the repository and the token.
     *
     * Neither is ever written into a backup. The token stays on this phone: the copy lives
     * in the repository the token can write to, so a backup carrying it would hand over the
     * repository as well.
     */
    fun saveOffsite() {
        viewModelScope.launch {
            settings.setBackupRepo(_repoText.value)
            settings.setBackupToken(_tokenText.value)
            _offsiteCheck.value = OffsiteCheckState.Idle
            _offsiteMessage.value = if (_tokenText.value.isBlank()) {
                "Saved, but with no token nothing can be written to the repository."
            } else {
                "Saved. The repository is ${_repoText.value.trim()}, and the token is kept on " +
                    "this phone only - never in a backup."
            }
        }
    }

    /** Turns the off-site copy off, or points it at a file or a repository. */
    fun setBackupDestination(destination: BackupDestination) {
        viewModelScope.launch {
            settings.setBackupDestination(destination)
            _offsiteCheck.value = OffsiteCheckState.Idle
            _offsiteMessage.value = when (destination) {
                BackupDestination.OFF ->
                    "Off-site copies off. The file you export by hand is the only copy."

                BackupDestination.FILE -> if (settings.backupFileUri.first().isBlank()) {
                    "Choose the file the copy should be written to."
                } else {
                    "Copies are written to ${settings.backupFileName.first()}."
                }

                BackupDestination.GITHUB -> if (settings.backupToken.first().isBlank()) {
                    "Enter a token for the repository, and press Save."
                } else {
                    "Copies go to ${settings.backupRepo.first()}."
                }
            }
        }
    }

    /**
     * Puts a copy in the off-site destination, now.
     *
     * The one thing this will not do is replace a good copy with an empty database - the
     * backup itself refuses that, and the message says why rather than quietly doing
     * nothing. A fresh install is exactly the case it exists for.
     */
    fun backUpOffsiteNow() {
        viewModelScope.launch {
            _offsiteBusy.value = true
            _offsiteMessage.value = null
            val copy = offsite?.invoke()
            _offsiteMessage.value = when {
                copy == null -> notSetUpYet()

                else -> when (
                    val result = runCatching { copy.backUpNow() }
                        .getOrElse { failure ->
                            OffsiteResult.Failed(failure.message ?: "the copy could not be written")
                        }
                ) {
                    is OffsiteResult.Written -> {
                        settings.setLastOffsiteBackupAt(System.currentTimeMillis())
                        "Backed up ${result.summary.describe()} to ${copy.destinationLabel}."
                    }

                    OffsiteResult.RefusedEmpty -> OffsiteBackupRules.refusedBecauseEmpty()

                    is OffsiteResult.Failed -> "Nothing was written: ${result.message}"
                }
            }
            _offsiteBusy.value = false
        }
    }

    /**
     * Asks the destination whether it will take the copy - and whether it is private.
     *
     * The privacy answer cannot be got any other way, and it matters: a repository that
     * accepts the token and is public would put your records in public, and nothing else
     * in the app would ever mention it.
     */
    fun testOffsite() {
        viewModelScope.launch {
            _offsiteCheck.value = OffsiteCheckState.Checking
            _offsiteMessage.value = null
            val copy = offsite?.invoke()
            _offsiteCheck.value = when {
                copy == null -> OffsiteCheckState.Failed(notSetUpYet())

                else -> runCatching { copy.check() }
                    .map { answer ->
                        when {
                            answer == null -> OffsiteCheckState.Worked(
                                "A file cannot be tested without writing to it. Use \"Back up now\"."
                            )

                            !answer.isPrivate -> OffsiteCheckState.Worked(
                                note = "${answer.fullName} answered, and the token can write to it.",
                                warning = "That repository is public, so anyone can read your " +
                                    "tracks and activities. Make it private before " +
                                    "backing up."
                            )

                            !answer.canPush -> OffsiteCheckState.Worked(
                                note = "${answer.fullName} is private.",
                                warning = "The token can see it but not write to it. A " +
                                    "fine-grained token needs Contents: read and write."
                            )

                            else -> OffsiteCheckState.Worked(
                                "${answer.fullName} is a private repository, and the token can " +
                                    "write the copy to it."
                            )
                        }
                    }
                    .getOrElse { failure ->
                        OffsiteCheckState.Failed(failure.message ?: "the destination could not be reached")
                    }
            }
        }
    }

    /**
     * Turns the weekly backup on or off.
     *
     * Only the setting is written: the application watches it and enqueues or cancels the
     * work, so there is one place that keeps the schedule honest - and a phone restored from
     * a backup starts backing itself up the same way.
     */
    fun setBackUpAutomatically(enabled: Boolean) {
        viewModelScope.launch {
            settings.setBackUpAutomatically(enabled)
            _offsiteMessage.value = if (enabled) {
                "Automatic backup on: the copy is written once a week, when there is a connection."
            } else {
                "Automatic backup off. Nothing is written in the background."
            }
        }
    }

    /**
     * A file chosen as the destination, kept so the copy can be written there again.
     *
     * The permission is taken as well as the `Uri`: without it the file works until the app
     * is restarted and then quietly cannot be written at all.
     */
    fun chooseOffsiteFile(uri: Uri) {
        keepFileAccess(uri)
        val name = fileLabel(uri)
        viewModelScope.launch {
            settings.setBackupFile(uri.toString(), name)
            settings.setBackupDestination(BackupDestination.FILE)
            _offsiteMessage.value = "Off-site copies will be written to $name."
        }
    }

    /** Fetches what the destination holds, and asks before replacing anything with it. */
    fun reviewOffsiteRestore() {
        viewModelScope.launch {
            _offsiteBusy.value = true
            _offsiteMessage.value = null
            val copy = offsite?.invoke()
            if (copy == null) {
                _offsiteMessage.value = notSetUpYet()
            } else {
                runCatching { copy.list() }
                    .onSuccess { copies ->
                        when {
                            copies.isEmpty() ->
                                _offsiteMessage.value = "There is no copy in ${copy.destinationLabel} yet."
                            // One copy is the ordinary case, and asking which one when there
                            // is only one would be a question with no content.
                            copies.size == 1 -> openOffsiteRestore(copy, copies.first())
                            else -> _copiesToChoose.value = copies
                        }
                    }
                    .onFailure {
                        _offsiteMessage.value = "The copies could not be listed: ${it.message}"
                    }
            }
            _offsiteBusy.value = false
        }
    }

    /** The walker has picked which of several copies to restore. */
    fun chooseOffsiteCopy(stored: StoredBackup) {
        _copiesToChoose.value = null
        viewModelScope.launch {
            _offsiteBusy.value = true
            offsite?.invoke()?.let { copy -> openOffsiteRestore(copy, stored) }
            _offsiteBusy.value = false
        }
    }

    fun cancelOffsiteChoice() {
        _copiesToChoose.value = null
    }

    /**
     * Reads the copy's counters and hands them to the same dialog the file path uses.
     *
     * The copy is fetched again when the walker confirms rather than held in memory: a
     * season of recordings is not worth keeping in the heap to save one download, and the
     * second read is what makes the dialog's numbers true of what is actually restored.
     */
    private suspend fun openOffsiteRestore(copy: OffsiteBackup, stored: StoredBackup) {
        runCatching { copy.preview(stored) }
            .onSuccess { preview ->
                _pendingRestore.value = PendingRestore(
                    source = RestoreSource.Offsite(
                        reference = stored.reference,
                        name = stored.name,
                        targetLabel = copy.destinationLabel,
                        savedAtEpochMs = preview.savedAtEpochMs
                    ),
                    file = preview.summary,
                    current = preview.here
                )
            }
            .onFailure { failure ->
                _offsiteMessage.value = "That copy could not be read: ${failure.message}"
            }
    }

    /** Why nothing can happen yet, in terms of the one thing that is missing. */
    private suspend fun notSetUpYet(): String = when (settings.backupDestination.first()) {
        BackupDestination.OFF -> "Choose where the off-site copy should go first."
        BackupDestination.FILE -> "Choose the file the copy should be written to first."
        BackupDestination.GITHUB -> "Enter the token for the repository, and press Save, first."
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Seven taps on the version line, which is Android's own number for the same door. */
        private const val TAPS_TO_UNLOCK = 7

        /** How many taps from the end the hint starts saying how many are left. */
        private const val COUNTDOWN_FROM = 3

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val settings = SettingsRepository(appContext)

                    // Cheap: there is one database for the whole app already, and this only holds onto it.
                    fun backupRepository() = BackupRepository(
                        db = TrailDayDatabase.get(appContext),
                        appVersion = BuildConfig.VERSION_NAME
                    )

                    SettingsViewModel(
                        settings = settings,
                        store = TileServerHolder.imageryStore(appContext),
                        // The editor's service, started from the screen that has the switch:
                        // Android only lets a visible app start a foreground service.
                        webEditor = object : WebEditorSwitch {
                            override fun setEnabled(enabled: Boolean) {
                                if (enabled) {
                                    WebEditorService.start(appContext)
                                } else {
                                    WebEditorService.stop(appContext)
                                }
                            }

                            override fun restart() {
                                WebEditorService.refresh(appContext)
                            }
                        },
                        backup = BackupController(
                            repository = backupRepository(),
                            context = appContext,
                            switches = settings
                        ),
                        // Built per use rather than held, so changing the destination on
                        // this screen takes effect on the next press of a button.
                        offsite = {
                            val target = BackupTargets.from(appContext, settings)
                            if (target == null) {
                                null
                            } else {
                                val repository = backupRepository()
                                OffsiteBackup(
                                    controller = BackupController(
                                        repository = repository,
                                        context = appContext,
                                        switches = settings
                                    ),
                                    repository = repository,
                                    target = target
                                )
                            }
                        },
                        keepFileAccess = { uri ->
                            runCatching {
                                appContext.contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                )
                            }
                            Unit
                        },
                        fileLabel = { uri -> FileBackupTarget(appContext, uri).label }
                    )
                }
            }
        }
    }
}
