package nz.mckenzie.trailday.ui.screens

import android.Manifest
import android.graphics.BitmapFactory
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import nz.mckenzie.trailday.domain.asset.YourPlaceLayer
import nz.mckenzie.trailday.domain.doc.DocAlertsState
import nz.mckenzie.trailday.domain.doc.DocBikeLinks
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocPick
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.look.Look
import nz.mckenzie.trailday.domain.look.LookTarget
import nz.mckenzie.trailday.viewmodel.PendingFollow
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.PlaceLabels
import nz.mckenzie.trailday.domain.tiles.TeAraroa
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.map.DEFAULT_CAMERA_TARGET
import nz.mckenzie.trailday.map.DEFAULT_CAMERA_ZOOM
import nz.mckenzie.trailday.map.KindTileLook
import nz.mckenzie.trailday.map.OtherTracksLayerIds
import nz.mckenzie.trailday.map.ScaleBar
import nz.mckenzie.trailday.map.ScaleBarSpan
import org.maplibre.android.geometry.LatLng
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.theme.TrailDayElevation
import nz.mckenzie.trailday.ui.docBikeFacts
import nz.mckenzie.trailday.ui.docCardFacts
import nz.mckenzie.trailday.ui.formatDate
import nz.mckenzie.trailday.tracking.LocationPermission
import nz.mckenzie.trailday.ui.docCardProse
import nz.mckenzie.trailday.ui.formatLengthOf
import nz.mckenzie.trailday.viewmodel.MapFocus
import nz.mckenzie.trailday.viewmodel.MapViewModel
import nz.mckenzie.trailday.viewmodel.PendingMapFocus
import nz.mckenzie.trailday.viewmodel.YourPlaceCounts
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onOpenTab: (Tab) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAsset: (Long) -> Unit = {},
    onOpenDocDetail: (DocItem, DocItem?) -> Unit = { _, _ -> },
    onOpenOtherTrack: (OtherTrack) -> Unit = {}
) {
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
    val basemapTrouble by viewModel.basemapTrouble.collectAsStateWithLifecycle()
    val camera by viewModel.camera.collectAsStateWithLifecycle()
    val geoJson by viewModel.assetGeoJson.collectAsStateWithLifecycle()
    val docGeoJson by viewModel.docGeoJson.collectAsStateWithLifecycle()
    val teAraroa by viewModel.teAraroa.collectAsStateWithLifecycle()
    val teAraroaGeoJson by viewModel.teAraroaGeoJson.collectAsStateWithLifecycle()
    val otherTracks by viewModel.otherTracks.collectAsStateWithLifecycle()
    val otherTracksGeoJson by viewModel.otherTracksGeoJson.collectAsStateWithLifecycle()
    val otherTracksDownloadedAt by viewModel.otherTracksDownloadedAt.collectAsStateWithLifecycle()
    val docBikeBadgesGeoJson by viewModel.docBikeBadgesGeoJson.collectAsStateWithLifecycle()
    val initialFrame by viewModel.initialFrame.collectAsStateWithLifecycle()
    val selectionFrame by viewModel.selectionFrame.collectAsStateWithLifecycle()
    val pickedPlaceId by viewModel.pickedPlaceId.collectAsStateWithLifecycle()
    val recentre by viewModel.recentre.collectAsStateWithLifecycle()
    val locationNotice by viewModel.locationNotice.collectAsStateWithLifecycle()
    val hiddenLayers by viewModel.hiddenLayers.collectAsStateWithLifecycle()
    val hiddenYourPlaces by viewModel.hiddenYourPlaces.collectAsStateWithLifecycle()
    val yourPlaceCounts by viewModel.yourPlaceCounts.collectAsStateWithLifecycle()
    val yourTracksGeoJson by viewModel.yourTracksGeoJson.collectAsStateWithLifecycle()
    val placeNames by viewModel.placeNames.collectAsStateWithLifecycle()
    val docPick by viewModel.docPick.collectAsStateWithLifecycle()
    val docAlerts by viewModel.pickedDocAlerts.collectAsStateWithLifecycle()
    val places by viewModel.assets.collectAsStateWithLifecycle()
    val recordedPlaces by viewModel.recordedPlaces.collectAsStateWithLifecycle()

    var layersOpen by remember { mutableStateOf(false) }
    val mapScope = rememberCoroutineScope()

    // How far across the ground one pixel of the map covers, as the map reports it on every camera
    // move, and the room the bar is allowed to take. Null until the map has said, which is a moment
    // after it opens: the bar is drawn only from an answer the map itself gave.
    var metresPerPixel by remember { mutableStateOf<Double?>(null) }
    val scaleWidthPx = with(LocalDensity.current) { ScaleBar.MAX_WIDTH_DP.dp.toPx() }

    // The zoom keys under the map's own controls, and the level between them. The level is the map's own
    // answer, so it is filled in by the camera-idle callback below rather than kept here.
    val zoom = rememberMapZoomState()

    val context = LocalContext.current

    // A track chosen from a list is asked for here, when the map is the screen in front of the walker:
    // the request is taken once, so a later visit to the map opens on the work rather than on the last
    // row somebody tapped in a list.
    LaunchedEffect(Unit) {
        when (val focus = PendingMapFocus.take()) {
            is MapFocus.Place -> viewModel.focusPlace(focus.assetId)

            is MapFocus.Doc -> viewModel.focusDoc(focus.item, focus.bike)
            null -> Unit
        }
    }

    // Where the app asks for the permission the marker needs, and asked on a tap rather than on
    // the way in: a prompt that arrives with the map is a prompt for nothing, and one that
    // arrived from the record screen already has an answer.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        // A granted "Approximate" arrives here as a refusal, and it is not the same refusal: the fix
        // exists, it is just too rough to draw a dot from, and only the walker can change that.
        if (granted) {
            viewModel.recentreOnDevice()
        } else {
            viewModel.onLocationRefused(LocationPermission.refusal(context))
        }
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
                hiddenYourPlaces = hiddenYourPlaces,
                // The place names, which the walker asks for in the same sheet: this is the map
                // they read the map on, and "which town is that" is asked here rather than on a
                // screen that is about one place.
                placeNames = placeNames,
                // The trail, under everything including DOC's material: a national route is the ground
                // the rest of the map is read against, and it is the app's own feature rather than
                // anything the walker has to find and import.
                showTeAraroa = teAraroa,
                teAraroaGeoJson = teAraroaGeoJson,
                // Somebody else's tracks: over the trail, and under DOC's material and the walker's own
                // work. A second publisher's reference rather than the walker's, so it is drawn where the
                // reference belongs - and it is empty until an area has been downloaded.
                showOtherTracks = otherTracks,
                otherTracksGeoJson = otherTracksGeoJson,
                // The marker for the phone is the map's own business now, so this screen
                // says nothing about it: it draws where you are wherever a map is drawn.
                // The walker's own tracks first; failing that, where the device is;
                // failing that, the neutral country-wide default.
                // A card that is still up owns the frame when the map is rebuilt by a tab change;
                // otherwise this is the first look at the walker's work.
                fitBounds = selectionFrame ?: initialFrame,
                // Where "show me" last asked the camera to be. Null until they ask: the map
                // does not follow the phone, because it is also for reading the work.
                recentreBounds = recentre?.bounds,
                recentreCount = recentre?.count ?: 0,
                // The move has been made, so the request is let go: one that stayed would be made again
                // every time this screen was rebuilt, which a tab change does.
                onRecentreHandled = viewModel::consumeRecentre,
                // The ground the walker was last reading, so a tab change or a basemap change
                // opens here rather than on the work or the phone.
                initialTarget = camera?.let { LatLng(it.lat, it.lng) } ?: DEFAULT_CAMERA_TARGET,
                initialZoom = camera?.zoom ?: DEFAULT_CAMERA_ZOOM,
                restoreCamera = camera != null,
                onCameraIdle = { lat, lng, level ->
                    viewModel.rememberCamera(lat, lng, level)
                    // The same callback carries the level the keys show: on this map the walker can also
                    // pinch, so the number is where the camera ended up rather than where a key put it.
                    zoom.reported(level)
                },
                // How far a pixel of this map covers, for the scale bar at the map's foot. The map
                // measures it, being the only thing that knows its own projection; this screen draws
                // it, because the corner it goes in is this screen's furniture.
                onMetresPerPixel = { metresPerPixel = it },
                // The zoom keys, which on this map are the second way to do what a pinch does - the first
                // being the pinch itself. **The step and the count go to the map**: the keys only ask.
                zoomStep = zoom.step,
                zoomCount = zoom.count,
                // A tap lands on something: a line of the walker's own or a thing DOC publishes. Either
                // way the card below says what it is, and the card's own tap opens the place. The radius
                // comes from the map's zoom, so the tap works at country scale as well as at paddock
                // height, and the DOC question is worked out away from the drawing, because DOC's cache
                // is far bigger than the walker's own list.
                onMapClick = { lat, lng, radiusM ->
                    val asset = viewModel.assetAt(lat, lng, radiusM)
                    if (asset != null) {
                        viewModel.pickPlace(asset)
                    } else {
                        // The map says so itself when DOC has nothing there: the card goes away rather
                        // than growing a nearest-thing answer nobody asked for.
                        viewModel.clearPickedPlace()
                        mapScope.launch {
                            // The page this opens is the card at full size, so the map's own card is not
                            // raised for it: coming back from the page should be coming back to the map,
                            // not to the same thing twice.
                            val picked = viewModel.pickDocAt(lat, lng, radiusM, raiseCard = false)
                            if (picked != null) {
                                onOpenDocDetail(picked, viewModel.bikeFor(picked))
                            } else {
                                // **DOC's material answers first when both are under the finger**, which is
                                // deliberate rather than an accident of ordering: a council track running
                                // along a DOC one is a tap on the DOC one, and that is what this map has
                                // always answered. Somebody else's tracks are what a tap reaches where DOC
                                // has nothing.
                                viewModel.pickOtherTrackAt(lat, lng, radiusM)?.let(onOpenOtherTrack)
                            }
                        }
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

            // The zoom keys and the level, **halfway down the right-hand edge** - where they are on every
            // preview map, and where a walker who has met them once will look for them again. Not in the
            // corner with the buttons above: those are what the map is set up with, and this is what the
            // map is *read* with, so it belongs beside the ground rather than in a stack of settings.
            MapZoomButtons(
                zoom = zoom,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = KEY_EDGE_PADDING)
            )

            // What to say when the imagery has stopped arriving, with the way out of it. Only ever shown
            // after a run of tiles has failed, and only for a basemap that has a drawn alternative - see
            // [TileTrouble] and [nz.mckenzie.trailday.offline.ProxyHealthCheck].
            basemapTrouble?.let { trouble ->
                BasemapTroubleCard(
                    message = trouble,
                    // Nothing better to offer a walker who is already on a drawn map: this notice is about
                    // LINZ's tiles, and LINZ's tiles are not what they are drawing.
                    onUseDrawnMap = if (basemap == Basemap.OPENSTREETMAP) {
                        null
                    } else {
                        { viewModel.useBasemap(Basemap.OPENSTREETMAP) }
                    },
                    onDismiss = viewModel::answerTrouble,
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
                        alerts = docAlerts,
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
                        onClose = viewModel::clearPickedPlace,
                        modifier = Modifier.widthIn(max = 250.dp)
                    )
                }

                // The scale bar, above the credit line: the strip owns the very corner, so a bar
                // drawn into it would be two plates on top of each other. Nothing is drawn until the
                // map has said how far a pixel covers.
                metresPerPixel?.let { perPixel ->
                    ScaleBar.span(metresAcross = perPixel * scaleWidthPx, maxWidthPx = scaleWidthPx)
                        ?.let { span -> MapScaleBar(span = span) }
                }

                AttributionStrip(basemap = basemap, teAraroa = teAraroa)
            }
        }
    }

    if (layersOpen) {
        LayersSheet(
            hidden = hiddenLayers,
            onHide = viewModel::setLayerHidden,
            onShowAll = viewModel::showEveryLayer,
            hiddenYourPlaces = hiddenYourPlaces,
            onYourPlaceHidden = viewModel::setYourPlaceHidden,
            yourPlaceCounts = yourPlaceCounts,
            placeNames = placeNames,
            onPlaceNames = viewModel::setPlaceNames,
            teAraroa = teAraroa,
            onTeAraroa = viewModel::setTeAraroa,
            otherTracks = otherTracks,
            onOtherTracks = viewModel::setOtherTracks,
            otherTracksDownloadedAt = otherTracksDownloadedAt,
            onDismiss = { layersOpen = false }
        )
    }
}

