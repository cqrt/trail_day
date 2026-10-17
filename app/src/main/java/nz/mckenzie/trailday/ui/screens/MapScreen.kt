package nz.mckenzie.trailday.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.R
import nz.mckenzie.trailday.domain.asset.AssetLayer
import nz.mckenzie.trailday.domain.nearby.DocPick
import nz.mckenzie.trailday.domain.nearby.Nearby
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.PlaceLabels
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.docRowDetail
import nz.mckenzie.trailday.ui.formatEstimate
import nz.mckenzie.trailday.viewmodel.MapViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onOpenTab: (Tab) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAsset: (Long) -> Unit = {}
) {
    val apiKey by viewModel.linzApiKey.collectAsStateWithLifecycle()
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
    val geoJson by viewModel.assetGeoJson.collectAsStateWithLifecycle()
    val docGeoJson by viewModel.docGeoJson.collectAsStateWithLifecycle()
    val initialFrame by viewModel.initialFrame.collectAsStateWithLifecycle()
    val recentre by viewModel.recentre.collectAsStateWithLifecycle()
    val locationNotice by viewModel.locationNotice.collectAsStateWithLifecycle()
    val hiddenLayers by viewModel.hiddenLayers.collectAsStateWithLifecycle()
    val placeNames by viewModel.placeNames.collectAsStateWithLifecycle()
    val nearby by viewModel.nearby.collectAsStateWithLifecycle()
    val docPick by viewModel.docPick.collectAsStateWithLifecycle()

    var layersOpen by remember { mutableStateOf(false) }
    val mapScope = rememberCoroutineScope()

    val context = LocalContext.current

    // Where the app asks for the permission the marker needs, and asked on a tap rather than on
    // the way in: a prompt that arrives with the map is a prompt for nothing, and one that
    // arrived from the record screen already has an answer.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.recentreOnDevice() else viewModel.onLocationRefused()
    }

    val locate = {
        val allowed =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (allowed) {
            viewModel.recentreOnDevice()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        // No bar over the map: the mock screens give the map the whole screen, and the three things that
        // used to sit in a bar - what the map draws, the settings, and where I am - are round buttons over
        // the map's top corner instead. The title has no job on a screen that is a place.
        bottomBar = { TrailDayNavBar(current = Tab.MAP, onSelect = onOpenTab) }
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
                // DOC's material, under the walker's own work: the tracks, huts, campsites and bike
                // locations kept on the phone for offline use. It is not the walker's to change and has
                // no page of its own - a tap on it is answered by the card at the bottom of the map.
                docGeoJson = docGeoJson,
                // What the walker has switched off. The home map is the one these apply to: a
                // screen that is about one place, or about the spot being drawn, draws it
                // whatever is hidden here.
                hiddenLayers = hiddenLayers,
                // The place names, which the walker asks for in the same sheet: this is the map
                // they read the map on, and "which town is that" is asked here rather than on a
                // screen that is about one place.
                placeNames = placeNames,
                // The marker for the phone is the map's own business now, so this screen
                // says nothing about it: it draws where you are wherever a map is drawn.
                // The walker's own tracks first; failing that, where the device is;
                // failing that, the neutral country-wide default.
                fitBounds = initialFrame,
                // Where "show me" last asked the camera to be. Null until they ask: the map
                // does not follow the phone, because it is also for reading the work.
                recentreBounds = recentre?.bounds,
                recentreCount = recentre?.count ?: 0,
                // Tapping a track opens it: the map is where they are looking when they
                // wonder about a block. The radius comes from the map's zoom, so the tap
                // works at country scale as well as at paddock height.
                //
                // A tap that lands on nothing of the walker's but on something DOC publishes
                // asks the card below about it, and one that lands on nothing at all puts what
                // is nearest back. The question is worked out away from the drawing, because
                // DOC's cache is far bigger than the walker's own list.
                onMapClick = { lat, lng, radiusM ->
                    val asset = viewModel.assetAt(lat, lng, radiusM)
                    if (asset != null) {
                        viewModel.clearPickedDoc()
                        onOpenAsset(asset)
                    } else {
                        mapScope.launch { viewModel.pickDocAt(lat, lng, radiusM) }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                locationNotice?.let { notice ->
                    LocationNoticeCard(
                        message = notice,
                        onDismiss = viewModel::clearLocationNotice
                    )
                }
            }

            // Where the app asks for the permission the marker needs, and asked on a tap: a
            // prompt that arrives with the map is a prompt for nothing, and one that arrived
            // from the record screen has an answer already.
            //
            // The three buttons over the map's top corner, as the mock screens draw them: white circles
            // with a shadow, settings above what-the-map-draws above where-I-am. The mock screens have the
            // last two; the settings need a door of their own once the bar is gone, and this is the corner
            // the walker's thumb is already going to.
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RoundIconButton(glyph = IconGlyph.SETTINGS, description = "Settings", onClick = onOpenSettings)
                RoundIconButton(
                    glyph = IconGlyph.LAYERS,
                    description = "What the map draws",
                    onClick = { layersOpen = true }
                )
                RoundIconButton(glyph = IconGlyph.LOCATE, description = "Show where I am", onClick = locate)
            }

            // Only when the chosen basemap needs a key and none is set. OpenStreetMap needs none,
            // which is the whole reason it is in the app: the day a LINZ key expires, the map is
            // still a map.
            if (basemap.needsKey && apiKey.isBlank()) {
                MissingKeyCard(
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }

            AttributionStrip(
                basemap = basemap,
                placeNames = placeNames,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 6.dp)
            )

            // What is nearest, at the bottom of the map where the mock screens put it: the quickest answer
            // there is to "am I where I think I am". Above the attribution line, and clear of the locate
            // button so neither is in the other's way.
            // The card's subject: the thing DOC publishes that was tapped, or failing that what is nearest.
            val cardModifier = Modifier
                .align(Alignment.BottomStart)
                // The mock screens' card does not cross the whole screen: the map is the subject and the card
                // sits in its corner. Clear of the licence line below, which is not the app's to hide.
                .padding(start = 12.dp, bottom = 44.dp)
                .widthIn(max = 250.dp)

            val picked = docPick
            if (picked != null) {
                DocCard(pick = picked, modifier = cardModifier)
            } else {
                nearby?.let { card ->
                    NearbyCard(nearby = card, onOpenAsset = onOpenAsset, modifier = cardModifier)
                }
            }
        }
    }

    if (layersOpen) {
        LayersSheet(
            hidden = hiddenLayers,
            onHide = viewModel::setLayerHidden,
            onShowAll = viewModel::showEveryLayer,
            placeNames = placeNames,
            onPlaceNames = viewModel::setPlaceNames,
            onDismiss = { layersOpen = false }
        )
    }
}

