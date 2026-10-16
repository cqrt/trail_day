package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.ui.formatDate
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.formatDuration
import nz.mckenzie.trailday.viewmodel.RecordingDetail
import nz.mckenzie.trailday.viewmodel.RecordingDetailViewModel
import kotlin.math.abs

/**
 * One trip in full: the line that was walked or ridden, and which place it followed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordingDetailScreen(
    viewModel: RecordingDetailViewModel,
    onBack: () -> Unit
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
    val geoJson by viewModel.geoJson.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()

    var confirmingDelete by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) {
        if (deleted) {
            // Consume before navigating, so re-entering this screen does not
            // immediately bounce back out.
            viewModel.consumeDeleted()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.name ?: "Trip") },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(IconGlyph.BACK, contentDescription = "Back") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // A Box, where before this was the map alone, because the imagery licence requires a
            // credit wherever the imagery is shown and this map had none. The screenshot of a
            // pass is a place the imagery is read.
            Box {
                BasemapView(
                    basemap = basemap,
                    apiKey = apiKey,
                    assetGeoJson = geoJson,
                    fitBounds = remember(detail) { boundsOf(detail) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
                AttributionStrip(
                    basemap = basemap,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 4.dp)
                )
            }

            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                detail?.let { recording ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = when {
                                    recording.isFinished -> "Recorded ${formatDate(recording.startedAtEpochMs)}"
                                    else -> "Started ${formatDate(recording.startedAtEpochMs)}, never finished"
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Recorded ${formatDistance(recording.geometryDistanceM)} \u00b7 " +
                                    "${formatDuration(recording.durationMs)} \u00b7 " +
                                    "${recording.points.size} points",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (differsFromRecorded(recording)) {
                                Text(
                                    text = "Recorded as ${formatDistance(recording.recordedDistanceM)} " +
                                        "at the time; the distance above is re-derived from the stored points.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                text = when {
                                    recording.assetName != null -> "Followed for ${recording.assetName}"
                                    recording.assetId != null -> "Its place has since been deleted"
                                    else -> "Not linked to a planned place"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                message?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DestructiveOutlinedButton(text = "Delete trip", onClick = { confirmingDelete = true })
                }
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this trip?") },
            text = {
                Text(
                    "Its points go with it. This cannot be undone."
                )
            },
            confirmButton = {
                DestructiveTextButton(
                    text = "Delete",
                    onClick = {
                        confirmingDelete = false
                        viewModel.delete()
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            }
        )
    }
}

/** Camera bounds covering the recorded line. */
private fun boundsOf(detail: RecordingDetail?): LatLngBounds? {
    val points: List<GeoPoint> = detail?.points.orEmpty()
    if (points.size < 2) return null
    return LatLngBounds(
        minLat = points.minOf { it.lat },
        minLng = points.minOf { it.lng },
        maxLat = points.maxOf { it.lat },
        maxLng = points.maxOf { it.lng }
    )
}

/** True when the stored distance disagrees with the geometry by more than a rounding. */
private fun differsFromRecorded(detail: RecordingDetail): Boolean {
    val stored = detail.recordedDistanceM
    if (stored <= 0.0 || detail.geometryDistanceM <= 0.0) return false
    return abs(stored - detail.geometryDistanceM) / detail.geometryDistanceM > 0.01
}