/**
 * What the map draws, and the switches for it: **one page, eleven switches.**
 *
 * A sheet rather than a settings screen, because the question "why is that not on the map" is
 * asked while looking at the map, and is answered by switches in front of you. It also says what
 * it does not do: the drawing screen still draws what is being drawn, and a place's own page
 * still draws that place, whatever is switched off here - a switch that left one of those blank
 * would have gone too far.
 *
 * **One page, against the three tabs it had.** Three pages were how a thousand dp of rows fitted the
 * eight hundred a phone gives the sheet, and the cost was that two of them were never seen: the
 * question this sheet answers is *whose line is that*, and a tab is a page you have to find before you
 * can ask it. The count beside each name in place of a second line made a row 49 dp, the whole list
 * 770 dp, and the DOC rows now say DOC - which is the whole of what the third tab was saying.
 *
 * **A second line stays where it names somebody.** "The whole route, Te Araroa Trust" and "From
 * councils, trusts and clubs" are a publisher's credit rather than a description of a switch, and a
 * licence is not the count's to replace.
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
    hiddenYourPlaces: Set<YourPlaceLayer>,
    onYourPlaceHidden: (layer: YourPlaceLayer, hide: Boolean) -> Unit,
    yourPlaceCounts: YourPlaceCounts,
    placeNames: Boolean,
    onPlaceNames: (show: Boolean) -> Unit,
    teAraroa: Boolean,
    onTeAraroa: (show: Boolean) -> Unit,
    otherTracks: Boolean,
    onOtherTracks: (show: Boolean) -> Unit,
    /** When the tracks other people publish were last downloaded, for the credit the licence asks for. */
    otherTracksDownloadedAt: Long?,
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
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The title's row keeps its height whether or not the button is there, and the button is
            // *above* the switches: a link that appeared under them moved the whole list up the moment a
            // layer was switched off, which is the one moment the list must stay still.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Layers",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (hidden.isNotEmpty() || hiddenYourPlaces.isNotEmpty()) {
                    TextButton(onClick = onShowAll) { Text("Show everything") }
                }
            }

            LayerSection("Your Places")
            LayerPanel {
                LayerSwitchRow(
                    icon = { KindTile(kind = AssetKind.WAYPOINT, size = 34.dp) },
                    title = "Spots",
                    count = "${yourPlaceCounts.spots} saved",
                    checked = YourPlaceLayer.SPOTS !in hiddenYourPlaces,
                    onToggle = { onYourPlaceHidden(YourPlaceLayer.SPOTS, !it) }
                )
                LayerDivider()
                LayerSwitchRow(
                    icon = { KindTile(kind = AssetKind.TRACK, size = 34.dp) },
                    title = "Tracks",
                    count = "${yourPlaceCounts.tracks} saved",
                    checked = YourPlaceLayer.TRACKS !in hiddenYourPlaces,
                    onToggle = { onYourPlaceHidden(YourPlaceLayer.TRACKS, !it) }
                )
                LayerDivider()
                LayerSwitchRow(
                    icon = { KindTile(kind = AssetKind.BIKE_TRACK, size = 34.dp) },
                    title = "Bike tracks",
                    count = "${yourPlaceCounts.bikeTracks} saved",
                    checked = YourPlaceLayer.BIKE_TRACKS !in hiddenYourPlaces,
                    onToggle = { onYourPlaceHidden(YourPlaceLayer.BIKE_TRACKS, !it) }
                )
                LayerDivider()
                LayerSwitchRow(
                    icon = { RecordedTile() },
                    title = "Recordings",
                    count = "${yourPlaceCounts.recordings} standalone",
                    checked = YourPlaceLayer.RECORDINGS !in hiddenYourPlaces,
                    onToggle = { onYourPlaceHidden(YourPlaceLayer.RECORDINGS, !it) }
                )
            }

            LayerSection("Map")
            LayerPanel {
                LayerSwitchRow(
                    icon = {
                        GlyphTile(
                            glyph = IconGlyph.MAP,
                            background = MaterialTheme.colorScheme.tertiaryContainer,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    },
                    title = "Place names",
                    checked = placeNames,
                    onToggle = onPlaceNames
                )
                LayerDivider()
                LayerSwitchRow(
                    icon = { TargetTile(target = LookTarget.TE_ARAROA, glyph = IconGlyph.TRAIL) },
                    title = "Te Araroa trail",
                    // The Trust's own route, named with the Trust: the layer is drawn under a
                    // licence that asks for exactly this.
                    subtitle = "The whole route, Te Araroa Trust",
                    checked = teAraroa,
                    onToggle = onTeAraroa
                )
            }

            LayerSection("DOC")
            LayerPanel {
                LayerSwitchRow(
                    icon = { KindTile(kind = AssetKind.TRACK, size = 34.dp) },
                    title = "DOC tracks",
                    checked = AssetLayer.TRACKS !in hidden,
                    onToggle = { onHide(AssetLayer.TRACKS, !it) }
                )
                LayerDivider()
                LayerSwitchRow(
                    icon = { KindTile(kind = AssetKind.BIKE_TRACK, size = 34.dp) },
                    title = "DOC bike trails",
                    checked = AssetLayer.BIKE_TRACKS !in hidden,
                    onToggle = { onHide(AssetLayer.BIKE_TRACKS, !it) }
                )
                LayerDivider()
                LayerSwitchRow(
                    icon = { KindTile(kind = AssetKind.HUT, size = 34.dp) },
                    title = "DOC huts",
                    checked = AssetLayer.HUTS !in hidden,
                    onToggle = { onHide(AssetLayer.HUTS, !it) }
                )
                LayerDivider()
                LayerSwitchRow(
                    icon = { KindTile(kind = AssetKind.CAMPSITE, size = 34.dp) },
                    title = "DOC campsites",
                    checked = AssetLayer.CAMPSITES !in hidden,
                    onToggle = { onHide(AssetLayer.CAMPSITES, !it) }
                )
            }

            LayerSection("Other tracks")
            LayerPanel {
                LayerSwitchRow(
                    icon = { TargetTile(target = LookTarget.OTHER_TRACKS, glyph = IconGlyph.LINES) },
                    title = "Other tracks",
                    // Who publishes them, in the walker's own terms: the dataset is councils,
                    // regional councils, trail trusts and a couple of clubs, and "authority" or
                    // "supplier" is nobody's word for any of that.
                    subtitle = "From councils, trusts and clubs",
                    checked = otherTracks,
                    onToggle = onOtherTracks
                )
            }
            // The publisher's licence, under the switch it belongs to - which is where a walker is when
            // they wonder whose tracks these are.
            OtherTracksCredit(downloadedAtEpochMs = otherTracksDownloadedAt)
        }
    }
}

