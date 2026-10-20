package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.geo.ElevationPhrase
import nz.mckenzie.trailday.domain.geo.ElevationProfile
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.ui.formatDate
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.formatDuration
import nz.mckenzie.trailday.ui.theme.TrailDayElevation
import nz.mckenzie.trailday.viewmodel.RecordingDetail
import nz.mckenzie.trailday.viewmodel.RecordingDetailViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One trip in full: **the line that was walked or ridden, and what it came to.**
 *
 * The shape is the mock screens': the trip's own line on the map at the top with a round back button over it,
 * and a white sheet lifted over the foot of the map carrying the name, when and for how long, the height card
 * with its chart, and the figures - distance, time, moving time, average, climb and descent - in the mock
 * screens' divided cells.
 *
 * The delete is here rather than on the list, because this is where the line and its numbers are in front of the
 * walker before it goes.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val placeGoesToo by viewModel.placeGoesToo.collectAsStateWithLifecycle()

    var confirmingDelete by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) {
        if (deleted) {
            // Consume before navigating, so re-entering this screen does not immediately bounce back out.
            viewModel.consumeDeleted()
            onBack()
        }
    }

    val elevation = remember(detail) { detail?.points?.let { ElevationProfile.of(it) } }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // The trip's own line, across the whole width: the map is what the page is about, so it is the top
            // of the page rather than a picture inside a card.
            Box {
                BasemapView(
                    basemap = basemap,
                    apiKey = apiKey,
                    assetGeoJson = geoJson,
                    fitBounds = remember(detail) { boundsOf(detail) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MAP_HEIGHT)
                )
                RoundIconButton(
                    glyph = IconGlyph.BACK,
                    description = "Back",
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )
                AttributionStrip(
                    basemap = basemap,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 40.dp, start = 4.dp)
                )
            }

            // The sheet, lifted over the foot of the map so the two read as one screen - and lifted off it
            // with the same shadow every other thing floating over a map wears.
            Column(
                modifier = Modifier
                    .offset(y = -SHEET_LIFT)
                    .fillMaxWidth()
                    .shadow(
                        elevation = TrailDayElevation.floating,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                detail?.let { recording ->
                    Text(
                        text = recording.name,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = subtitleOf(recording),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // The height card: the range in the domain's words and, under it, the shape.
                    elevation?.let { profile ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "ELEVATION",
                                    style = MaterialTheme.typography.labelSmall,
                                    letterSpacing = 1.1.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ElevationPhrase.range(profile)?.let { range ->
                                    Text(text = range, style = MaterialTheme.typography.bodyMedium)
                                }
                                ElevationChart(
                                    profile = profile,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(96.dp)
                                )
                            }
                        }
                    }

                    FiguresCard(rows = figuresOf(recording, elevation))

                    if (differsFromRecorded(recording)) {
                        Text(
                            text = "Recorded as ${formatDistance(recording.recordedDistanceM)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                message?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }

                DestructiveOutlinedButton(text = "Delete activity", onClick = { confirmingDelete = true })
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this activity?") },
            // What goes, in words, before anything does: a trip that made a place takes the place with it,
            // and one that was made for a place leaves the place standing.
            text = {
                Text(
                    if (placeGoesToo) {
                        "Its points go with it, and the track it made - this is the only walk of it. " +
                            "This cannot be undone."
                    } else {
                        "Its points go with it. This cannot be undone."
                    }
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

/** "Sun 18 Oct · 1m 00s · For Track 18 Oct", or the same with what happened to the link. */
private fun subtitleOf(recording: RecordingDetail): String = listOfNotNull(
    formatDate(recording.startedAtEpochMs),
    if (recording.isFinished) formatDuration(recording.durationMs) else "never finished",
    when {
        recording.assetName != null -> "For ${recording.assetName}"
        // A line of DOC's never becomes a place, so a trip that followed one says whose line it was.
        recording.docRef != null -> "Following a DOC track"
        recording.assetId != null -> "Its place has since been deleted"
        else -> "A new track"
    }
).joinToString(" \u00b7 ")

/**
 * The trip's figures, in the mock screens' cells: how far, how long, how much of that was moving, how fast,
 * and how much ground it took up and down.
 *
 * **Moving time is the total less the pauses**, which is what the pass recorded: a walker who stopped for
 * lunch did not spend it walking. The average is over the moving time for the same reason, and says nothing
 * while there is no moving time to divide by.
 */
internal fun figuresOf(recording: RecordingDetail, elevation: ElevationProfile?): List<List<Figure>> {
    val end = recording.endedAtEpochMs ?: recording.startedAtEpochMs
    val pausedMs = recording.breaks.sumOf { brk ->
        ((brk.toEpochMs ?: end) - brk.fromEpochMs).coerceAtLeast(0L)
    }
    val movingMs = (recording.durationMs - pausedMs).coerceAtLeast(0L)
    val distanceM = if (recording.geometryDistanceM > 0.0) {
        recording.geometryDistanceM
    } else {
        recording.recordedDistanceM
    }
    val average = if (movingMs > 0L && distanceM > 0.0) {
        String.format(Locale.US, "%.1f km/h", distanceM / 1000.0 / (movingMs / 3_600_000.0))
    } else {
        "\u2013"
    }

    return listOf(
        listOf(
            Figure(label = "Distance", value = formatDistance(distanceM)),
            Figure(label = "Total time", value = formatDuration(recording.durationMs))
        ),
        listOf(
            Figure(label = "Moving", value = formatDuration(movingMs)),
            Figure(label = "Average", value = average)
        ),
        listOf(
            Figure(label = "Climb", value = elevation?.let { "${it.climbM.roundToInt()} m" } ?: "\u2013"),
            Figure(label = "Descent", value = elevation?.let { "${it.descentM.roundToInt()} m" } ?: "\u2013")
        )
    )
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

/** How much of the screen the trip's own line gets. */
private val MAP_HEIGHT = 320.dp

/** How far the sheet is lifted over the map, so the two read as one screen. */
private val SHEET_LIFT = 28.dp
