package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.geo.TravelMode
import nz.mckenzie.trailday.domain.geo.TravelTime
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.formatEstimate
import nz.mckenzie.trailday.viewmodel.CreateJourneyViewModel

/**
 * Building a walk from DOC's separate tracks.
 *
 * One screen and one map. A tap places a point on the nearest of DOC's lines; two points draw the walk
 * between them **along the tracks**; every tap after that extends it. The card at the foot says what the
 * walk is so far - how long, how long it takes, how much it climbs - so the decision is made while
 * tapping rather than after saving.
 *
 * The heights are LINZ's, not DOC's: DOC publishes none. They arrive a moment after the length does, and
 * a journey made where they cannot be had keeps no heights at all rather than a zero.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateJourneyScreen(
    viewModel: CreateJourneyViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
    val docGeoJson by viewModel.docGeoJson.collectAsStateWithLifecycle()
    val previewGeoJson by viewModel.previewGeoJson.collectAsStateWithLifecycle()
    val startBounds by viewModel.startBounds.collectAsStateWithLifecycle()
    val picks by viewModel.picks.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val climb by viewModel.climbM.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    var naming by rememberSaveable { mutableStateOf(false) }

    // The walk is kept: the screen leaves for it.
    LaunchedEffect(saved) {
        saved?.let { id ->
            naming = false
            onSaved(id)
        }
    }

    val cardPaddingPx = with(LocalDensity.current) { 220.dp.roundToPx() }

    // **The zoom keys are not decoration here.** The screen opens on the whole of what DOC's tracks were
    // downloaded for, which for a region is a few hundred kilometres across - and at that scale a hundred
    // metres is a third of a pixel, so a tap cannot land on a line at all. The keys are how a walker gets
    // to a scale where their thumb can pick the track they mean.
    val zoom = rememberMapZoomState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create a journey") },
                navigationIcon = {
                    IconButton(onClick = onBack) { AppIcon(IconGlyph.BACK, contentDescription = "Back") }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            BasemapView(
                basemap = basemap,
                assetGeoJson = previewGeoJson,
                docGeoJson = docGeoJson,
                fitBounds = startBounds,
                // The card is the question, so the ground it asks about is framed above it.
                fitBoundsPaddingBottomPx = cardPaddingPx,
                onMapClick = { latitude, longitude, _ -> viewModel.tap(latitude, longitude) },
                zoomStep = zoom.step,
                zoomCount = zoom.count,
                modifier = Modifier.fillMaxSize()
            )

            MapZoomButtons(
                zoom = zoom,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
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
                        text = message ?: when {
                            plan != null -> "Tap another point, or finish."
                            picks.isEmpty() -> "Tap where the walk starts."
                            else -> "Tap where it goes next."
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )

                    plan?.let { built ->
                        Text(
                            text = listOf(
                                formatDistance(built.lengthM),
                                formatEstimate(
                                    TravelTime.seconds(built.lengthM, climb ?: 0.0, TravelMode.WALKING)
                                ),
                                if (climb == null) "↑ --" else "↑ ${formatMetres(climb!!)} m"
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (built.bridgeM > 0.0) {
                            Text(
                                text = "One part of ${formatDistance(built.bridgeM)} where DOC's line " +
                                    "breaks is joined straight.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (picks.isNotEmpty()) {
                            TextButton(onClick = { viewModel.startOver() }, enabled = !saving) {
                                Text("Start over")
                            }
                            OutlinedButton(
                                onClick = { viewModel.undo() },
                                enabled = !saving,
                                modifier = Modifier.weight(1f)
                            ) { Text("Undo") }
                        }
                        Button(
                            onClick = { naming = true },
                            enabled = plan != null && !saving,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (saving) "Keeping…" else "Finish")
                        }
                    }
                }
            }
        }
    }

    if (naming) {
        val suggested = plan?.name.orEmpty()
        var typed by remember(suggested) { mutableStateOf(suggested) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Name this journey") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "It will be in Places, under Journeys, ready to follow.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.keep(typed) }, enabled = !saving) {
                    Text("Keep this journey")
                }
            },
            dismissButton = {
                TextButton(onClick = { naming = false }) { Text("Cancel") }
            }
        )
    }
}

/** Metres as a whole number, with a comma in the thousands: "2,140". */
private fun formatMetres(metres: Double): String =
    "%,.0f".format(java.util.Locale.getDefault(), metres)
