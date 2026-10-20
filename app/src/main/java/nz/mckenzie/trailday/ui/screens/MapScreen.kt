package nz.mckenzie.trailday.ui.screens

import android.Manifest
import android.graphics.BitmapFactory
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.R
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetLayer
import nz.mckenzie.trailday.domain.asset.AssetPhrase
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.doc.DocPick
import nz.mckenzie.trailday.viewmodel.PendingFollow
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.PlaceLabels
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.theme.TrailDayElevation
import nz.mckenzie.trailday.ui.docBikeLine
import nz.mckenzie.trailday.ui.docCardFacts
import nz.mckenzie.trailday.ui.docCardProse
import nz.mckenzie.trailday.ui.formatLengthOf
import nz.mckenzie.trailday.viewmodel.MapFocus
import nz.mckenzie.trailday.viewmodel.MapViewModel
import nz.mckenzie.trailday.viewmodel.PendingMapFocus
import kotlinx.coroutines.launch

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
    val docBikeBadgesGeoJson by viewModel.docBikeBadgesGeoJson.collectAsStateWithLifecycle()
    val initialFrame by viewModel.initialFrame.collectAsStateWithLifecycle()
    val recentre by viewModel.recentre.collectAsStateWithLifecycle()
    val locationNotice by viewModel.locationNotice.collectAsStateWithLifecycle()
    val hiddenLayers by viewModel.hiddenLayers.collectAsStateWithLifecycle()
    val yourTracksHidden by viewModel.yourTracksHidden.collectAsStateWithLifecycle()
    val yourTracksGeoJson by viewModel.yourTracksGeoJson.collectAsStateWithLifecycle()
    val placeNames by viewModel.placeNames.collectAsStateWithLifecycle()
    val docPick by viewModel.docPick.collectAsStateWithLifecycle()
    val places by viewModel.assets.collectAsStateWithLifecycle()
    val recordedPlaces by viewModel.recordedPlaces.collectAsStateWithLifecycle()

    var layersOpen by remember { mutableStateOf(false) }
    val mapScope = rememberCoroutineScope()

    // What the card at the foot of the map is about: the place the walker last tapped, or the thing DOC
    // publishes that was under their finger. Nothing tapped is no card - the map is the subject.
    var pickedPlaceId by remember { mutableStateOf<Long?>(null) }

    val context = LocalContext.current

    // A track chosen from a list is asked for here, when the map is the screen in front of the walker:
    // the request is taken once, so a later visit to the map opens on the work rather than on the last
    // row somebody tapped in a list.
    LaunchedEffect(Unit) {
        when (val focus = PendingMapFocus.take()) {
            is MapFocus.Place -> {
                pickedPlaceId = focus.assetId
                viewModel.focusPlace(focus.assetId)
            }

            is MapFocus.Doc -> viewModel.focusDoc(focus.item)
            null -> Unit
        }
    }

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
                yourTracksGeoJson = yourTracksGeoJson,
                // DOC's material, under the walker's own work: the tracks, huts, campsites and bike
                // locations kept on the phone for offline use. It is not the walker's to change and has
                // no page of its own - a tap on it is answered by the card at the bottom of the map.
                docGeoJson = docGeoJson,
                docBikeBadgesGeoJson = docBikeBadgesGeoJson,
                // What the walker has switched off. The home map is the one these apply to: a
                // screen that is about one place, or about the spot being drawn, draws it
                // whatever is hidden here.
                hiddenLayers = hiddenLayers,
                yourTracksHidden = yourTracksHidden,
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
                // A tap lands on something: a line of the walker's own or a thing DOC publishes. Either
                // way the card below says what it is, and the card's own tap opens the place. The radius
                // comes from the map's zoom, so the tap works at country scale as well as at paddock
                // height, and the DOC question is worked out away from the drawing, because DOC's cache
                // is far bigger than the walker's own list.
                onMapClick = { lat, lng, radiusM ->
                    val asset = viewModel.assetAt(lat, lng, radiusM)
                    if (asset != null) {
                        viewModel.clearPickedDoc()
                        pickedPlaceId = asset
                    } else {
                        // The map says so itself when DOC has nothing there: the card goes away rather
                        // than growing a nearest-thing answer nobody asked for.
                        pickedPlaceId = null
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
                    description = "Layers",
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

            // The card and the licence line share one column at the foot of the map, so the card sits *above*
            // the line rather than on top of it - whatever the line's height is, with place names and without.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val picked = docPick
                val place = pickedPlaceId?.let { id -> places.firstOrNull { it.id == id } }
                if (picked != null) {
                    // A tapped thing has more to say than a glance: a picture, what DOC says about it, and its
                    // prose - so its card is a little wider and scrolls, rather than pushing the map off the
                    // screen.
                    DocCard(
                        pick = picked,
                        onClose = viewModel::clearPickedDoc,
                        onFollow = {
                            // The recorder takes it from here: the walk starts on the screen that asks about
                            // walking, not on a card over a map.
                            PendingFollow.offer(picked.item)
                            viewModel.clearPickedDoc()
                            onOpenTab(Tab.RECORD)
                        },
                        modifier = Modifier.widthIn(max = 300.dp)
                    )
                } else if (place != null) {
                    PlaceCard(
                        place = place,
                        recorded = place.id in recordedPlaces,
                        onOpen = { onOpenAsset(place.id) },
                        onClose = { pickedPlaceId = null },
                        modifier = Modifier.widthIn(max = 250.dp)
                    )
                }

                AttributionStrip(basemap = basemap, placeNames = placeNames)
            }
        }
    }

    if (layersOpen) {
        LayersSheet(
            hidden = hiddenLayers,
            onHide = viewModel::setLayerHidden,
            onShowAll = viewModel::showEveryLayer,
            yourTracksHidden = yourTracksHidden,
            onYourTracksHidden = viewModel::setYourTracksHidden,
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
 * **The content scrolls.** Opened at its full height (`skipPartiallyExpanded`) *and* scrollable is what
 * makes every switch reachable on a short screen: the height is so that the sheet does not open halfway up
 * with a scroll nobody has been told about, and the scroll is so that a sheet taller than the screen is
 * still a sheet you can read to the end of.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayersSheet(
    hidden: Set<AssetLayer>,
    onHide: (layer: AssetLayer, hide: Boolean) -> Unit,
    onShowAll: () -> Unit,
    yourTracksHidden: Boolean,
    onYourTracksHidden: (hide: Boolean) -> Unit,
    placeNames: Boolean,
    onPlaceNames: (show: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    // The sheet is the page's own pale sage, and each row is a white strip on it: the app's cards on the
    // app's page, the same pairing as every other list.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // The title's row keeps its height whether or not the button is there, and the button is
            // *above* the switches: a link that appeared under them moved the whole list up the moment a
            // layer was switched off, which is the one moment the list must stay still.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Layers",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (hidden.isNotEmpty() || yourTracksHidden) {
                    TextButton(onClick = onShowAll) { Text("Show everything") }
                }
            }
            AssetLayer.ALL.forEach { layer ->
                val shown = layer !in hidden
                LayerSwitchRow(
                    label = layer.displayName,
                    checked = shown,
                    onToggle = { onHide(layer, shown) }
                )
            }
            // The walks and rides the walker has recorded, which are evidence rather than one of the
            // places and so have a switch of their own.
            LayerSwitchRow(
                label = "Your Tracks",
                checked = !yourTracksHidden,
                onToggle = { onYourTracksHidden(!yourTracksHidden) }
            )

            // The place names, which are the one thing here that starts *off*: everything else in this
            // sheet is the work, which is why the walker opened the app.
            LayerSwitchRow(
                label = "Place names",
                checked = placeNames,
                onToggle = { onPlaceNames(!placeNames) }
            )
        }
    }
}

