package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.look.LineWidthChoice
import nz.mckenzie.trailday.domain.look.LookColour
import nz.mckenzie.trailday.domain.look.LookControl
import nz.mckenzie.trailday.domain.look.LookTarget
import nz.mckenzie.trailday.domain.look.MapLook
import nz.mckenzie.trailday.domain.look.MarkerSizeChoice
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.map.DEFAULT_CAMERA_TARGET
import nz.mckenzie.trailday.map.LookSample
import nz.mckenzie.trailday.map.MapCameraMemory
import nz.mckenzie.trailday.viewmodel.TracksAndPlacesViewModel
import org.maplibre.android.geometry.LatLng

/** How many swatches go on one line of the shelf: four and four, whatever the screen width. */
private const val COLOURS_PER_ROW = 4

/** How much map the preview gets: enough for seven things to be told apart, not a whole screen. */
private val PREVIEW_HEIGHT = 240.dp

/**
 * The room left at the foot of the preview beyond the map's own padding, in pixels.
 *
 * The credit strip is drawn over the bottom of the preview, and the sample's three places are the
 * lowest thing in it: without this they sit under the strip. 112 px is a little more than the strip's
 * own height at this density.
 */
private const val PREVIEW_FOOT_ROOM_PX = 112

/**
 * **Colours & Styles**: a width and a colour for each of the seven things the map draws.
 *
 * The order of the screen is the order of the map: the trail first, because it is drawn under
 * everything else, then the lines from the top down, then the three places. Every row is one thing the
 * walker can see drawn, and the preview above them is the map they are looking at rather than a picture
 * of it - see [LookSample].
 *
 * **There is no Save.** A tap is the change: the preview redraws, the map behind this screen redraws,
 * and the place list and the desk pick it up the next time they are drawn. A screen that asked the
 * walker to confirm a colour would be a screen where the picture is a proposal.
 *
 * The seven rows are not the five kinds: **other tracks are a publisher, not a kind**, and Te Araroa is a
 * route - and giving each of them a row is what lets a walker keep a council's walkway apart from DOC's,
 * or the trail apart from both, once they have chosen a colour for tracks. See [LookTarget].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TracksAndPlacesScreen(
    viewModel: TracksAndPlacesViewModel,
    onBack: () -> Unit
) {
    val look by viewModel.look.collectAsStateWithLifecycle()
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
    val camera by MapCameraMemory.current.collectAsStateWithLifecycle()

    // The ground the map was left on, so the sample is drawn over the walker's own place rather than
    // over a stock picture of somewhere they have never been. A cold start has no camera yet, and the
    // whole country is what the map itself opens on.
    val centre = remember(camera) {
        GeoPoint(
            lat = camera?.lat ?: DEFAULT_CAMERA_TARGET.latitude,
            lng = camera?.lng ?: DEFAULT_CAMERA_TARGET.longitude
        )
    }
    val sample = remember(centre, look) { LookSample.build(centre, look) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Colours & Styles") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(IconGlyph.BACK, contentDescription = "Back")
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                BasemapView(
                    basemap = basemap,
                    assetGeoJson = sample.assets,
                    docGeoJson = sample.doc,
                    otherTracksGeoJson = sample.otherTracks,
                    // The reference layers are off on every other screen but the map's. Here they are
                    // on, because the sample draws one line from each of the three publishers and the
                    // walker is choosing a colour for one of them - and the trail, which has a row too.
                    showOtherTracks = true,
                    showTeAraroa = true,
                    teAraroaGeoJson = sample.trail,
                    // A preview: the page is about the look, and a drag would only slide the sample
                    // off the picture it is meant to show.
                    interactive = false,
                    showPosition = false,
                    initialTarget = LatLng(centre.lat, centre.lng),
                    // The whole sample, framed the way the map frames the walker's own work. A zoom
                    // picked by hand showed a fifth of it and four of the seven things fell off an edge.
                    fitBounds = remember(centre) { LookSample.bounds(centre) },
                    fitBoundsPaddingBottomPx = PREVIEW_FOOT_ROOM_PX,
                    modifier = Modifier.fillMaxSize()
                )
                AttributionStrip(
                    basemap = basemap,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = 4.dp)
                )
            }

            LookTarget.entries.forEach { target ->
                LookCard(
                    target = target,
                    look = look,
                    onColour = { viewModel.setColour(target, it) },
                    onWidth = { viewModel.setWidth(target, it) },
                    onSize = { viewModel.setSize(target, it) }
                )
            }

            // Only while there is something to go back from: a reset that does nothing is a button
            // the walker presses to find out that nothing happens.
            if (!look.isDefault) {
                TextButton(onClick = viewModel::reset) { Text("Back to how it came") }
            }
        }
    }
}

/** One row of the screen: what it is called, how heavy it is drawn, and the colour it is drawn in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookCard(
    target: LookTarget,
    look: MapLook,
    onColour: (LookColour) -> Unit,
    onWidth: (LineWidthChoice) -> Unit,
    onSize: (MarkerSizeChoice) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TargetGlyph(target = target, look = look)
                Text(target.displayName, style = MaterialTheme.typography.titleMedium)
            }

            when (target.control) {
                LookControl.LINE -> ChoiceRow(
                    choices = LineWidthChoice.entries,
                    selected = look.widthOf(target),
                    onChoose = onWidth,
                    text = { it.displayName }
                )

                LookControl.PLACE -> ChoiceRow(
                    choices = MarkerSizeChoice.entries,
                    selected = look.sizeOf(target),
                    onChoose = onSize,
                    text = { it.displayName }
                )
            }

            // The whole shelf on every row, so the same colour can be used twice: a walker who wants
            // their campsites the colour of their tracks has no other way to say so.
            //
            // Four to a row rather than a wrapping flow: eleven swatches come out as a ragged last line
            // either way, and a row of four reads as a shelf however wide the screen is. The last line
            // of three is the new end of it - the lemon, the sky and the trail's rust.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LookColour.entries.chunked(COLOURS_PER_ROW).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { colour ->
                            ColourChip(
                                colour = colour,
                                selected = look.colourOf(target) == colour,
                                onClick = { onColour(colour) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The row's own glyph, in the row's own colour: a hut's roof for huts, a line for a track.
 *
 * The deep tone on a light card and the derived dark tone on a dark one, which is the app's rule for a
 * glyph on a card - a deep purple on charcoal is a glyph nobody sees, and a lemon on white is the same
 * problem the other way round. The map's own drawing is the shelf's colour whatever the theme, because
 * it is drawn over imagery rather than over a card.
 */
