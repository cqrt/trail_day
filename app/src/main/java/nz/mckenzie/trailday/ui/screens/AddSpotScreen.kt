package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.viewmodel.AddSpotViewModel

/**
 * Adds a waypoint to the map: one tap where it is, a name, and it is kept.
 *
 * One tap is the whole of it, because a waypoint is a single coordinate - the water, the lunch spot,
 * the junction. A line is not made here at all: the app's lines come from DOC, from a file, or from
 * walking them. The description is how the walker says what kind of spot it is; the app no longer
 * asks them to choose hut or campsite before they have said a word about it.
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

    var detailsOpen by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }
    var draftDescription by remember { mutableStateOf("") }
    var draftPhoto by remember { mutableStateOf<ByteArray?>(null) }

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
                title = { Text("Add a waypoint") },
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
                    Text(
                        text = if (placed) "Placed here" else "No waypoint yet",
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
                    Button(onClick = { detailsOpen = true }, enabled = placed) {
                        Text("Save")
                    }
                }
            }
        }
    }

    if (detailsOpen) {
        ModalBottomSheet(onDismissRequest = { detailsOpen = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Save this waypoint", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draftDescription,
                    onValueChange = { draftDescription = it },
                    label = { Text("Description") },
                    supportingText = {
                        Text("What is here? A campsite, water, a junction - a line for the next visit.")
                    },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                WaypointPhotoField(
                    photo = draftPhoto,
                    onPhoto = { draftPhoto = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        detailsOpen = false
                        viewModel.save(draftName, draftDescription, draftPhoto)
                    },
                    enabled = draftName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save waypoint")
                }
                TextButton(
                    onClick = { detailsOpen = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