/**
 * One switch in the layers sheet: its name, and the switch.
 *
 * **The whole row switches**, not only the switch: this is a screen used with gloves on, and the switch is a
 * small target. No second line: a layer called Huts does not need telling what a hut is.
 */
@Composable
private fun LayerSwitchRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // A white strip on the sheet's pale sage, as every row in this app is a card on the page.
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
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
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = TrailDayElevation.floating),
        modifier = modifier.fillMaxWidth()
    ) {
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
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = TrailDayElevation.floating),
        modifier = modifier.fillMaxWidth()
    ) {
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
 * A card floating over the map: the app's corner, a shadow that lifts it off the imagery, and a small close
 * in its top-right corner when the card can be put away.
 *
 * **One height for every card on the map** - a thing that is nearest, a thing DOC publishes, a word about the
 * phone's location - because a map is imagery and imagery is as pale as snow or as dark as water: a card with
 * no shadow melts into it. See [TrailDayElevation].
 *
 * The close sits on a white circle of its own over the card's corner, because a card that opens with a
 * photograph would otherwise hide a plain cross behind the picture.
 */
@Composable
private fun MapCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    /** The cross's ink: the card's own quiet grey, or white where the cross sits over a picture. */
    closeTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        val elevation = CardDefaults.cardElevation(defaultElevation = TrailDayElevation.floating)
        if (onClick != null) {
            Card(onClick = onClick, elevation = elevation, modifier = Modifier.fillMaxWidth()) { content() }
        } else {
            Card(elevation = elevation, modifier = Modifier.fillMaxWidth()) { content() }
        }

        if (onClose != null) {
            // Just a cross: no circle, no shadow. The target is still 40 points, which is the thumb's own
            // size, and the description is on the click so the glyph says nothing twice.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(40.dp)
                    .clickable(onClickLabel = "Close the card", onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(glyph = IconGlyph.CLOSE, dimension = 19.dp, tint = closeTint)
            }
        }
    }
}

