package nz.mckenzie.trailday.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.backup.BackupDestination
import nz.mckenzie.trailday.domain.settings.ThemeChoice
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.viewmodel.InstallState
import nz.mckenzie.trailday.viewmodel.KeyCheckState
import nz.mckenzie.trailday.viewmodel.OffsiteCheckState
import nz.mckenzie.trailday.viewmodel.SettingsViewModel
import nz.mckenzie.trailday.viewmodel.UpdateState
import nz.mckenzie.trailday.viewmodel.UpdateViewModel

/**
 * App settings: the basemap, the offline material, the backups and the update check.
 *
 * No map key lives here any more. LINZ's tiles are fetched through the app's own proxy, which holds
 * the key, so there is nothing for the walker to paste and nothing that expires on the phone - which
 * is why this screen is about the map, the material and the housekeeping rather than about a key.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    updateViewModel: UpdateViewModel,
    onOpenTracksAndPlaces: () -> Unit,
    onBack: () -> Unit
) {
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val installState by updateViewModel.install.collectAsStateWithLifecycle()
    val autoUpdateChecks by updateViewModel.autoCheckEnabled.collectAsStateWithLifecycle()
    // Android's permission to install this is granted on a system screen this app sends
    // the walker to, so it is state rather than a value read once: a plain read leaves
    // the warning on screen after they have already granted it.
    var canInstallUpdates by remember { mutableStateOf(updateViewModel.canInstall()) }
    LifecycleResumeEffect(Unit) {
        canInstallUpdates = updateViewModel.canInstall()
        onPauseOrDispose { }
    }
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
    val themeChoice by viewModel.themeChoice.collectAsStateWithLifecycle()
    // The hidden section, and everything it shows when it is open. See [DeveloperOptionsCard].
    val developerOptions by viewModel.developerOptions.collectAsStateWithLifecycle()
    val doorMessage by viewModel.doorMessage.collectAsStateWithLifecycle()
    val ownKeyLabel by viewModel.ownKeyLabel.collectAsStateWithLifecycle()
    val keyText by viewModel.keyText.collectAsStateWithLifecycle()
    val ownKeyInForce by viewModel.ownKeyInForce.collectAsStateWithLifecycle()
    val check by viewModel.check.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val webEditorUrl by viewModel.webEditorUrl.collectAsStateWithLifecycle()
    val webEditorTokenRequired by viewModel.webEditorTokenRequired.collectAsStateWithLifecycle()
    val webEditorKeepAwake by viewModel.webEditorKeepAwake.collectAsStateWithLifecycle()
    val storedTiles by viewModel.storedTiles.collectAsStateWithLifecycle()
    val dataMessage by viewModel.dataMessage.collectAsStateWithLifecycle()
    val dataBusy by viewModel.dataBusy.collectAsStateWithLifecycle()
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val offsiteMessage by viewModel.offsiteMessage.collectAsStateWithLifecycle()
    val offsiteBusy by viewModel.offsiteBusy.collectAsStateWithLifecycle()
    val offsiteCheck by viewModel.offsiteCheck.collectAsStateWithLifecycle()
    val copiesToChoose by viewModel.copiesToChoose.collectAsStateWithLifecycle()
    val backupDestination by viewModel.backupDestination.collectAsStateWithLifecycle()
    val backupFileName by viewModel.backupFileName.collectAsStateWithLifecycle()
    val backUpAutomatically by viewModel.backUpAutomatically.collectAsStateWithLifecycle()
    val lastBackupLabel by viewModel.lastBackupLabel.collectAsStateWithLifecycle()
    val repoText by viewModel.repoText.collectAsStateWithLifecycle()
    val tokenText by viewModel.tokenText.collectAsStateWithLifecycle()

    // The file the off-site copy is written to. Created through the same picker as the
    // manual backup, so it can be anywhere the walker can reach: a Downloads folder, a
    // stick, or a folder another app syncs.
    val offsiteFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::chooseOffsiteFile) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportBackupTo) }

    // Any MIME type: a backup can end up on a stick or in an email, and being unable
    // to select the file is worse than a lax filter.
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::chooseBackupToRestore) }

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var notificationsAllowed by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // Returning from system settings is the other way this can change, so re-read it
    // whenever the screen comes back to the front.
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }

    // Restoring replaces everything, so the numbers on both sides are shown first. The
    // off-site path asks through this same dialog, because it is the same decision - and a
    // second dialog with its own wording is how two of them end up disagreeing.
    pendingRestore?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "${pending.heldBy} holds ${pending.file.describe()}." +
                        (pending.provenance()?.let { age -> "\n\n$age" } ?: "") +
                        "\n\nRestoring replaces what is in the app now " +
                        "(${pending.current.describe()}). This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRestore) { Text("Cancel") }
            }
        )
    }

    // More than one phone has backed up to the same place, so the walker says which one.
    // The ordinary case is a single copy and never reaches this.
    copiesToChoose?.let { copies ->
        AlertDialog(
            onDismissRequest = viewModel::cancelOffsiteChoice,
            title = { Text("Restore which copy?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "There are ${copies.size} copies. The name says which phone wrote each."
                    )
                    copies.forEach { copy ->
                        OutlinedButton(
                            onClick = { viewModel.chooseOffsiteCopy(copy) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${copy.name} \u2014 ${copy.sizeLabel}")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::cancelOffsiteChoice) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(IconGlyph.BACK, contentDescription = "Back") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Text("Basemap", style = MaterialTheme.typography.titleMedium)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Basemap.entries.forEach { choice ->
                            BasemapChoice(
                                basemap = choice,
                                selected = choice == basemap,
                                onClick = { viewModel.setBasemap(choice) }
                            )
                        }
                    }


                    // Their tile usage policy asks for a way to report a problem, and a gate that
                    // is not on the map is exactly the sort of thing the walker would know about.
                    if (basemap == Basemap.OPENSTREETMAP) {
                        TextButton(onClick = { uriHandler.openUri(Basemap.OSM_REPORT_LINK) }) {
                            Text("Report a map issue to OpenStreetMap")
                        }
                    }
                }
            }

            // How the whole app is lit, which is the one setting here that changes the screen it is
            // set from. The theme around every screen reads this same preference, so a tap lands at
            // once - and a phone held up in the sun, or a tent at night, is a reason to say outright
            // rather than leave it to the phone's own idea of the time of day.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)

                    ChoiceRow(
                        label = "Display",
                        choices = ThemeChoice.entries,
                        selected = themeChoice,
                        onChoose = viewModel::setThemeChoice,
                        text = { it.displayName }
                    )

                    // The look of the map: a width and a colour for each kind of thing it draws. It is a
                    // row rather than a section here because it is the same sort of answer as the theme -
                    // how the app is drawn - and the questions it asks are six, which is a screen.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenTracksAndPlaces)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Named the same as the screen it opens, so the row and its heading agree.
                        Text(
                            text = "Colours & Styles",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        AppIcon(
                            glyph = IconGlyph.CHEVRON,
                            dimension = 18.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Drawing with a mouse instead of tapping a phone. The switch is the whole of the
            // feature's control: on is a foreground service serving this phone on the Wi-Fi, and
            // the address under it is the one to open on the computer. The switch is read from the
            // address, so it can never say on with nothing listening, while the choice behind it is
            // stored - on by default, and off stays off, because a phone that served again after the
            // walker had turned it off would be the app putting itself back on the network.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Text("Draw from a computer", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Serve the editor to a computer on this Wi-Fi",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = webEditorUrl != null,
                            onCheckedChange = viewModel::setWebEditor
                        )
                    }

                    Text(
                        "Your computer must be on the same Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // The default said out loud, because a phone serving without the walker
                    // having thrown anything is exactly the sort of thing that must not arrive as a
                    // surprise - and because this is the switch they may well want the other way.
                    Text(
                        text = "On from launch. Turn it off and it stays off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val address = webEditorUrl
                    if (address != null) {
                        Text(address, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { clipboard.setText(AnnotatedString(address)) }) {
                            Text("Copy the address")
                        }
                    }

                    // The token is what makes the switch above safe to throw on a network nobody
                    // vouches for, so turning it off is offered in the walker's own terms and with
                    // what it gives away said out loud - not as a word like "token" on its own.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Only this address can open it",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = webEditorTokenRequired,
                            onCheckedChange = viewModel::setWebEditorTokenRequired
                        )
                    }

                    Text(
                        text = if (webEditorTokenRequired) {
                            "The address ends in a secret code, made fresh each time it is turned on. " +
                                "Without it, the phone refuses."
                        } else {
                            "Anyone on this Wi-Fi can open the editor and change or delete your tracks."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // The phone staying awake. On the card because it costs something the walker
                    // should be the one to choose: a screen that stays lit all afternoon is a flat
                    // battery, and most of the work at the desk needs nothing fetched at all.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Keep this phone awake while it is serving",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = webEditorKeepAwake,
                            onCheckedChange = viewModel::setWebEditorKeepAwake
                        )
                    }

                    Text(
                        text = if (webEditorKeepAwake) {
                            "The screen stays on, dimmed, until this is turned off."
                        } else {
                            "The phone may sleep, and new ground comes up blank until you wake it."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Updates", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Version ${updateViewModel.currentLabel}. It asks GitHub once a day for " +
                            "a newer release.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Switch(
                            checked = autoUpdateChecks,
                            onCheckedChange = updateViewModel::setAutoCheck
                        )
                        Text(
                            text = "Tell me when a newer version is released",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (!canInstallUpdates) {
                        Text(
                            text = "Android needs permission to install updates.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = updateViewModel::openInstallPermission) {
                            Text("Allow installing updates")
                        }
                    }

                    when (val state = updateState) {
                        UpdateState.Idle -> Unit
                        UpdateState.Checking -> Text(
                            text = "Asking GitHub\u2026",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        is UpdateState.NothingToInstall -> Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        is UpdateState.Available -> Text(
                            text = "${state.update.title} is available (${state.update.sizeLabel}).",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    when (val state = installState) {
                        InstallState.Idle -> Unit

                        is InstallState.Downloading -> {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Downloading\u2026 ${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        InstallState.HandedToInstaller -> Text(
                            text = "Confirm the update when Android asks.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        is InstallState.Failed -> Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = updateViewModel::check,
                            enabled = updateState != UpdateState.Checking
                        ) {
                            Text(
                                if (updateState == UpdateState.Checking) {
                                    "Checking\u2026"
                                } else {
                                    "Check for updates"
                                }
                            )
                        }

                        (updateState as? UpdateState.Available)?.let { state ->
                            Button(
                                onClick = { updateViewModel.install(state.update) },
                                enabled = canInstallUpdates && installState !is InstallState.Downloading
                            ) {
                                Text("Update to ${state.update.version}")
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Text("Your data", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Your places and every activity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { exportLauncher.launch(viewModel.backupFileName()) },
                            enabled = !dataBusy
                        ) {
                            Text("Back up everything")
                        }
                        OutlinedButton(
                            onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                            enabled = !dataBusy
                        ) {
                            Text("Restore from a backup")
                        }
                    }
                    dataMessage?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        text = "Offline imagery is not in the backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Off-site copy", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "The same backup, kept by the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ChoiceRow(
                        label = "Where the copy goes",
                        choices = BackupDestination.entries,
                        selected = backupDestination,
                        onChoose = viewModel::setBackupDestination,
                        text = ::destinationLabel
                    )

                    when (backupDestination) {
                        BackupDestination.OFF -> Text(
                            text = "Nothing is written on its own.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        BackupDestination.FILE -> {
                            Text(
                                text = if (backupFileName.isBlank()) {
                                    "No file chosen yet."
                                } else {
                                    "Written to $backupFileName, replacing the last copy."
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedButton(
                                onClick = { offsiteFileLauncher.launch(viewModel.backupFileName()) },
                                enabled = !offsiteBusy
                            ) {
                                Text(
                                    if (backupFileName.isBlank()) {
                                        "Choose a file"
                                    } else {
                                        "Choose another file"
                                    }
                                )
                            }
                        }

                        BackupDestination.GITHUB -> {
                            Text(
                                text = "Every backup is a commit, so older copies remain.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = repoText,
                                onValueChange = viewModel::setRepoText,
                                label = { Text("Repository, owner/name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = tokenText,
                                onValueChange = viewModel::setTokenText,
                                label = { Text("Token for that repository") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(onClick = viewModel::saveOffsite) { Text("Save") }
                                OutlinedButton(
                                    onClick = viewModel::testOffsite,
                                    enabled = offsiteCheck != OffsiteCheckState.Checking
                                ) {
                                    Text(
                                        if (offsiteCheck == OffsiteCheckState.Checking) {
                                            "Testing\u2026"
                                        } else {
                                            "Test"
                                        }
                                    )
                                }
                            }

                            when (val state = offsiteCheck) {
                                OffsiteCheckState.Idle -> Unit

                                OffsiteCheckState.Checking ->
                                    Text("Asking GitHub\u2026", style = MaterialTheme.typography.bodySmall)

                                is OffsiteCheckState.Worked -> Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = state.note,
                                        color = if (state.warning == null) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    state.warning?.let { warning ->
                                        Text(
                                            text = warning,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                is OffsiteCheckState.Failed -> Text(
                                    text = "The destination did not answer: ${state.message}",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            Text(
                                text = "A fine-grained GitHub token with Contents: read and write on " +
                                    "this repository.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (backupDestination != BackupDestination.OFF) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Switch(
                                checked = backUpAutomatically,
                                onCheckedChange = viewModel::setBackUpAutomatically
                            )
                            Text("Back up once a week, on its own", style = MaterialTheme.typography.bodyMedium)
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = viewModel::backUpOffsiteNow, enabled = !offsiteBusy) {
                                Text(if (offsiteBusy) "Working\u2026" else "Back up now")
                            }
                            OutlinedButton(
                                onClick = viewModel::reviewOffsiteRestore,
                                enabled = !offsiteBusy
                            ) {
                                Text("Restore from the copy")
                            }
                        }
                    }

                    lastBackupLabel?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
                    offsiteMessage?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }

                    Text(
                        text = "An empty phone never overwrites the copy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    Text("This device", style = MaterialTheme.typography.titleMedium)
                    // The door: seven taps on the version line opens the Developer options section at
                    // the foot of this screen. Android's own joke, and Android's own number - see
                    // [SettingsViewModel.onVersionTap] for why it is not discoverable by accident.
                    Text(
                        text = viewModel.versionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { viewModel.onVersionTap() }
                    )
                    doorMessage?.let { hint ->
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "${storedTiles.tiles} imagery tiles cached, ${storedTiles.sizeLabel}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Downloaded areas, their progress and clearing them are on the " +
                            "Offline areas screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (developerOptions) {
                DeveloperOptionsCard(
                    viewModel = viewModel,
                    ownKeyLabel = ownKeyLabel,
                    keyText = keyText,
                    ownKeyInForce = ownKeyInForce,
                    check = check,
                    message = message
                )
            }
        }
    }
}

/**
 * The hidden section: what a walker who went looking for it gets.
 *
 * Off unless the version line has been tapped seven times - see [SettingsViewModel.onVersionTap] - and
 * the switch at its top is how it goes away again. Closing it hides these controls and **changes nothing
 * they set**: a walker who closes the section has not asked for their map back, and the way back is
 * **Clear**, which is a thing you press deliberately.
 *
 * The app ships no key of its own: by default every LINZ tile comes through the app's own proxy, which
 * holds the key so the app does not have to. A key entered here sends the app to LINZ itself instead -
 * see [nz.mckenzie.trailday.domain.tiles.LinzAccess] - which is what a walker with their own key, or a
 * developer checking against LINZ rather than the proxy, would want.
 */