@Composable
private fun TargetGlyph(target: LookTarget, look: MapLook) {
    val kind = kindOf(target)
    if (kind != null) {
        // A kind draws the glyph it draws everywhere else, in the colour the look gives it.
        AssetKindIcon(kind = kind, dimension = 24.dp)
        return
    }

    // The two rows that are not a kind: the trail, and the lines other people publish. Each has a mark
    // of its own, because two rows with one glyph is two rows the walker cannot tell apart at a glance.
    val hex = if (isSystemInDarkTheme()) {
        look.colourOnDark(target)
    } else {
        look.colourOnLight(target)
    }
    AppIcon(
        glyph = if (target == LookTarget.TE_ARAROA) IconGlyph.TRAIL else IconGlyph.LINES,
        dimension = 24.dp,
        tint = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
    )
}

/** The kind a row draws, or null for the rows that are not one. */
private fun kindOf(target: LookTarget): AssetKind? = when (target) {
    LookTarget.TRACKS -> AssetKind.TRACK
    LookTarget.BIKE_TRACKS -> AssetKind.BIKE_TRACK
    LookTarget.HUTS -> AssetKind.HUT
    LookTarget.CAMPSITES -> AssetKind.CAMPSITE
    LookTarget.WAYPOINTS -> AssetKind.WAYPOINT
    LookTarget.OTHER_TRACKS -> null
    LookTarget.TE_ARAROA -> null
}