/**
 * One of the walker's own places, as they tapped it on the map: what it is, its name, and how long it is.
 *
 * **A line a walk has been made of says so.** "RECORDED TRACK" rather than "TRACK", because that is the
 * difference between a line that was drawn and a line that was actually walked - the same difference the
 * map's green and gold make. The card is a glance; the whole of it is a way into the place's own page,
 * which has the figures, the heights and the trips.
 */
@Composable
private fun PlaceCard(
    place: AssetEntity,
    recorded: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val kind = AssetKind.fromStorage(place.kind)
    val what = AssetPhrase.kind(kind).uppercase()
    val length = formatLengthOf(AssetShape.fromStorage(place.shape), place.lengthM)

    MapCard(modifier = modifier, onClick = onOpen, onClose = onClose) {
        Column(
            // Room at the end for the close in the corner, so a long name does not run under it.
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 40.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = if (recorded) "RECORDED $what" else what,
                style = MaterialTheme.typography.labelSmall,
                // Spaced out, as the mock screens write that line: it is a label, not a sentence.
                letterSpacing = 1.2.sp
            )
            Text(text = place.name, style = MaterialTheme.typography.titleLarge)
            if (length.isNotEmpty()) {
                Text(text = length, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * What DOC publishes, as the walker tapped it: the kind of thing, its name, DOC's own facts about it, and how
 * far the phone is from it now.
 *
 * **A card and no more.** This material is not the walker's to change and has no page of its own; the header
 * says whose it is, and nothing else on the card needs to. The facts are the phone's own words for the row -
 * the same line the browser puts under a name - so the two surfaces cannot drift. Tapping the map anywhere
 * else puts the card away.
 */
@Composable
private fun DocCard(
    pick: DocPick,
    onClose: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = pick.item
    val facts = remember(item) { docCardFacts(item) }
    val prose = remember(item) { docCardProse(item) }
    // Decoded here rather than in the view model: it is a thumbnail from DOC, and what the card needs to
    // draw is a picture. A picture the phone cannot read is a card without one, not a card that fails.
    val picture = remember(pick.picture) {
        pick.picture?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }
    val away = pick.distanceM?.let { formatDistance(it) + " away" }

    MapCard(
        modifier = modifier,
        onClose = onClose,
        // The cross is over the picture when there is one, so it is drawn in white there.
        closeTint = if (picture != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            picture?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(130.dp)
                )
            }
            Column(
                // Room at the end for the close in the corner, which sits over the picture when there is one.
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 40.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "DOC ${item.source.singular}".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.2.sp
                )
                Text(text = item.name, style = MaterialTheme.typography.titleLarge)
                facts.forEach { fact ->
                    Text(text = fact, style = MaterialTheme.typography.bodySmall)
                }
                pick.bike?.let { bike ->
                    Text(
                        text = docBikeLine(bike),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                away?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
                // The one thing to do with a line of DOC's: go and walk it. A hut or a campsite is a
                // place to reach rather than a line to follow, so its card says what it is and stops.
                if (item.source.isLine) {
                    Button(
                        onClick = onFollow,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) {
                        Text("Follow this")
                    }
                }
                prose?.let {
                    HorizontalDivider(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }

            }
        }
    }
}