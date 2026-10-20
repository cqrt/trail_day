package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetPhrase
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.ui.AssetEditFields
import nz.mckenzie.trailday.ui.AssetEditResult
import nz.mckenzie.trailday.ui.AssetEdits
import nz.mckenzie.trailday.viewmodel.AssetDetailViewModel

/**
 * Editing one place, on a screen of its own.
 *
 * A screen rather than a dialog, so the form, the keyboard, the notes field and the one
 * decision that matters at the end of it all fit.
 *
 * What was typed is checked by [AssetEdits] rather than here: this screen collects it,
 * and a refused edit stays on screen with the reason under it. Closing instead would leave
 * the walker believing a change had been stored.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssetEditScreen(
    viewModel: AssetDetailViewModel,
    onDone: () -> Unit
) {
    val asset by viewModel.editDraft.collectAsStateWithLifecycle()
    val photo by viewModel.photo.collectAsStateWithLifecycle()

    // Nothing until the place is here: a form that opened with a blank name would save
    // that name over a real one.
    val current = asset ?: return
    val shape = AssetShape.fromStorage(current.shape)

    var name by remember { mutableStateOf(current.name) }
    var kind by remember { mutableStateOf(AssetKind.fromStorage(current.kind)) }
    var notes by remember { mutableStateOf(current.notes.orEmpty()) }
    var problem by remember { mutableStateOf<String?>(null) }
    // A picture can be replaced or removed without touching either until Save. The flag is what
    // distinguishes "never had one" from "the walker took this one away".
    var photoEdited by remember(current.id) { mutableStateOf(false) }
    var draftPhoto by remember(current.id) { mutableStateOf<ByteArray?>(null) }
    val shownPhoto = if (photoEdited) draftPhoto else photo

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit place") },
                navigationIcon = { IconButton(onClick = onDone) { AppIcon(IconGlyph.BACK, contentDescription = "Back") } },
                actions = {
                    TextButton(onClick = {
                        when (
                            val result = AssetEdits.apply(
                                current,
                                AssetEditFields(
                                    name = name,
                                    kind = kind,
                                    notes = notes
                                )
                            )
                        ) {
                            is AssetEditResult.Ok -> {
                                viewModel.save(
                                    asset = result.asset,
                                    photo = draftPhoto,
                                    photoChanged = photoEdited
                                )
                                onDone()
                            }

                            is AssetEditResult.Invalid -> problem = result.message
                        }
                    }) { Text("Save") }
                }
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; problem = null },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ChoiceRow(
                label = "What it is",
                // Only the kinds that agree with the shape this place already has: how it was made
                // decided that, and a hut asked to be a track would be a line with one coordinate
                // on it. See [AssetPhrase.kindsFor].
                choices = AssetPhrase.kindsFor(shape),
                selected = kind,
                // The shape follows the kind and is not asked about: a hut is a point because a hut
                // is somewhere you stop, and a question that could answer otherwise is a waypoint
                // drawn across a valley.
                onChoose = { choice -> kind = choice; problem = null },
                text = AssetPhrase::kind
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it; problem = null },
                label = { Text(if (shape == AssetShape.POINT) "Description" else "Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            if (shape == AssetShape.POINT) {
                Text("Photo", style = MaterialTheme.typography.labelLarge)
                WaypointPhotoField(
                    photo = shownPhoto,
                    onPhoto = {
                        photoEdited = true
                        draftPhoto = it
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            problem?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