/**
 * What the map draws, and the switches for it.
 *
 * A sheet rather than a settings screen, because the question "why is that not on the map" is
 * asked while looking at the map, and is answered by switches in front of you. It also says what
 * it does not do: the drawing screen still draws what is being drawn, and a place's own page
 * still draws that place, whatever is switched off here - a switch that left one of those blank
 * would have gone too far.
 *
 * **The content scrolls, and it has to.** Ten layers with a line under each is already taller than
 * a phone's screen minus the bars, so the last row was being cut off at the bottom of the sheet -
 * and when the place names were added below them, that cut-off row was the one the walker was
 * looking for. Opened at its full height (`skipPartiallyExpanded`) *and* scrollable is what makes
 * every switch reachable: the height is so that the sheet does not open halfway up the screen with
 * a scroll nobody has been told about, and the scroll is so that a sheet taller than the screen is
 * still a sheet you can read to the end of.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayersSheet(
    hidden: Set<AssetLayer>,
    onHide: (layer: AssetLayer, hide: Boolean) -> Unit,
    onShowAll: () -> Unit,
    placeNames: Boolean,
    onPlaceNames: (show: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("What the map draws", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Switch off what you do not need to see. The map remembers, and it is " +
                    "only this map: a spot being drawn, or a place's own page, still draws it.",
                style = MaterialTheme.typography.bodySmall
            )
            AssetLayer.ALL.forEach { layer ->
                val shown = layer !in hidden
                ListItem(
                    // The whole row switches, rather than only the switch: this is a screen used
                    // with gloves on, and a switch is a small target. Tapping a layer that is on
                    // turns it off, which is what "hide" means here.
                    modifier = Modifier.clickable { onHide(layer, shown) },
                    headlineContent = { Text(layer.displayName) },
                    supportingContent = { Text(layer.summary) },
                    trailingContent = {
                        Switch(checked = shown, onCheckedChange = { checked -> onHide(layer, !checked) })
                    }
                )
            }
            if (hidden.isNotEmpty()) {
                TextButton(onClick = onShowAll) { Text("Show everything") }
            }

            // The place names, which are the one thing here that starts *off*: everything else in
            // this sheet is the work, which is why the walker opened the app. A town name is
            // furniture over their own ground, so it is asked for rather than arrived - and the row
            // says where it comes from, because it is the one switch here that fetches anything new.
            HorizontalDivider()
            ListItem(
                modifier = Modifier.clickable { onPlaceNames(!placeNames) },
                headlineContent = { Text("Place names") },
                supportingContent = {
                    Text("Towns, lakes, peaks, bays and rivers from LINZ, drawn over the imagery.")
                },
                trailingContent = {
                    Switch(
                        checked = placeNames,
                        onCheckedChange = { checked -> onPlaceNames(checked) }
                    )
                }
            )
        }
    }
}

/**
 * What to say when the walker has asked where they are and the app cannot answer.
 *
 * A card rather than a toast, because the reason is worth reading and acting on - location is
 * switched off for the app, or off altogether - and it stays until it has been read.
 */