@Composable
private fun DeveloperOptionsCard(
    viewModel: SettingsViewModel,
    ownKeyLabel: String,
    keyText: String,
    ownKeyInForce: Boolean,
    check: KeyCheckState,
    message: String?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Developer options", style = MaterialTheme.typography.titleMedium)
                Switch(checked = true, onCheckedChange = { viewModel.setDeveloperOptions(it) })
            }

            Text(
                text = if (ownKeyInForce) {
                    "Using your own LINZ key (${ownKeyLabel.ifBlank { "none" }}). Tiles come straight " +
                        "from LINZ, so the app's own proxy is not used."
                } else {
                    "Using the app's own proxy. A key here sends every LINZ request straight to LINZ " +
                        "instead, with the key on the URL."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = keyText,
                onValueChange = viewModel::setKeyText,
                label = { Text("Your LINZ key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = viewModel::saveKey) { Text("Save") }
                OutlinedButton(
                    onClick = viewModel::check,
                    enabled = check != KeyCheckState.Checking
                ) {
                    Text(if (check == KeyCheckState.Checking) "Checking…" else "Check")
                }
                OutlinedButton(onClick = { viewModel.setKeyText(""); viewModel.saveKey() }) {
                    Text("Clear")
                }
            }

            when (check) {
                KeyCheckState.Idle -> Unit

                KeyCheckState.Checking ->
                    Text("Asking LINZ for its style document…", style = MaterialTheme.typography.bodySmall)

                KeyCheckState.Worked -> Text(
                    text = "The key works: LINZ accepted it.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )

                is KeyCheckState.Failed -> Text(
                    text = "The key did not work: ${check.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            message?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }

            Text(
                text = "A key is a credential, so it is never written into a backup. LINZ's " +
                    "standard-access keys expire every 90 days, which is why Check is worth pressing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/**
 * Android's own per-app notification settings, which is where a prompt that will not
 * appear again gets undone.
 */
private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun destinationLabel(destination: BackupDestination): String = when (destination) {
    BackupDestination.OFF -> "Nowhere"
    BackupDestination.FILE -> "A file"
    BackupDestination.GITHUB -> "GitHub"
}