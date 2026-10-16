package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetPhrase
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.asset.MethodPhrase
import nz.mckenzie.trailday.domain.asset.PassPhrase
import nz.mckenzie.trailday.domain.asset.SprayMethod
import nz.mckenzie.trailday.ui.AssetEditFields
import nz.mckenzie.trailday.ui.AssetEditResult
import nz.mckenzie.trailday.ui.AssetEdits
import nz.mckenzie.trailday.ui.formatPlainNumber
import nz.mckenzie.trailday.viewmodel.AssetDetailViewModel

/**
 * Editing one asset, on a screen of its own.
 *
 * This was a dialog. Nine fields do not fit in a dialog: on a phone it opened taller than
 * the screen, so the notes sat below the fold inside a box that could not scroll while the
 * keyboard was up - and the notes are the field people actually write in. A screen has room
 * for the whole form, the keyboard, and the one decision that matters at the end of it.
 *
 * What was typed is still checked by [AssetEdits] rather than here: this screen collects it,
 * and a refused edit stays on screen with the reason under it. Closing instead would leave
 * the operator believing a change had been stored.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssetEditScreen(
    viewModel: AssetDetailViewModel,
    onDone: () -> Unit
) {
    val asset by viewModel.editDraft.collectAsStateWithLifecycle()

    // Nothing until the asset is here: a form that opened with a blank name would save
    // that name over a real one.
    val current = asset ?: return

    var name by remember { mutableStateOf(current.name) }
    var kind by remember {
        // Read by its shape as well as its word, because a record written before the types existed
        // says only "infrastructure": whether that was a line or a spot is what says which of the two
        // it settles as when it is next saved.
        mutableStateOf(AssetKind.fromStorage(current.kind, AssetShape.fromStorage(current.shape)))
    }
    var method by remember { mutableStateOf(SprayMethod.fromStorage(current.method)) }
    var intervalDays by remember { mutableStateOf(current.intervalDays.toString()) }
    var swathWidth by remember {
        mutableStateOf(current.swathWidthM?.let(::formatPlainNumber).orEmpty())
    }
    var notes by remember { mutableStateOf(current.notes.orEmpty()) }
    var passes by remember { mutableStateOf(current.passesRequired) }
    var separation by remember {
        mutableStateOf(current.passSeparationM?.let(::formatPlainNumber).orEmpty())
    }
    var problem by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit asset") },
                navigationIcon = { IconButton(onClick = onDone) { AppIcon(IconGlyph.BACK, contentDescription = "Back") } },
                actions = {
                    TextButton(onClick = {
                        when (
                            val result = AssetEdits.apply(
                                current,
                                AssetEditFields(
                                    name = name,
                                    kind = kind,
                                    method = method,
                                    intervalDays = intervalDays,
                                    swathWidthM = swathWidth,
                                    notes = notes,
                                    passesRequired = passes,
                                    passSeparationM = separation
                                )
                            )
                        ) {
                            is AssetEditResult.Ok -> {
                                viewModel.save(result.asset)
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
                choices = AssetPhrase.kinds,
                selected = kind,
                // The shape follows the kind and is not asked about: a fenceline is a line because a
                // fenceline is something you follow, and a question that could answer otherwise is a
                // picnic table drawn across a paddock.
                onChoose = { choice -> kind = choice; problem = null },
                text = AssetPhrase::kind
            )
            OutlinedTextField(
                value = intervalDays,
                onValueChange = { intervalDays = it; problem = null },
                label = { Text("Days between sprays") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            ChoiceRow(
                label = "Spray method",
                choices = MethodPhrase.choices,
                selected = method,
                onChoose = { choice ->
                    // The boom is adjustable, so a width the operator typed is kept; only a
                    // blank field, or the previous method's own default, moves.
                    swathWidth = AssetEdits.swathAfterMethodChange(method, choice, swathWidth)
                    method = choice
                    problem = null
                },
                text = MethodPhrase::choice
            )
            // A kind that is ground is not asked these two questions: its area is measured from its own
            // shape, and one run round it is the job. [AssetEdits] is what stores nothing for them; this
            // is only the screen agreeing with it, and saying so rather than leaving a gap.
            if (kind.shape != AssetShape.AREA) {
            OutlinedTextField(
                value = swathWidth,
                onValueChange = { swathWidth = it; problem = null },
                label = { Text("Swath width (m)") },
                supportingText = { Text(AssetEdits.SWATH_HINT) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            // How many passes the job takes, and - when it takes two - how far apart they run. The
            // second field is what the app reads to decide whether it can tell which side each
            // pass was on; left empty it goes by direction and asks when even that cannot tell.
            ChoiceRow(
                label = "Passes to finish it",
                choices = PassPhrase.choices,
                selected = passes,
                onChoose = { choice ->
                    passes = choice
                    if (choice < PassPhrase.TWO_PASSES) separation = ""
                    problem = null
                },
                text = PassPhrase::choice
            )
            if (passes >= PassPhrase.TWO_PASSES) {
                OutlinedTextField(
                    value = separation,
                    onValueChange = { separation = it; problem = null },
                    label = { Text("Two passes about (m) apart") },
                    supportingText = { Text(PassPhrase.SEPARATION_HINT) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            } else {
                Text(
                    text = AssetEdits.GROUND_HINT,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it; problem = null },
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

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
