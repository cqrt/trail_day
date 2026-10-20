package nz.mckenzie.trailday.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetPhrase
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.ElevationPhrase
import nz.mckenzie.trailday.domain.geo.ElevationProfile
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.TravelMode
import nz.mckenzie.trailday.domain.geo.TravelTime
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.map.AssetGeoJson
import nz.mckenzie.trailday.map.AssetLine
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.ui.formatCoordinates
import nz.mckenzie.trailday.ui.formatDate
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.formatEstimate
import nz.mckenzie.trailday.ui.formatLengthOf
import nz.mckenzie.trailday.viewmodel.AssetDetailViewModel

/**
 * One place: **the place on its own ground, and a sheet of what it is.**
 *
 * The shape is the mock screens': the map is the top of the screen with the place on it and two round buttons
 * over it - back, and everything else this page can do - and a white sheet with rounded top corners carries the
 * name, the figures, the height chart, the notes and the trips recorded on it.
 *
 * The figures are the mock screens' own: a card divided into cells, each a small capital label with its value,
 * rather than one line of facts. The time to walk and ride comes from the same arithmetic the map's nearest
 * card and the recorder's target use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    viewModel: AssetDetailViewModel,
    onBack: () -> Unit,
    // Editing happens on a screen of its own, so this screen only has to say where to go.
    onEdit: () -> Unit,
    onKeepGround: () -> Unit = {},
    /** Record while following this place - or, for a spot, head for it. */
    onRecord: () -> Unit = {},
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

    val geoJson = remember(geometry, track, kind, recordings) {
        AssetGeoJson.build(
            listOf(
                AssetLine(
                    assetId = track?.id ?: 0L,
                    name = track?.name.orEmpty(),
                    // A line a walk has been made of wears the recorded green, with the map's own gold edge:
                    // the place's page and the map cannot disagree about what has been walked.
                    colorHex = AssetColors.forPlace(kind, recorded = recordings.isNotEmpty()),
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

    val lengthM = track?.lengthM ?: 0.0
    val lengthText = formatLengthOf(shape, lengthM)

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // The place on its own ground, the whole width of the screen: the map is what the page is about,
            // so it is the top of the page rather than a picture inside a card.
            Box {
                BasemapView(
                    basemap = basemap,
                    apiKey = apiKey,
                    assetGeoJson = geoJson,
                    fitBounds = bounds,
                    // How close this map is allowed to sit. Asked for in as many words: the page was
                    // landing at the deepest zoom the map has, where the imagery runs out, and 15 is the
                    // view the walker asked for - a paddock with the place in it. A place too big to fit at
                    // 15 still gets its whole self on the page, because that is the fit it always had.
                    fitBoundsMaxZoom = ASSET_PAGE_MAX_ZOOM,
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
                RoundIconButton(
                    glyph = IconGlyph.MORE,
                    description = "More actions",
                    onClick = { actionsOpen = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )

                AttributionStrip(
                    basemap = basemap,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 40.dp, start = 4.dp)
                )
            }

            // The sheet: white, its top corners round, lifted over the foot of the map so the two read as one
            // screen rather than a picture above a list.
            Column(
                modifier = Modifier
                    .offset(y = -SHEET_LIFT)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = track?.name ?: "Place",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = listOfNotNull(
                            AssetPhrase.kind(kind),
                            lengthText.takeIf { it.isNotEmpty() }
                        ).joinToString(" \u00b7 "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                figures(shape = shape, lengthM = lengthM, lengthText = lengthText, elevation = elevation, point = geometry.line.firstOrNull())

                // The height of the line, in the words the domain gives and, under them, its shape. A line
                // with no heights says nothing at all.
                ElevationPhrase.range(elevation)?.let { range ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = range, style = MaterialTheme.typography.bodyMedium)
                            elevation?.let { profile ->
                                ElevationChart(
                                    profile = profile,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(96.dp)
                                )
                            }
                        }
                    }
                }

                track?.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }

                message?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }

                // What this page is *for*: recording a pass against the place, which the recorder then reads
                // back against as the walker moves. Everything else is behind the button at the top.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onRecord, modifier = Modifier.weight(1f)) {
                        Text(if (shape == AssetShape.LINE) "Record this" else "Head for this")
                    }
                    OutlinedButton(
                        onClick = { actionsOpen = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("More")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "TRIPS",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (recordings.isEmpty()) {
                        EmptyState(
                            glyph = IconGlyph.RECORDINGS,
                            title = "No trip for this place",
                            body = "Records your walk of this track."
                        )
                    }
                    recordings.forEach { session ->
                        Card(modifier = Modifier.fillMaxWidth(), onClick = { onOpenRecording(session.id) }) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(session.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${formatDate(session.startedAtEpochMs)} \u00b7 " +
                                        "${formatDistance(session.distanceM)} \u00b7 " +
                                        "${session.pointCount} point" +
                                        (if (session.pointCount == 1) "" else "s"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                                "The track and its side tracks, for another device"
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
                            Text("The map, and DOC's tracks and huts around the track, for no reception")
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
 * The page's figures: how long, how much up, and how long that takes both ways.
 *
 * A spot has none of those and says where it is instead, which is the one fact worth having about a spot.
 */
@Composable
private fun figures(
    shape: AssetShape,
    lengthM: Double,
    lengthText: String,
    elevation: ElevationProfile?,
    point: GeoPoint?
) {
    val rows = if (shape != AssetShape.LINE || lengthM <= 0.0) {
        listOfNotNull(
            point?.let { listOf(Figure(label = "Where", value = formatCoordinates(it.lat, it.lng))) }
        )
    } else {
        val climbM = elevation?.climbM ?: 0.0
        val onFoot = TravelTime.seconds(lengthM, climbM, TravelMode.WALKING)
        val riding = TravelTime.seconds(lengthM, climbM, TravelMode.RIDING)
        listOf(
            listOf(
                Figure(label = "Length", value = lengthText),
                Figure(
                    label = "Climb",
                    value = elevation?.let { "${it.climbM.roundToInt()} m" } ?: "Not given"
                )
            ),
            listOf(
                Figure(
                    label = "Time",
                    value = "${formatEstimate(onFoot)} on foot \u00b7 ${formatEstimate(riding)} riding"
                )
            )
        )
    }
    FiguresCard(rows = rows)
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

/** How much of the screen the place's own ground gets: enough to see where it is, not the whole screen. */
private val MAP_HEIGHT = 320.dp

/** How far the sheet is lifted over the map, so the two read as one screen. */
private val SHEET_LIFT = 28.dp