/** A small capital heading over a group of rows. */
@Composable
private fun LayerSection(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, start = 2.dp)
    )
}

/** A rounded white list of rows, the same card-on-sage pairing as the rest of the app. */
@Composable
private fun LayerPanel(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        )
    ) {
        Column { content() }
    }
}

/** The hairline between two rows, aligned under the text rather than the icon. */
@Composable
private fun LayerDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    )
}

/**
 * One row of the layers sheet: a coloured icon, its name, how many of them there are, and its switch.
 *
 * **The count sits beside the name, not under it**, which is what took a row from 54 dp to 48: eleven
 * rows of second lines is a hundred dp of the sheet spent on numbers. A second line is kept only where
 * it names somebody - the Trust's route, the councils' and clubs' tracks - because a licence is not the
 * count's to replace.
 *
 * The whole row is the switch, because this is a screen used with gloves on. The Switch itself is
 * only the state: the row owns the tap, so a thumb on the switch does not toggle twice.
 */
@Composable
private fun LayerSwitchRow(
    icon: @Composable () -> Unit,
    title: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    count: String? = null,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
            // 34 dp of tile and 7 either side: 48 dp, the smallest a gloved thumb should have to hit.
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        icon()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    // `fill = false` so a short name does not push its own count to the switch.
                    modifier = Modifier.weight(1f, fill = false)
                )
                count?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A tile in **the layer's own colour**, so the sheet and the map agree about whose line is whose.
 *
 * Used by the two rows that are not one of the app's kinds: Te Araroa, which is a route, and Other
 * tracks, which is a publisher. Both have a row on the Colours & Styles screen, and this is that row's
 * answer drawn as a tile - the deep tone on a light screen, the light one on a dark screen, over the page
 * at the same low alpha [KindTile] uses so the two kinds of tile sit together.
 *
 * The map's copy of a colour is a hex string for its style; this is the same string, drawn. Which is the
 * point: one choice, and neither the map nor the sheet can disagree with it.
 */
