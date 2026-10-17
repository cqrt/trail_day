package nz.mckenzie.trailday.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetPhrase
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.ElevationPhrase
import nz.mckenzie.trailday.domain.geo.ElevationProfile
import nz.mckenzie.trailday.domain.geo.TravelMode
import nz.mckenzie.trailday.domain.geo.TravelTime
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.map.AssetGeoJson
import nz.mckenzie.trailday.map.AssetLine
import nz.mckenzie.trailday.ui.formatDate
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.formatEstimate
import nz.mckenzie.trailday.ui.formatLengthOf
import nz.mckenzie.trailday.viewmodel.AssetDetailViewModel

/**
 * One track or place: where it is, what shape it is, and the trips recorded on it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    viewModel: AssetDetailViewModel,
    onBack: () -> Unit,
    // Editing happens on a screen of its own, so this screen only has to say where to go.
    onEdit: () -> Unit,
    onKeepGround: () -> Unit = {},
    onOpenRecording: (Long) -> Unit = {}
) {
    val track by viewModel.track.collectAsStateWithLifecycle()

    val shape = AssetShape.fromStorage(track?.shape)
    val kind = AssetKind.fromStorage(track?.kind)
    val geometry by viewModel.geometry.collectAsStateWithLifecycle()
    // The height of the line, from the geometry the phone already holds. Null for a track drawn on
    // a map, which has no heights: the range line and the chart say nothing rather than a zero.
    val elevation = remember(geometry) { ElevationProfile.of(geometry.line) }
    val bounds by viewModel.bounds.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val geoJson = remember(geometry, track, kind) {
        AssetGeoJson.build(
            listOf(
                AssetLine(
                    assetId = track?.id ?: 0L,
                    name = track?.name.orEmpty(),
                    colorHex = AssetColors.forKind(kind),
                    points = geometry.line,
                    sideTracks = geometry.sideTracks,
                    kind = kind,
                    shape = shape
                )
            )
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri -> uri?.let(viewModel::exportGpx) }

    var confirmingDelete by remember { mutableStateOf(false) }
    var actionsOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(track?.name ?: "Place") },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(IconGlyph.BACK, contentDescription = "Back") } },
                actions = {
                    // Everything that is not the line itself lives behind this: editing the
                    // place, exporting it, deleting it. Material puts those in a sheet rather
                    // than as three buttons competing for the top bar.
                    IconButton(onClick = { actionsOpen = true }) {
                        AppIcon(IconGlyph.MORE, contentDescription = "More actions")
                    }
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    BasemapView(
                        basemap = basemap,
                        apiKey = apiKey,
                        assetGeoJson = geoJson,
                        fitBounds = bounds,
                        // How close this little map is allowed to sit. Asked for in as many words:
                        // the page was landing at the deepest zoom the map has, where the imagery
                        // runs out, and 15 is the view the walker asked for - a paddock with the
                        // place in it. A place too big to fit at 15 still gets its whole self on
                        // the card, because that is the fit it always had.
                        fitBoundsMaxZoom = ASSET_PAGE_MAX_ZOOM,
                        modifier = Modifier.fillMaxSize()
                    )
                    AttributionStrip(
                        basemap = basemap,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 4.dp)
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(AssetPhrase.kind(kind), style = MaterialTheme.typography.titleMedium)
                    // How long the line is, and nothing at all when there is no line: a spot is
                    // somewhere you stop, and "Length 0 m" is a measurement nobody took. The list's
                    // rows say it the same way, through the same one rule.
                    formatLengthOf(shape, track?.lengthM ?: 0.0).takeIf { it.isNotEmpty() }?.let { length ->
                        Text(
                            text = AssetPhrase.lengthLabel() + " " + length,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    // How high the line goes, when the file it came from said. One line, in the
                    // words the domain gives; a track drawn here has no heights and says nothing.
                    ElevationPhrase.range(elevation)?.let { range ->
                        Text(text = range, style = MaterialTheme.typography.bodyMedium)
                    }

                    // How long the line takes, on foot and on a bike, from the one piece of arithmetic
                    // every screen that estimates time uses. Both, because a place does not know which
                    // the walker will do - and the same climb the line's heights gave is what the time
                    // is made of, rather than a second reading of the ground that could disagree.
                    if (shape == AssetShape.LINE) {
                        val lengthM = track?.lengthM ?: 0.0
                        val climbM = elevation?.climbM ?: 0.0
                        if (lengthM > 0.0) {
                            val onFoot = TravelTime.seconds(lengthM, climbM, TravelMode.WALKING)
                            val riding = TravelTime.seconds(lengthM, climbM, TravelMode.RIDING)
                            Text(
                                text = "About " + formatEstimate(onFoot) + " " +
                                    TravelMode.WALKING.phrase + " \u00b7 " + formatEstimate(riding) + " " +
                                    TravelMode.RIDING.phrase,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    elevation?.let { profile ->
                        ElevationChart(
                            profile = profile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                        )
                    }
                    track?.notes?.takeIf { it.isNotBlank() }?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            message?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }

            Text("Trips", style = MaterialTheme.typography.titleSmall)
            if (recordings.isEmpty()) {
                EmptyState(
                    glyph = IconGlyph.RECORDINGS,
                    title = "No trip for this place",
                    body = "Recording while walking or riding this line leaves the evidence " +
                        "of what was actually followed."
                )
            }
            recordings.forEach { session ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { onOpenRecording(session.id) }) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(session.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${formatDate(session.startedAtEpochMs)} \u00b7 " +
                                "${formatDistance(session.distanceM)} \u00b7 " +
                                "${session.pointCount} point${if (session.pointCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this place?") },
            text = { Text("Its trips stay, but lose their link to it. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    viewModel.delete()
                    onBack()
                }, colors = destructiveTextButtonColors()) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            }
        )
    }

    // The secondary actions, in a sheet. The place is re-read from the database when it is
    // opened rather than copied once, so a rename made from the edit screen cannot be saved
    // back here as a stale name.
    if (actionsOpen) {
        ModalBottomSheet(onDismissRequest = { actionsOpen = false }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                // A menu with a way out that is written down. Swiping the sheet away or
                // pressing back both work, and neither of them is anything the walker can
                // see: a sheet of destructive choices should say how to leave it.
                TextButton(
                    onClick = { actionsOpen = false },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 8.dp)
                ) { Text("Cancel") }
                ListItem(
                    modifier = Modifier.clickable {
                        actionsOpen = false
                        onEdit()
                    },
                    headlineContent = { Text("Edit details") },
                    supportingContent = { Text("Name, kind and notes") },
                    leadingContent = { AppIcon(IconGlyph.EDIT) }
                )
                ListItem(
                    modifier = Modifier.clickable {
                        actionsOpen = false
                        exportLauncher.launch("${track?.name ?: "place"}.gpx")
                    },
                    headlineContent = { Text("Export GPX") },
                    supportingContent = {
                        // What goes in the file depends on what the place is: a line and the side
                        // tracks off it, or the one coordinate a place has.
                        Text(
                            if (shape == AssetShape.LINE) {
                                "The line and its side tracks, for another device"
                            } else {
                                "Where it is, for another device"
                            }
                        )
                    },
                    leadingContent = { AppIcon(IconGlyph.EXPORT) }
                )

                // Only a line has ground around it: a spot is a spot, and a box drawn around one is a
                // different thing from a walk (which is the offline picker's own job).
                if (shape == AssetShape.LINE) {
                    ListItem(
                        modifier = Modifier.clickable {
                            actionsOpen = false
                            onKeepGround()
                        },
                        headlineContent = { Text("Keep the ground around it") },
                        supportingContent = {
                            Text("The map, and DOC's tracks and huts around the line, for no reception")
                        },
                        leadingContent = { AppIcon(IconGlyph.OFFLINE) }
                    )
                }
                ListItem(
                    modifier = Modifier.clickable {
                        actionsOpen = false
                        confirmingDelete = true
                    },
                    headlineContent = {
                        // The irreversible one, in the colour that says so rather than in the
                        // same green as Save.
                        Text("Delete this place", color = MaterialTheme.colorScheme.error)
                    },
                    supportingContent = { Text("Trips stay, but lose their link to it") },
                    leadingContent = {
                        AppIcon(glyph = IconGlyph.TRASH, tint = MaterialTheme.colorScheme.error)
                    }
                )
            }
        }
    }
}

/**
 * How close this page's little map may sit on its place.
 *
 * The fit on its own is only as good as the place's box, and a place is one coordinate: the
 * camera went to the deepest zoom the map has, where the imagery has run out, and the card came
 * up as bare background with the marker on it. Fifteen is the view the walker asked for by
 * name - close enough to see the paddock the place is in, wide enough to see the ground around
 * it. It is a ceiling rather than a setting: the fit still says where, and this only stops the
 * camera going closer than 15, so a place whose own fit is already wider than that - a long
 * line - is shown whole rather than cut off to satisfy a number.
 */
private const val ASSET_PAGE_MAX_ZOOM = 15.0
