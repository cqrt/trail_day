package nz.mckenzie.trailday.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import nz.mckenzie.trailday.domain.asset.AssetLayer
import nz.mckenzie.trailday.domain.asset.YourPlaceLayer
import nz.mckenzie.trailday.domain.backup.BackupDestination
import nz.mckenzie.trailday.domain.backup.BackupSettingsRecord
import nz.mckenzie.trailday.domain.backup.BackupSwitches
import nz.mckenzie.trailday.domain.look.MapLook
import nz.mckenzie.trailday.domain.settings.ThemeChoice
import nz.mckenzie.trailday.domain.tiles.Basemap

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "trail_day_settings")

/**
 * User-editable settings.
 *
 * The LINZ Basemaps key is stored here rather than being relied on from
 * BuildConfig, because standard access keys **expire every 90 days** - an app
 * that only had a baked-in key would stop showing imagery with no way to fix it.
 *
 * It also reads and writes the switches a backup carries, because this is where they
 * already live: [BackupSwitches] is the narrow view of them that the backup code is given,
 * so nothing that writes a backup can reach the token or the key by accident.
 */
class SettingsRepository(private val context: Context) : BackupSwitches {

    private val basemapPref = stringPreferencesKey("basemap")
    private val themeChoicePref = stringPreferencesKey("theme_choice")
    private val mapLookPref = stringPreferencesKey("map_look")
    private val hiddenMapLayersPref = stringSetPreferencesKey("hidden_map_layers")
    private val hiddenYourPlacesPref = stringSetPreferencesKey("hidden_your_places")
    /** The old "Your Tracks" switch: read once to seed [hiddenYourPlaces], never written again. */
    private val legacyYourTracksHiddenPref = booleanPreferencesKey("your_tracks_hidden")
    private val placeNamesPref = booleanPreferencesKey("place_names")
    private val teAraroaPref = booleanPreferencesKey("te_araroa")
    private val otherTracksPref = booleanPreferencesKey("other_tracks")
    private val webEditorTokenRequiredPref = booleanPreferencesKey("web_editor_token_required")

    /**
     * Whether the app serves the editor to a computer - the first switch on the card.
     *
     * Stored, and **on when unset**. Stored because the two questions a card like this asks are
     * different ones: this is what the walker asked for, and the address under it is what is
     * actually happening. On, because the desk is how the work gets drawn, and a switch that has to
     * be thrown on the phone before a man can sit down at the computer is a switch that stops the
     * feature being used - which is why the phone now serves from the moment the app is opened.
     *
     * The default is also why turning it off has to be remembered: a phone restarted into serving,
     * after the walker said no, would be the app putting itself back on the network, and
     * that is not a thing a default may do.
     */
    private val webEditorEnabledPref = booleanPreferencesKey("web_editor_enabled")
    private val webEditorKeepAwakePref = booleanPreferencesKey("web_editor_keep_awake")
    private val updateChecksPref = booleanPreferencesKey("update_checks_enabled")
    private val docPlacesDroppedPref = booleanPreferencesKey("doc_places_dropped")
    private val lastNotifiedUpdatePref = stringPreferencesKey("last_notified_update")
    private val backupDestinationPref = stringPreferencesKey("backup_destination")
    private val backupRepoPref = stringPreferencesKey("backup_repo")
    private val backupTokenPref = stringPreferencesKey("backup_token")
    private val backupFileUriPref = stringPreferencesKey("backup_file_uri")
    private val backupFileNamePref = stringPreferencesKey("backup_file_name")
    private val backUpAutomaticallyPref = booleanPreferencesKey("backup_automatically")
    private val lastOffsiteBackupAtPref = longPreferencesKey("last_offsite_backup_at")
    private val lastOffsiteFailureNotifiedAtPref = longPreferencesKey("last_offsite_failure_notified_at")

    /**
     * Whether the **Developer options** section has been unlocked, by seven taps on the version line.
     *
     * Off when unset, which is the whole point: an ordinary walker never sees the section. Once it is on
     * it stays on across restarts, the way Android's own developer options do - and the switch inside the
     * section is how it goes away again.
     */
    private val developerOptionsPref = booleanPreferencesKey("developer_options")