@Composable
private fun TargetTile(target: LookTarget, glyph: IconGlyph) {
    val look by Look.current.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()
    val hex = remember(look, dark, target) {
        if (dark) look.colourOnDark(target) else look.colourOnLight(target)
    }
    val colour = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
    GlyphTile(
        glyph = glyph,
        background = colour.copy(
            alpha = if (dark) KindTileLook.TINT_ALPHA_DARK else KindTileLook.TINT_ALPHA_LIGHT
        ),
        tint = colour
    )
}

/**
 * The recordings row's tile: **the recorded green**, because that is what the map draws.
 *
 * The green is not on the colour shelf - it is nobody's kind - so there is no look row to read and this
 * cannot use [TargetTile]. It is the same green either way: a line that has been walked is green with its
 * gold edge whatever the walker has chosen for their tracks, and a tile in any other colour would promise
 * something the map does not do.
 */
@Composable
private fun RecordedTile() {
    val dark = isSystemInDarkTheme()
    val colour = remember(dark) {
        Color(android.graphics.Color.parseColor(if (dark) AssetColors.RECORDED_DARK else AssetColors.RECORDED))
    }
    GlyphTile(
        glyph = IconGlyph.RECORDINGS,
        background = colour.copy(
            alpha = if (dark) KindTileLook.TINT_ALPHA_DARK else KindTileLook.TINT_ALPHA_LIGHT
        ),
        tint = colour
    )
}