@Composable
private fun LocationNoticeCard(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = message, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun MissingKeyCard(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.linz_key_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.linz_key_missing),
                style = MaterialTheme.typography.bodySmall
            )
            // The one place where the missing key is the walker's problem, so the fix
            // is a tap away rather than buried in another screen.
            TextButton(onClick = onOpenSettings) { Text("Open settings") }
        }
    }
}

/**
 * The credit the basemap's licence requires, visible on the map rather than behind a tap.
 *
 * Both licences say the same thing in different words: LINZ requires attribution to be shown,
 * and OpenStreetMap's guidelines add that it must not be hidden "behind toggles, or off-screen".
 * So this strip is drawn by every screen that draws a map, names the basemap actually in use, and
 * links to that basemap's own credit page - which is what makes it satisfy the licence rather than
 * merely mention it.
 *
 * [placeNames] adds the second credit where a second set of LINZ data is being drawn: the imagery's
 * own credit already names the publisher and the licence, so what it adds is the part that is new -
 * that topographic data is on the map as well.
 */
@Composable
internal fun AttributionStrip(
    basemap: Basemap,
    modifier: Modifier = Modifier,
    placeNames: Boolean = false
) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = if (placeNames) {
            "${basemap.attribution} \u00b7 ${PlaceLabels.SHORT_ATTRIBUTION}"
        } else {
            basemap.attribution
        },
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .background(Color(0x99000000), RoundedCornerShape(4.dp))
            .clickable { uriHandler.openUri(basemap.attributionLink) }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}


/**
 * What is nearest, and what else is around: the map's own answer to "am I where I think I am".
 *
 * The shape is the mock screens': what kind of thing it is, its name, how far and how long on foot, then a
 * rule and a count of what else is inside a few kilometres. A place of the walker's opens when it is
 * tapped; a thing DOC publishes has no page to open yet, so its card is a card and no more.
 */
@Composable
private fun NearbyCard(
    nearby: Nearby,
    onOpenAsset: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val nearest = nearby.nearest
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Nearest ${nearest.what}".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                // Spaced out, as the mock screens write that line: it is a label, not a sentence.
                letterSpacing = 1.2.sp
            )
            Text(text = nearest.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = listOfNotNull(
                    formatDistance(nearest.distanceM),
                    "about ${formatEstimate(nearest.onFootSeconds())} on foot",
                    nearest.place
                ).joinToString(" \u00b7 "),
                style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Text(
                text = nearby.counted.joinToString(" \u00b7 ") +
                    " within ${(nearby.radiusM / 1000.0).roundToInt()} km",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (nearest.assetId != null) {
        Card(onClick = { onOpenAsset(nearest.assetId) }, modifier = modifier) { content() }
    } else {
        Card(modifier = modifier) { content() }
    }
}

/**
 * What DOC publishes, as the walker tapped it: the kind of thing, its name, DOC's own facts about it, and how
 * far the phone is from it now.
 *
 * **A card and no more, deliberately.** This material is not the walker's to change and has no page of its
 * own, so the card says so rather than looking like a door that opens onto nothing. The facts are the phone's
 * own words for the row - the same line the browser puts under a name - so the two surfaces cannot drift.
 * Tapping the map anywhere else puts what is nearest back.
 */
@Composable
private fun DocCard(pick: DocPick, modifier: Modifier = Modifier) {
    val item = pick.item
    val facts = docRowDetail(item, distanceM = null)
    val away = listOfNotNull(
        pick.distanceM?.let { formatDistance(it) + " away" },
        pick.onFootSeconds()?.let { "about ${formatEstimate(it)} on foot" }
    )

    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "DOC ${item.source.singular}".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.2.sp
            )
            Text(text = item.name, style = MaterialTheme.typography.titleLarge)
            if (facts.isNotBlank()) {
                Text(text = facts, style = MaterialTheme.typography.bodySmall)
            }
            if (away.isNotEmpty()) {
                Text(text = away.joinToString(" \u00b7 "), style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Text(
                text = "From DOC - not one of your places",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}