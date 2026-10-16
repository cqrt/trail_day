package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.asset.AssetPhrase
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.viewmodel.AddSpotViewModel

/**
 * Adds a place to the map: one tap where it is, a name, and it is kept.
 *
 * One tap is the whole of it, because a place is a single coordinate - a hut, a campsite, the water,
 * the lunch spot. A line is not made here at all: the app's lines come from DOC, from a file, or
 * from walking them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSpotScreen(viewModel: AddSpotViewModel, onBack: () -> Unit) {
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
    val geoJson by viewModel.draftGeoJson.collectAsStateWithLifecycle()
    val initialFrame by viewModel.initialFrame.collectAsStateWithLifecycle()
    val placed by viewModel.placed.collectAsStateWithLifecycle()
    val savedAssetId by viewModel.savedAssetId.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()

    var naming by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }

    LaunchedEffect(savedAssetId) {
        if (savedAssetId != null) {
            // Consume before navigating: the signal must not survive this screen.
            viewModel.consumeSaveResult()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add a spot") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(IconGlyph.BACK, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BasemapView(
                basemap = basemap,
                apiKey = apiKey,
                assetGeoJson = geoJson,
                // A spot is dropped where the walker is standing, so that is the frame.
                fitBounds = initialFrame,
                onMapClick = { latitude, longitude, _ -> viewModel.dropSpot(latitude, longitude) },
                modifier = Modifier.fillMaxSize()
            )

            AttributionStrip(
                basemap = basemap,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 6.dp)
            )

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 40.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // The three shapes a place can be, and only those three: what is dropped here is
                    // a point whatever it is called, so offering a track would be offering a line
                    // with one coordinate on it.
                    ChoiceRow(
                        label = "What it is",
                        choices = AssetPhrase.kindsFor(AssetShape.POINT),
                        selected = kind,
                        onChoose = viewModel::chooseKind,
                        text = AssetPhrase::kind
                    )
                    Text(
                        text = if (placed) "Placed here" else "No spot yet",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = message ?: if (placed) {
                            "Tap somewhere else to move it."
                        } else {
                            "Tap the map where it is."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = { naming = true }, enabled = placed) { Text("Save") }
                }
            }
        }
    }

    if (naming) {
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Name this spot") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        naming = false
                        viewModel.save(draftName)
                    },
                    enabled = draftName.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { naming = false }) { Text("Cancel") }
            }
        )
    }
}