    /**
     * The walker's own LINZ key, empty when the app's own proxy is what fetches the tiles.
     *
     * A credential rather than a record: it never travels in a backup, and nothing but the developer
     * section reads it. See [nz.mckenzie.trailday.domain.tiles.LinzAccess].
     */
    private val developerLinzKeyPref = stringPreferencesKey("developer_linz_key")

    /**
     * Whether the **Developer options** section is showing.
     *
     * Read by the Settings screen and by nothing else: everything the section controls is read through
     * the preference it writes, so the door being open or shut changes nothing about the app.
     */
    val developerOptions: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[developerOptionsPref] ?: false
    }

    suspend fun setDeveloperOptions(unlocked: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[developerOptionsPref] = unlocked }
    }

    /**
     * The walker's own LINZ key, empty when the app's own proxy is what fetches the tiles.
     *
     * The key in force is this and nothing else - there is no compiled-in default and no fallback - so
     * clearing it is the way back to the proxy, and nothing about it reaches a backup.
     */
    val developerLinzKey: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[developerLinzKeyPref].orEmpty()
    }

    suspend fun setDeveloperLinzKey(value: String) {
        context.settingsDataStore.edit { prefs ->
            val trimmed = value.trim()
            if (trimmed.isEmpty()) prefs.remove(developerLinzKeyPref) else prefs[developerLinzKeyPref] = trimmed
        }
    }

    /**
     * Which map the walker draws under the work.
     *
     * Aerial imagery by default, which is what every existing install has been looking at: a new
     * basemap is an addition to the app, not a change to somebody's maps mid-season. The list of
     * what can be chosen, and everything that differs between the choices, is in
     * [nz.mckenzie.trailday.domain.tiles.Basemap].
     */
    val basemap: Flow<Basemap> = context.settingsDataStore.data.map { prefs ->
        Basemap.fromStorage(prefs[basemapPref])
    }

    suspend fun setBasemap(value: Basemap) {
        context.settingsDataStore.edit { prefs -> prefs[basemapPref] = value.id }
    }

    /**
     * How the app is lit: light, dark, or whatever this phone is set to.
     *
     * Following the phone when unset, which is what every install has been doing since the theme
     * could only be the system's - a setting that arrived later must not darken a phone that was
     * one way yesterday.
     *
     * **Not carried in a backup**, and the difference between it and the switches that are is worth
     * saying: a backup is read on another phone, and the setting in *that* phone's hand, and the
     * light around it, are not the ones that answered this here. The basemap and the map's layer
     * switches are left out of a backup for the same reason - they are answers about this device's
     * screen rather than about the work, and [BackupSettingsRecord] carries the work.
     */
    val themeChoice: Flow<ThemeChoice> = context.settingsDataStore.data.map { prefs ->
        ThemeChoice.fromStorage(prefs[themeChoicePref])
    }

    suspend fun setThemeChoice(value: ThemeChoice) {
        context.settingsDataStore.edit { prefs -> prefs[themeChoicePref] = value.id }
    }

    /**
     * The width and colour of everything the map draws.
     *
     * **Not carried in a backup**, for the reason the theme is not: a look is an answer about this
     * phone's screen and this walker's eyes, not about the work, and [BackupSettingsRecord] carries
     * the work. A restored phone starts at the app's own look and is dressed again in a minute.
     *
     * A look that is the app's own is written as no value at all, which keeps a fresh install's file
     * to what has actually been chosen - the same rule the hidden layers follow.
     */
    val mapLook: Flow<MapLook> = context.settingsDataStore.data.map { prefs ->
        MapLook.fromStorage(prefs[mapLookPref])
    }

    suspend fun setMapLook(value: MapLook) {
        context.settingsDataStore.edit { prefs ->
            val stored = value.toStorage()
            if (stored.isEmpty()) prefs.remove(mapLookPref) else prefs[mapLookPref] = stored
        }
    }

    /**
     * The layers of the work the map is not drawing.
     *
     * What is stored is what is **hidden**, so the default is everything and a layer added in a
     * later build arrives visible - see [AssetLayer]. An empty set is written as no value at all:
     * that is the same thing to read, and it keeps a fresh install's file to what has actually
     * been chosen.
     */
    val hiddenMapLayers: Flow<Set<AssetLayer>> = context.settingsDataStore.data.map { prefs ->
        AssetLayer.hiddenIn(prefs[hiddenMapLayersPref])
    }

    suspend fun setHiddenMapLayers(layers: Set<AssetLayer>) {
        context.settingsDataStore.edit { prefs ->
            val ids = layers.mapTo(mutableSetOf()) { it.id }
            if (ids.isEmpty()) prefs.remove(hiddenMapLayersPref) else prefs[hiddenMapLayersPref] = ids
        }
    }

    /**
     * Hides or shows one layer, in a single write.
     *
     * The set is read and written inside the same edit, as one step, rather than from a value the
     * caller is holding: two taps in a row - which is exactly what a screen of switches is - would
     * otherwise both read what was there before either of them, and the second tap would undo the
     * first. One edit is done at a time and reads what the last one left, so reading the truth inside
     * it is what makes the two taps add up.
     */
    suspend fun hideMapLayer(layer: AssetLayer, hidden: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val now = AssetLayer.hiddenIn(prefs[hiddenMapLayersPref])
            val ids = (if (hidden) now + layer else now - layer).mapTo(mutableSetOf()) { it.id }
            if (ids.isEmpty()) prefs.remove(hiddenMapLayersPref) else prefs[hiddenMapLayersPref] = ids
        }
    }

    /**
     * Which parts of the walker's own work are left off the map.
     *
     * Stored as what is **hidden**, like the DOC switches: an install that has never touched the
     * sheet draws everything, and a layer added in a later build arrives visible. Empty means every
     * part is shown; all four means the walker has cleared every row of that section.
     *
     * A preference written before this sheet existed is read as the nearest equivalent rather than
     * dropped: the old "Your Tracks" switch hid the planned tracks and the recorded lines, and the
     * old "Bike tracks" and "Waypoints" ids are still in the DOC set on an upgraded phone.
     */
    val hiddenYourPlaces: Flow<Set<YourPlaceLayer>> = context.settingsDataStore.data.map { prefs ->
        prefs[hiddenYourPlacesPref]?.let { stored ->
            // An explicit "all visible" marker, rather than an empty set: it is what stops the
            // legacy mapping below from putting back a switch the walker has just cleared.
            if (ALL_VISIBLE in stored) emptySet() else YourPlaceLayer.hiddenIn(stored)
        } ?: legacyHiddenYourPlaces(prefs)
    }

    private fun legacyHiddenYourPlaces(prefs: Preferences): Set<YourPlaceLayer> =
        buildSet {
            if (prefs[legacyYourTracksHiddenPref] == true) {
                add(YourPlaceLayer.TRACKS)
                add(YourPlaceLayer.RECORDINGS)
            }
            val old = prefs[hiddenMapLayersPref].orEmpty()
            if (AssetLayer.BIKE_TRACKS.id in old) add(YourPlaceLayer.BIKE_TRACKS)
            if (LEGACY_WAYPOINTS in old || LEGACY_PLACES in old) add(YourPlaceLayer.SPOTS)
        }

    /**
     * Hides or shows one part of the walker's own work, in a single write.
     *
     * The old value is read inside the edit so two taps add up instead of undoing each other. An
     * explicit all-visible marker is written when nothing is hidden: it is what stops the legacy
     * mapping above from putting back a switch the walker has just cleared.
     */
    suspend fun setYourPlaceHidden(layer: YourPlaceLayer, hidden: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val now = prefs[hiddenYourPlacesPref]?.let(YourPlaceLayer::hiddenIn)
                ?: legacyHiddenYourPlaces(prefs)
            val ids = (if (hidden) now + layer else now - layer).mapTo(mutableSetOf()) { it.id }
            prefs[hiddenYourPlacesPref] = if (ids.isEmpty()) setOf(ALL_VISIBLE) else ids
        }
    }

    /**
     * Puts all of the walker's own work back on the map, in one write.
     *
     * This is the whole of what the sheet's old master switch wrote: hiding everything is now four taps of
     * the four rows, but "Show everything" is still one, and it has to put these back too.
     */
    suspend fun showAllYourPlaces() {
        context.settingsDataStore.edit { prefs ->
            prefs[hiddenYourPlacesPref] = setOf(ALL_VISIBLE)
        }
    }

    /**
     * Whether LINZ's place names are drawn over the imagery - the map's own "Place names" switch.
     *
     * **Off when unset**, and the argument is what the app is for: the walker is looking at their
     * own paddock, and a town name over it is something asked for rather than something that
     * arrives. It is also the honest default for a layer whose tiles are fetched from the internet
     * on the way past: until somebody asks, this app draws what it already holds and nothing more.
     *
     * Stored, because it is a property of the map rather than of a visit - a walker who has asked
     * to see which town that is wants the answer tomorrow too. See
     * [nz.mckenzie.trailday.domain.tiles.PlaceLabels] for where the names come from, and why they
     * cost nothing to look at twice: the tiles are kept in the same store as the imagery.
     */
    val placeNames: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[placeNamesPref] ?: false
    }

    suspend fun setPlaceNames(shown: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[placeNamesPref] = shown }
    }

    /**
     * Whether the Te Araroa trail is drawn - the map's own "Te Araroa trail" switch.
     *
     * **On when unset**, and the argument is the opposite of the place names': the trail is what this
     * app is about, it is already on the phone (it is bundled, not fetched), and a walker who has never
     * heard of it is exactly the walker the line is worth drawing for. Nobody has to ask for the thing
     * they came for, and the switch is there for the walker who is looking at their own paddock.
     *
     * Stored, because it is a property of the map rather than of a visit. See
     * [nz.mckenzie.trailday.domain.tiles.TeAraroa] for where the line comes from and whose licence it is.
     */
    val teAraroa: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[teAraroaPref] ?: true
    }

    suspend fun setTeAraroa(shown: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[teAraroaPref] = shown }
    }

    /**
     * Whether the tracks other people publish are drawn - the map's own "Other tracks" switch.
     *
     * **On when unset**, which is the rule the reference layers already follow: what a preference stores
     * is what is *hidden*, so a layer that arrives in a later build arrives visible rather than missing,
     * and a walker who has never touched this switch must not be shown a map that quietly left something
     * out. Nothing is drawn until an area has been downloaded either way - see
     * [nz.mckenzie.trailday.domain.other.OtherTracksCache] - so the switch being on costs a walker who
     * has downloaded nothing exactly nothing.
     */
    val otherTracks: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[otherTracksPref] ?: true
    }

    suspend fun setOtherTracks(shown: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[otherTracksPref] = shown }
    }

    /**
     * Whether a computer has to bring the token to reach the editor - the card's "Only this address
     * can open it".
     *
     * **Off when unset.** That reverses the promise this switch shipped with in v0.6.25, and the
     * argument for reversing it is the network it is served on: the desk is for your own Wi-Fi,
     * where every device on it is the walker's, so the secret buys them little and costs them a
     * paste - while a switch that has to be found and turned on before the desk can be used is a
     * switch that stops the desk being used. What it costs is said on the card in as many words:
     * with it off, anyone on the Wi-Fi can open the editor and change the tracks.
     *
     * Turned on, it is what stops the other devices on the Wi-Fi - a visitor's phone, a tablet in the
     * ute, a printer - and, more to the point, any web page open on the walker's own computer, from
     * reaching a door that can change and delete the work. None of that is a router's business, which
     * is why the token is what makes the switch safe to throw on a network nobody vouches for.
     */
    val webEditorTokenRequired: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[webEditorTokenRequiredPref] ?: false
    }

    suspend fun setWebEditorTokenRequired(required: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[webEditorTokenRequiredPref] = required }
    }

    /**
     * Whether the walker wants the editor served - the card's first switch.
     *
     * The other half of the same question: the address on the card says what is happening, and this
     * says what was asked for. Both are needed, because "on by default" and "off stays off" cannot
     * both be read out of a running service.
     */
    val webEditorEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[webEditorEnabledPref] ?: true
    }

    suspend fun setWebEditorEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[webEditorEnabledPref] = enabled }
    }

    /**
     * Whether the phone's screen is kept on - so the phone itself is kept awake - while the editor is
     * being served. The card's third switch.
     *
     * **Off when unset**, and the reason is the battery: a phone held awake with its screen on costs
     * a lot more than one that is allowed to sleep, and most of the work at the desk fetches nothing -
     * it is read, drawn, and saved from what the phone already holds.
     *
     * On is for the case the walker hits when the computer browses onto ground the phone has never
     * downloaded. That is the one thing the desk cannot answer out of the phone's own store: the phone
     * has to go to LINZ for it, and a phone whose screen has gone off is a phone whose radio is going
     * to sleep - and no lock the app may hold reliably stops that. Half an hour still and unplugged it
     * is Doze, where the app's network is suspended whatever it holds; a few minutes in, on many
     * phones, it is the phone's own idea of sleep. Keeping the screen on is the one lever that answers
     * both, because a phone whose screen is on never enters either.
     *
     * What answers it without the battery cost is downloading the ground first, on the **Offline
     * areas** screen - which is what the card says where this switch is.
     */
    val webEditorKeepAwake: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[webEditorKeepAwakePref] ?: false
    }

    suspend fun setWebEditorKeepAwake(keepAwake: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[webEditorKeepAwakePref] = keepAwake }
    }

    /**
     * Whether the places an earlier build imported from DOC have been dropped from the walker's list.
     *
     * A one-time tidy, and this is what makes it once: it is written down as soon as it has run, whether
     * or not there was anything to drop, so an install does not count DOC's rows again on every visit to
     * the list. **Off when unset**, which is every install that has not had it yet - and the tidy itself
     * is the thing that says how many went, in numbers, before they go.
     *
     * It is not carried in a backup: a restored phone's places have no DOC copies in them, and a flag
     * saying they had been dropped would be a fact about a list that no longer exists.
     */
    val docPlacesDropped: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[docPlacesDroppedPref] ?: false
    }

    suspend fun setDocPlacesDropped(dropped: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[docPlacesDroppedPref] = dropped }
    }

    /**
     * Whether the app should look for a newer version of itself.
     *
     * On by default. This app is not installed from a store, so nothing else will ever
     * mention a new version - without this, the only way to find out would be to go and
     * read the releases page, which nobody does.
     */
    val updateChecksEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[updateChecksPref] ?: true
    }

    suspend fun setUpdateChecksEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[updateChecksPref] = enabled }
    }

    /**
     * The version the walker has already been told about.
     *
     * Remembered so a daily check does not become a daily notification about the same
     * release. Stored rather than inferred, so it survives a restart.
     */
    val lastNotifiedUpdate: Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[lastNotifiedUpdatePref].orEmpty() }

    suspend fun setLastNotifiedUpdate(version: String) {
        context.settingsDataStore.edit { prefs -> prefs[lastNotifiedUpdatePref] = version }
    }

    /**
     * Where the off-site copy goes. Off by default.
     *
     * Not on by default because the off-site half needs something from the walker - a
     * file to write to, or a repository and a token - and a setting that looks enabled and
     * quietly does nothing is worse than one that is plainly off.
     */
    val backupDestination: Flow<BackupDestination> = context.settingsDataStore.data.map { prefs ->
        BackupDestination.fromName(prefs[backupDestinationPref])
    }

    suspend fun setBackupDestination(destination: BackupDestination) {
        context.settingsDataStore.edit { prefs -> prefs[backupDestinationPref] = destination.name }
    }

    /** "owner/name". Seeded with the repository this app was built for. */
    val backupRepo: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[backupRepoPref].orEmpty().ifBlank { DEFAULT_BACKUP_REPO }
    }

    suspend fun setBackupRepo(value: String) {
        context.settingsDataStore.edit { prefs ->
            val trimmed = value.trim()
            if (trimmed.isEmpty()) prefs.remove(backupRepoPref) else prefs[backupRepoPref] = trimmed
        }
    }

    /**
     * The fine-grained token that may write to [backupRepo], and to nothing else.
     *
     * Stored like the LINZ key, in this app's own preferences, and deliberately never
     * written into a backup: the backup lives in the repository the token can write to, so a
     * file carrying the token would hand over the repository as well.
     */
    val backupToken: Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[backupTokenPref].orEmpty() }

    suspend fun setBackupToken(value: String) {
        context.settingsDataStore.edit { prefs ->
            val trimmed = value.trim()
            if (trimmed.isEmpty()) prefs.remove(backupTokenPref) else prefs[backupTokenPref] = trimmed
        }
    }

    /** The file the walker chose, as a `Uri` string. Empty when none has been chosen. */
    val backupFileUri: Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[backupFileUriPref].orEmpty() }

    /** The chosen file's name, kept so the screen can say where the copy goes. */
    val backupFileName: Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[backupFileNamePref].orEmpty() }

    /** Records the file the walker chose, with its name, in one write. */
    suspend fun setBackupFile(uri: String, name: String) {
        context.settingsDataStore.edit { prefs ->
            if (uri.isBlank()) {
                prefs.remove(backupFileUriPref)
                prefs.remove(backupFileNamePref)
            } else {
                prefs[backupFileUriPref] = uri
                prefs[backupFileNamePref] = name
            }
        }
    }

    /**
     * Whether the app should write the copy on its own.
     *
     * Weekly, not daily: a backup is protection against losing a season, and a season does
     * not change by the hour a man is on a tractor. Off by default, and only ever on with a
     * destination chosen, because what it costs the walker is their data allowance.
     */
    val backUpAutomatically: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[backUpAutomaticallyPref] ?: false
    }

    suspend fun setBackUpAutomatically(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[backUpAutomaticallyPref] = enabled }
    }

    /** When a copy was last written, for the line on the screen. Zero when never. */
    val lastOffsiteBackupAt: Flow<Long> =
        context.settingsDataStore.data.map { prefs -> prefs[lastOffsiteBackupAtPref] ?: 0L }

    suspend fun setLastOffsiteBackupAt(epochMs: Long) {
        context.settingsDataStore.edit { prefs -> prefs[lastOffsiteBackupAtPref] = epochMs }
    }

    /**
     * When a scheduled backup last failed, so the notification is news rather than a daily
     * nag. A backup that has been failing for a month should say so, but not thirty times.
     */
    val lastOffsiteFailureNotifiedAt: Flow<Long> =
        context.settingsDataStore.data.map { prefs -> prefs[lastOffsiteFailureNotifiedAtPref] ?: 0L }

    suspend fun setLastOffsiteFailureNotifiedAt(epochMs: Long) {
        context.settingsDataStore.edit { prefs -> prefs[lastOffsiteFailureNotifiedAtPref] = epochMs }
    }

    /**
     * The switches as a backup carries them.
     *
     * What is deliberately left out is as much of a decision as what is in here: the LINZ
     * key and the GitHub token are credentials, and a backup file may be read by whoever
     * finds it.
     */
    override suspend fun read(): BackupSettingsRecord = BackupSettingsRecord(
        updateChecksEnabled = updateChecksEnabled.first(),
        backUpAutomatically = backUpAutomatically.first(),
        destination = backupDestination.first().name
    )

    /**
     * Puts a restored phone's switches back.
     *
     * The file's destination name is honoured only if it names one this build knows; the
     * token, and the file, stay as this device has them - a restored phone still has to be
     * given its own token, and there is no honest way around that.
     */
    override suspend fun apply(record: BackupSettingsRecord) {
        setUpdateChecksEnabled(record.updateChecksEnabled)
        setBackUpAutomatically(record.backUpAutomatically)
        record.destination?.let { setBackupDestination(BackupDestination.fromName(it)) }
    }

    companion object {
        /** The repository this app's backups were set up for; the field can be changed. */
        const val DEFAULT_BACKUP_REPO = "cqrt/trail-day-backups"

        /** The id the old sheet stored for the walker's own point markers. */
        private const val LEGACY_WAYPOINTS = "waypoints"

        /** The id an even older sheet stored for every place. */
        private const val LEGACY_PLACES = "places"

        /** A stored marker meaning "the walker has chosen to show every Your Places tile". */
        private const val ALL_VISIBLE = "__all-visible"
    }
}