/**
 * The dataset's licence, on the page its layer is switched on from - which is where a walker is when
 * they wonder whose tracks these are.
 *
 * It asks for two things, and the app did neither before: **name the publisher**, and **state when the
 * data was acquired**. The date is the moment of the newest download rather than the moment this switch
 * was touched, because it is the data that was acquired and not the preference. A walker who has
 * downloaded nothing has acquired nothing, so there is no date to state and the publisher is still worth
 * naming - it says whose tracks the switch would draw.
 */
@Composable
private fun OtherTracksCredit(downloadedAtEpochMs: Long?) {
    Column(
        modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "\u00a9 Herenga \u0101 Nuku Aotearoa, the Outdoor Access Commission",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        downloadedAtEpochMs?.let { at ->
            Text(
                text = "Downloaded ${formatDate(at)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A tile-sized mark for the things that are not one of the place kinds.
 *
 * The background and the glyph are both given a colour: a neutral tile made these the one
 * monochrome thing in an otherwise coloured sheet.
 */
@Composable
private fun GlyphTile(glyph: IconGlyph, background: Color, tint: Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(glyph = glyph, dimension = 20.dp, tint = tint)
    }
}

/**
 * What to say when the basemap has stopped answering, and the way out of it.
 *
 * The app cannot tell a dead key from a Worker that is down - see [TileTrouble] - so the sentence in
 * the middle is the Worker's own, asked for once and repeated as it was given. What the card adds is
 * the thing the walker can actually do about it: the drawn maps need no key, no account and nothing
 * configured, so they work on the day the imagery does not, and one tap puts them under the walker's
 * work rather than sending them into Settings to find a switch.
 *
 * A card rather than a toast, and at the middle of the map rather than in a corner: a map that is not
 * drawing is what the walker is looking at, so this is where their eyes already are.
 */
@Composable
private fun BasemapTroubleCard(
    message: String,
    /** Null when the map already *is* a drawn map: there is then nothing better to offer. */
    onUseDrawnMap: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = TrailDayElevation.floating),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "This map is not drawing", style = MaterialTheme.typography.titleMedium)
            Text(text = message, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "The drawn maps - OpenStreetMap and Topographic - need no key, so they work " +
                    "whatever this is.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onUseDrawnMap?.let { use ->
                    Button(onClick = use) { Text("Use OpenStreetMap") }
                }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
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

/**
 * The plate both things drawn at the foot of the map wear.
 *
 * One constant for the scale bar and the credit strip, because the two are stacked in the same corner
 * and two nearly-identical blacks is how one of them comes to look like a mistake.
 */
private val MAP_PLATE = Color(0x99000000)

/**
 * The bar that says how far across the ground the map is: a distance over a white line with a tick at
 * each end, on the same plate the credit strip wears so it can be read over pale imagery and dark.
 *
 * **The line's own width is the measurement.** The plate is wider than the line by its own padding,
 * and the label sits above the line rather than setting its length, so the distance written is the
 * distance drawn. That is why the width goes on the line and not on the plate - and why the line is a
 * `Canvas` rather than a `Box` with borders: a border is drawn inside its own box and would leave the
 * line a stroke shorter than the ground it stands for.
 *
 * The distance and the width both come from the map, through [ScaleBar]: this draws, the map measures,
 * because where a bar sits is the screen's business and how far a pixel covers is the map's.
 */
@Composable
private fun MapScaleBar(span: ScaleBarSpan, modifier: Modifier = Modifier) {
    val lineWidth = with(LocalDensity.current) { span.widthPx.toDp() }

    Column(
        modifier = modifier
            .background(MAP_PLATE, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = ScaleBar.label(span.metres),
            color = Color.White,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                // No room above the line: this is a number in a plate a few pixels deep, and the room
                // Android reserves for accents pushes it off the middle of that - the same reason the
                // zoom level turns it off.
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
        Canvas(modifier = Modifier.width(lineWidth).height(5.dp)) {
            val stroke = 1.5.dp.toPx()
            val tick = 4.dp.toPx()
            val foot = size.height - stroke / 2f

            drawLine(
                color = Color.White,
                start = Offset(stroke / 2f, foot),
                end = Offset(size.width - stroke / 2f, foot),
                strokeWidth = stroke
            )
            // A tick rising from each end of the line: the ends are what a walker lays against the
            // ground, and a bare line has no ends to read.
            drawLine(
                color = Color.White,
                start = Offset(stroke / 2f, foot),
                end = Offset(stroke / 2f, foot - tick),
                strokeWidth = stroke
            )
            drawLine(
                color = Color.White,
                start = Offset(size.width - stroke / 2f, foot),
                end = Offset(size.width - stroke / 2f, foot - tick),
                strokeWidth = stroke
            )
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
 * [placeNames] is deliberately **not** part of this. The names come from LINZ's topographic tileset, and
 * the credit for that - `LINZ CC BY 4.0 (c) Topographic data` - is drawn by the style document the
 * names are served in, which is where a source has to speak for itself. On the strip there is nothing
 * for it to add: the imagery's own credit already names the publisher and the licence, and the strip
 * grew by a third for a fact the walker had already read. See [nz.mckenzie.trailday.domain.tiles.PlaceLabels].
 *
 * **A `FlowRow`, not a `Row`, and that is the whole of a bug this once had.** A `Row` lays its
 * children out side by side and squeezes them to fit, so with three credits - the imagery's, the
 * topographic data's, and the trail's - the last one was given **102 px of the screen's 1080** and
 * wrapped to one letter per line. Measured on the emulator with all three on: that credit was
 * **378 px tall of 2400**, the strip's box **399 px**, a dark band across the foot of the map - and
 * the credit it was drawing was unreadable at the same time, which is the worse half of it. A
 * `FlowRow` moves a credit that does not fit onto the next line instead: the same three credits are
 * **158 px** in all, both readable, the trail's on a line of its own.
 * The trail's credit carries no leading separator for the same reason: on a line of its own, a dot
 * at the start reads as a mistake.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AttributionStrip(
    basemap: Basemap,
    modifier: Modifier = Modifier,
    teAraroa: Boolean = false
) {
    val uriHandler = LocalUriHandler.current
    FlowRow(
        modifier = modifier
            .background(MAP_PLATE, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = basemap.attribution,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.clickable { uriHandler.openUri(basemap.attributionLink) }
        )
        // The trail is a different publisher with a licence of its own, so it wears its own link rather
        // than borrowing LINZ's: a credit that opens somebody else's page is not a credit.
        if (teAraroa) {
            Text(
                text = TeAraroa.SHORT_ATTRIBUTION,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.clickable { uriHandler.openUri(TeAraroa.ATTRIBUTION_LINK) }
            )
        }
    }
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
 * **A glance, and the live alert check.** This material is not the walker's to change; the header says whose
 * it is, and the facts are the phone's own words for the row - the same line the browser puts under a name,
 * so the two surfaces cannot drift. The alert state belongs to the picked thing, not to the area: the
 * view model asks DOC when the card appears, because an alert kept with a download would be yesterday's
 * answer. Tapping the map anywhere else puts the card away.
 */
@Composable
private fun DocCard(
    pick: DocPick,
    alerts: DocAlertsState?,
    onClose: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = pick.item
    val bike = pick.bike
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // A bicycle beside the label, so "this line can be ridden too" is read at a glance -
                    // and so an orphan bike trail is marked as one rather than as a spot.
                    if (bike != null || item.source == DocSource.BIKES) {
                        AssetKindIcon(kind = AssetKind.BIKE_TRACK, dimension = 14.dp)
                    }
                    Text(
                        text = if (bike != null) {
                            "DOC ${item.source.singular} \u00b7 BIKE TRACK".uppercase()
                        } else {
                            "DOC ${item.source.singular}".uppercase()
                        },
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.2.sp
                    )
                }
                Text(text = item.name, style = MaterialTheme.typography.titleLarge)
                when (val state = alerts) {
                    is DocAlertsState.Found -> if (state.alerts.isNotEmpty()) {
                        DocAlertSection(state.alerts)
                    }

                    is DocAlertsState.Failed -> Text(
                        text = "Could not check DOC alerts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    DocAlertsState.Loading -> Text(
                        text = "Checking DOC alerts\u2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    DocAlertsState.NotAvailable, null -> Unit
                }
                facts.forEach { fact ->
                    Text(text = fact, style = MaterialTheme.typography.bodySmall)
                }
                bike?.let { linked ->
                    // A fallback pair can have a very different name, and then DOC's own trail name
                    // is the useful half of the answer.
                    if (!DocBikeLinks.sameName(item, linked)) {
                        Text(
                            text = "Bike trail: ${linked.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    docBikeFacts(linked)?.let { ride ->
                        Text(
                            text = ride,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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