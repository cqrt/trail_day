package nz.mckenzie.trailday.map

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.tiles.LatLngBounds as DomainBounds
import nz.mckenzie.trailday.offline.TileServerHolder
import nz.mckenzie.trailday.tracking.DevicePosition
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.roundToInt
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetLayer
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.PlaceLabels

/**
 * Where the map opens when there is nothing better to show: the whole country, at a
 * zoom that shows both islands.
 *
 * It used to be a fixed default near Blenheim, which was a reasonable guess for exactly one
 * person - a walker in Invercargill opened the app looking at Marlborough. The
 * map prefers the device's own position (see `MapViewModel.startBounds`) and the
 * walker's own tracks, and only falls back to this.
 */
val DEFAULT_CAMERA_TARGET = LatLng(-41.5, 172.8)
const val DEFAULT_CAMERA_ZOOM = 6.0

internal const val ASSETS_SOURCE = "trailday-assets"

/** The gold line under a recorded line: see [AssetColors.RECORDED_EDGE]. */
internal const val RECORDED_CASING = "trailday-recorded-casing"

/**
 * The walks and rides the walker has recorded: their own source, because they are evidence rather
 * than one of the places, and their own gold-edged style for the same reason.
 */
internal const val YOUR_TRACKS_SOURCE = "trailday-your-tracks"
internal const val YOUR_TRACKS_CASING = "trailday-your-tracks-casing"
internal const val YOUR_TRACKS_LINE = "trailday-your-tracks-line"

/**
 * Where DOC's material is drawn from: its own source, so it can be styled, ordered and tapped
 * separately from the walker's own work - which is the whole difference between the two.
 */
internal const val DOC_SOURCE = "trailday-doc"

/**
 * DOC's bike points that sit on tracks, drawn as a badge rather than as a place.
 *
 * A separate source because the same point cannot be both a destination pin and a note on a line: the
 * bike points that belong to a track are taken out of the place document and drawn here.
 */
internal const val DOC_BIKE_BADGES_SOURCE = "trailday-doc-bike-badges-source"

/** The bicycle picture the badge layer asks for. */
private const val BIKE_BADGE_IMAGE = "trailday-bike-badge"

/** How big that badge is, in density-independent pixels: smaller than a place marker. */
private const val BIKE_BADGE_DP = 18f

// The layers of the work are named in [AssetLayerIds], which is also where the map's own switches
// find them: the layer that is created and the layer a switch hides are the same name by
// construction, rather than two strings that have to be kept in step.

/**
 * The line being followed, in its own source and layer so it can be drawn wider than the work it is
 * among. Empty whenever no pass is following anything, which is what most maps are.
 */
internal const val TARGET_SOURCE = "trailday-target"
internal const val TARGET_LAYER = "trailday-target-line"

/** How wide the followed line is: nearly twice a track of the walker's, so it is the thing in view. */
private const val TARGET_WIDTH = 9f

internal const val POSITION_SOURCE = "trailday-position"
internal const val POSITION_ACCURACY_LAYER = "trailday-position-accuracy"
internal const val POSITION_DOT_LAYER = "trailday-position-dot"

/**
 * The uncertainty around the dot, faint enough to read the imagery through and to sit under
 * the work rather than over it.
 *
 * Alpha in `rgba()` rather than an eight-digit hex: every style version this app loads
 * understands the first, and the second is a newer thing to rely on.
 */
private const val POSITION_ACCURACY_FILL = "rgba(33, 33, 33, 0.16)"
private const val POSITION_ACCURACY_OUTLINE = "rgba(33, 33, 33, 0.38)"

/**
 * How wide a line of the work is drawn: a track and a bike track.
 */
private const val ASSET_LINE_WIDTH = 5f

/** Padding around the track network when the camera frames it. */
private const val BOUNDS_PADDING_PX = 96

/**
 * How long the camera takes to glide to the next fix while following.
 *
 * Fixes arrive every couple of seconds, so a glide of about a second reads as the map moving
 * along with the phone; a jump from fix to fix reads as the map twitching, and the walker
 * has both hands on a quad bike.
 */
private const val FOLLOW_DURATION_MS = 900

/**
 * The chosen basemap with the track network drawn on top.
 *
 * The native [MapView] is hosted directly rather than through a Compose map wrapper, so that the
 * app keeps control of the style document - which is what lets one tile server serve every
 * basemap, and lets a downloaded area work with no reception.
 */
@Composable
fun BasemapView(
    /**
     * Which map to draw under the work. Imagery unless the walker says otherwise; see
     * [nz.mckenzie.trailday.domain.tiles.Basemap].
     */
    basemap: Basemap,
    /**
     * The LINZ key, for the basemaps that need one.
     *
     * Passed in rather than read here because a key that changes has to reload the style, and
     * that is the screen's flow to observe. A basemap that needs no key ignores it entirely,
     * which is the whole point of having one: the map still works the day a LINZ key expires.
     */
    apiKey: String,
    assetGeoJson: String,
    /** The tracks the walker has recorded, drawn in the recorded green with the gold edge. */
    yourTracksGeoJson: String = AssetGeoJson.build(emptyList()),
    /**
     * DOC's material, drawn **under** the work.
     *
     * Empty by default, and that is what every map shows until something of DOC's has been kept for
     * offline use: the reference layer is what is on the phone, not what could be fetched, so it works
     * exactly where reception does not. See [DocGeoJson].
     */
    docGeoJson: String = AssetGeoJson.build(emptyList()),
    /** The bike points that belong to a track, drawn as a badge over the line. */
    docBikeBadgesGeoJson: String = AssetGeoJson.build(emptyList()),
    /**
     * The line being followed, drawn wide over everything else - see [TARGET_SOURCE].
     *
     * Empty when nothing is being followed, which is every map except the recorder's while a pass is
     * running against a chosen place.
     */
    targetGeoJson: String = AssetGeoJson.build(emptyList()),
    modifier: Modifier = Modifier,
    /**
     * Whether the camera should keep the phone in the middle of the screen.
     *
     * Set while a pass is being driven, when the map is being watched rather than read.
     * Following gives way the moment the walker drags the map - see [onFollowBroken] -
     * because a camera that swings back to the phone at every fix is a camera you cannot look
     * at a block with. The zoom is always the walker's: following moves the map, it does
     * not zoom it.
     */
    follow: Boolean = false,
    /**
     * The walker dragged the map, which is them saying "let me look at this instead".
     *
     * The screen turns following off and says so, rather than the map fighting the drag on
     * the next fix.
     */
    onFollowBroken: () -> Unit = {},
    fitBounds: DomainBounds? = null,
    /**
     * The deepest the camera may sit when it frames [fitBounds].
     *
     * A fit is only ever as good as the box: a place that is one coordinate, or a track a
     * few metres long, has a box that fills the screen at the deepest zoom the map has - which
     * is past the imagery, so the screen comes up as bare background. A cap says how close is
     * still useful, and the asset's own page asks for 15: a place or a short line lands on that,
     * while a fit that is already wider than the cap keeps the fit it always had, so a long line
     * is never cut off to satisfy a number.
     *
     * Null, the default, means no cap - which is what every map that is not about one asset
     * wants, because there the walker has asked for the whole map.
     */
    fitBoundsMaxZoom: Double? = null,
    /**
     * Whether the walker may move this map.
     *
     * True on every map the work is read on. False for a **preview** - the thumbnail in a listing
     * that says where a downloaded area is - where there is nothing to explore and a drag would only
     * move the picture off the thing it is a picture of.
     */
    interactive: Boolean = true,
    /**
     * Whether the phone's own dot is drawn on this map.
     *
     * True on every map the work is read on: where you are standing is the first thing a map of a
     * block is for. False for a preview, which is about a downloaded area rather than about the
     * phone, and where a dot would be one more thing to read in a picture nobody can move.
     */
    showPosition: Boolean = true,
    /**
     * The layers of the work this map is not drawing.
     *
     * Empty by default, which is what every map that is not the walker's own home map wants: a
     * screen that is *about* something - one asset's own line, a pass being recorded, a spot being
     * drawn - has to show it whatever the switches on the home map say. Hiding a layer is a
     * property of the style's layers rather than a reload, so the camera, the tiles and the zoom
     * stay exactly where the walker left them.
     */
    hiddenLayers: Set<AssetLayer> = emptySet(),
    /** Whether the recorded walks and rides are left off: the layers sheet's "Your Tracks" switch. */
    yourTracksHidden: Boolean = false,
    /**
     * Whether LINZ's place names are drawn over the imagery - see
     * [nz.mckenzie.trailday.domain.tiles.PlaceLabels].
     *
     * Off by default, and off for every map that is not the walker's own home map, for the same
     * reason [hiddenLayers] is empty there: a map that is *about* one asset is about that asset.
     * Unlike a layer being hidden, this changes what the style *is* - a source the map would
     * otherwise never fetch - so switching it reloads the style. That is a rare tap (it is a
     * preference, not a glance) and it costs nothing on disk: the tiles come from the app's own
     * server, which has them or goes and gets them.
     */
    placeNames: Boolean = false,
    /**
     * A camera move the walker asked for. The bounds say where; [recentreCount] is what says
     * it is a new request rather than the same one arriving twice, so the second tap on
     * "where am I" moves the map again.
     */
    recentreBounds: DomainBounds? = null,
    recentreCount: Int = 0,
    /**
     * Tile template to render. Defaults to the app's own tile server for the chosen basemap, so
     * every map shares one tile path - and therefore one cache, one set of offline tiles - rather
     * than some screens talking to a provider directly and some not.
     */
    tileUrlTemplate: String? = TileServerHolder.templateUrl(basemap),
    onMapClick: ((latitude: Double, longitude: Double, radiusM: Double) -> Unit)? = null,
    initialTarget: LatLng = DEFAULT_CAMERA_TARGET,
    initialZoom: Double = DEFAULT_CAMERA_ZOOM
) {
    val mapState = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleState = remember { mutableStateOf<Style?>(null) }
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    var boundsApplied by remember { mutableStateOf(false) }

    // The markers a place is drawn with are rendered at the device's own density, so a place is
    // the size it is meant to be, and sharp, on the screen it lands on. Read here and passed
    // in because a style is loaded from two places - the first frame, and a basemap change -
    // and both want the same answer.
    val density = LocalDensity.current.density

    // Keeps the tap handler current without rebuilding the map.
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnFollowBroken by rememberUpdatedState(onFollowBroken)

    // Where the phone is. Every map in the app shows it, and this is the whole of how: the
    // marker is built here from the app's own stream rather than handed in per screen, so a
    // screen added later cannot be the one that forgot. Collection lasts exactly as long as
    // this map is on screen.
    //
    // The stream is remembered rather than asked for on the way through: a fresh one per
    // recomposition would start from "no fix yet" every time the map drew, and a marker that
    // blinks out while the camera is moving is a marker nobody ever sees.
    val positionScope = rememberCoroutineScope()
    val fixes = remember(positionScope) { DevicePosition.updates(positionScope) }
    val fix by fixes.collectAsStateWithLifecycle()
    // A preview shows no dot: the position is not what it is about, and drawing it would be the one
    // moving thing in a picture nobody can move.
    val positionGeoJson = remember(fix, showPosition) {
        PositionGeoJson.build(if (showPosition) fix else null)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                onCreate(null)
                onStart()
                onResume()
                getMapAsync { map ->
                    mapState.value = map
                    map.uiSettings.isCompassEnabled = true
                    // The licence requires attribution to be visible; MapLibre's own
                    // control is backed up by an always-visible overlay in MapScreen.
                    map.uiSettings.isAttributionEnabled = true
                    // A preview is a picture of somewhere, not a place to explore: every gesture off,
                    // so a drag on the list cannot move the thumbnail off what it is a picture of.
                    map.uiSettings.isScrollGesturesEnabled = interactive
                    map.uiSettings.isZoomGesturesEnabled = interactive
                    map.uiSettings.isDoubleTapGesturesEnabled = interactive
                    map.uiSettings.isQuickZoomGesturesEnabled = interactive
                    // North stays up. A gloved hand rotates this map by accident, and the
                    // mistake is invisible until a track points the wrong way - while
                    // nothing on it is easier to read at an angle.
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isTiltGesturesEnabled = false
                    map.cameraPosition = CameraPosition.Builder()
                        .target(initialTarget)
                        .zoom(initialZoom)
                        .build()
                    map.addOnMapClickListener { latLng ->
                        val handler = currentOnMapClick
                        if (handler == null) {
                            false
                        } else {
                            // The radius is the map's own answer for the zoom the walker is working
                            // at, so "which place was that tap" means the same thing zoomed out over
                            // a valley as it does zoomed in on a gate.
                            val zoom = map.cameraPosition.zoom
                            handler(
                                latLng.latitude,
                                latLng.longitude,
                                AssetHitTest.toleranceForZoom(zoom, latLng.latitude)
                            )
                            true
                        }
                    }
                    // A drag is the walker asking for the map rather than the phone, so
                    // following gives way to them. Only a gesture counts: the map's own
                    // camera moves arrive here too, and treating those as a drag would stop
                    // following the first time it followed anything.
                    map.addOnCameraMoveStartedListener(
                        object : MapLibreMap.OnCameraMoveStartedListener {
                            override fun onCameraMoveStarted(reason: Int) {
                                if (reason ==
                                    MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
                                ) {
                                    currentOnFollowBroken()
                                }
                            }
                        }
                    )
                    map.loadTrailDayStyle(
                        basemap = basemap,
                        apiKey = apiKey,
                        tileUrlTemplate = tileUrlTemplate,
                        assetGeoJson = assetGeoJson,
                        yourTracksGeoJson = yourTracksGeoJson,
                        docGeoJson = docGeoJson,
                        docBikeBadgesGeoJson = docBikeBadgesGeoJson,
                        targetGeoJson = targetGeoJson,
                        positionGeoJson = positionGeoJson,
                        density = density,
                        hiddenLayers = hiddenLayers,
                        yourTracksHidden = yourTracksHidden,
                        placeNames = placeNames
                    ) { style ->
                        styleState.value = style
                    }
                }
                mapViewState.value = this
            }
        }
    )

    // The key itself is not used here: the style URL already embeds it, or the tile
    // server holds it. Note what is *not* a key: the position, which changes every few
    // seconds and must never send the map back to reload its style.
    //
    // The place names are in this key rather than applied to the loaded style like a hidden layer,
    // because they are a source the style either has or has not got - a style with no vector source
    // in it has nothing to show. It is a preference the walker sets once, not a switch they flip
    // while reading the map, and the tiles come from the app's own server either way.
    LaunchedEffect(basemap, apiKey, tileUrlTemplate, placeNames) {
        val map = mapState.value
        val usable = !tileUrlTemplate.isNullOrBlank() || !basemap.needsKey || apiKey.isNotBlank()
        if (map != null && usable) {
            map.loadTrailDayStyle(
                basemap = basemap,
                apiKey = apiKey,
                tileUrlTemplate = tileUrlTemplate,
                assetGeoJson = assetGeoJson,
                yourTracksGeoJson = yourTracksGeoJson,
                docGeoJson = docGeoJson,
                docBikeBadgesGeoJson = docBikeBadgesGeoJson,
                targetGeoJson = targetGeoJson,
                positionGeoJson = positionGeoJson,
                density = density,
                hiddenLayers = hiddenLayers,
                yourTracksHidden = yourTracksHidden,
                placeNames = placeNames
            ) { style ->
                styleState.value = style
            }
        }
    }

    LaunchedEffect(assetGeoJson, styleState.value) {
        styleState.value?.getSourceAs<GeoJsonSource>(ASSETS_SOURCE)?.setGeoJson(assetGeoJson)
    }

    LaunchedEffect(yourTracksGeoJson, styleState.value) {
        styleState.value?.getSourceAs<GeoJsonSource>(YOUR_TRACKS_SOURCE)?.setGeoJson(yourTracksGeoJson)
    }

    LaunchedEffect(docGeoJson, styleState.value) {
        styleState.value?.getSourceAs<GeoJsonSource>(DOC_SOURCE)?.setGeoJson(docGeoJson)
    }

    LaunchedEffect(docBikeBadgesGeoJson, styleState.value) {
        styleState.value
            ?.getSourceAs<GeoJsonSource>(DOC_BIKE_BADGES_SOURCE)
            ?.setGeoJson(docBikeBadgesGeoJson)
    }

    // The line being followed, handed over the same way: a new target while the map is up is a new
    // document, not a reload of the style, so the camera and the tiles stay where the walker left them.
    LaunchedEffect(targetGeoJson, styleState.value) {
        styleState.value?.getSourceAs<GeoJsonSource>(TARGET_SOURCE)?.setGeoJson(targetGeoJson)
    }

    // A layer the walker has switched off, applied to the style that is already loaded. This is a
    // property of a layer rather than a new style, so the camera, the tiles and the zoom are all
    // left exactly where they were - which is what makes the switch usable while reading the map
    // rather than something that sends you back to the start every time you touch it.
    LaunchedEffect(hiddenLayers, yourTracksHidden, styleState.value) {
        val style = styleState.value ?: return@LaunchedEffect
        applyLayerVisibility(style, hiddenLayers, yourTracksHidden)
    }

    // The marker follows the fixes. Setting the GeoJSON on one source rather than rebuilding
    // the layer is what keeps a moving dot cheap: the style is written once, and a fix only
    // replaces two features.
    LaunchedEffect(positionGeoJson, styleState.value) {
        styleState.value?.getSourceAs<GeoJsonSource>(POSITION_SOURCE)?.setGeoJson(positionGeoJson)
    }

    // Following: the phone stays in the middle, at the zoom the walker chose, while the pass
    // is being driven. Every fix glides the camera and a newer fix replaces the glide in
    // flight, so the map moves with the phone rather than jumping from fix to fix. Turning
    // following on moves the map at once, rather than waiting for the next fix to arrive.
    LaunchedEffect(fix, follow, mapState.value) {
        val map = mapState.value ?: return@LaunchedEffect
        val point = fix ?: return@LaunchedEffect
        if (!follow) return@LaunchedEffect

        map.animateCamera(
            CameraUpdateFactory.newLatLng(LatLng(point.lat, point.lng)),
            FOLLOW_DURATION_MS
        )
        // Following settles the question of the first frame in the same way an asked-for move
        // does: a walker who starts recording before the tracks' bounds come back off the
        // database must not be dragged off their own pass a second later.
        boundsApplied = true
    }

    // A recentre asked for by the walker. The map reads the work more than it reads the
    // phone, so it stays where it was put unless it is following a pass or is asked to move.
    LaunchedEffect(recentreBounds, recentreCount, mapState.value) {
        val map = mapState.value ?: return@LaunchedEffect
        val bounds = recentreBounds ?: return@LaunchedEffect

        map.animateCamera(cameraFor(map, bounds))
        // An asked-for camera move settles the question of the first frame. Without this, an
        // walker quick enough to tap "where am I" before the tracks' bounds come back off the
        // database is dragged from their own paddock to the work a few seconds later, having
        // done nothing wrong.
        boundsApplied = true
    }

    // Frame the track network the first time we know where it is, so opening the
    // app shows "my tracks", not an arbitrary patch of countryside. The asset's own page
    // also says how close it is allowed to end up - see [fitBoundsMaxZoom].
    LaunchedEffect(fitBounds, fitBoundsMaxZoom, mapState.value) {
        val map = mapState.value ?: return@LaunchedEffect
        val bounds = fitBounds ?: return@LaunchedEffect
        if (boundsApplied) return@LaunchedEffect

        map.animateCamera(cameraFor(map, bounds, fitBoundsMaxZoom))
        boundsApplied = true
    }

    // Hand the map view back to MapLibre when this screen leaves the composition.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            val mapView = mapViewState.value ?: return@onDispose
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
            mapViewState.value = null
            mapState.value = null
            styleState.value = null
        }
    }
}

/**
 * Loads a trail-day style and installs the track source/layer. Tracks are drawn
 * from a data-driven `stroke` property so each kind takes its own colour,
 * and a place is drawn as a marker in the kind's colour.
 *
 * [density] is the device's, and it is what the markers are rendered at: see
 * [addPlaceMarkerImages].
 */
internal fun MapLibreMap.loadTrailDayStyle(
    basemap: Basemap,
    apiKey: String,
    tileUrlTemplate: String?,
    assetGeoJson: String,
    /** The tracks the walker has recorded, drawn in the recorded green with the gold edge. */
    yourTracksGeoJson: String = AssetGeoJson.build(emptyList()),
    /**
     * DOC's material, drawn **under** the work.
     *
     * Empty by default, and that is what every map shows until something of DOC's has been kept for
     * offline use: the reference layer is what is on the phone, not what could be fetched, so it works
     * exactly where reception does not. See [DocGeoJson].
     */
    docGeoJson: String = AssetGeoJson.build(emptyList()),
    /** The bike points that belong to a track, drawn as a badge over the line. */
    docBikeBadgesGeoJson: String = AssetGeoJson.build(emptyList()),
    targetGeoJson: String = AssetGeoJson.build(emptyList()),
    positionGeoJson: String,
    density: Float,
    hiddenLayers: Set<AssetLayer>,
    yourTracksHidden: Boolean = false,
    placeNames: Boolean,
    onLoaded: (Style) -> Unit
) {
    val json = when {
        // A running tile server is the preferred source: it serves downloaded areas
        // with no reception, keeps whatever it fetches, and holds the LINZ key so the
        // style does not have to. It is also the only place the letters for a track's name come
        // from, so a style built on it is the only one that can write a name at all - which is why
        // the place names can only be asked for here too: they are fetched by that server and
        // written with those letters.
        !tileUrlTemplate.isNullOrBlank() -> BasemapStyles.rasterStyleJson(
            basemap,
            tileUrlTemplate,
            glyphsUrl = GlyphFonts.urlTemplateFrom(tileUrlTemplate),
            placeNamesUrl = if (placeNames) PlaceLabels.urlTemplateFrom(tileUrlTemplate) else null
        )

        // No server (tests, previews). A basemap that needs no key can simply be talked to.
        !basemap.needsKey -> BasemapStyles.rasterStyleJson(basemap, basemap.tileTemplate(""))

        apiKey.isNotBlank() -> BasemapStyles.rasterStyleJson(basemap, basemap.tileTemplate(apiKey))

        // A key is needed and there is none: a calm placeholder rather than thousands of
        // tiles that will come back HTTP 400.
        else -> BasemapStyles.blankStyleJson()
    }

    setStyle(Style.Builder().fromJson(json)) { style ->
        // DOC's material first, so it lies **under** the walker's own work: what you made is what you
        // are reading, and the reference is the ground it sits on.
        if (style.getSource(DOC_SOURCE) == null) {
            style.addSource(GeoJsonSource(DOC_SOURCE, docGeoJson))
        }
        if (style.getSource(DOC_BIKE_BADGES_SOURCE) == null) {
            style.addSource(GeoJsonSource(DOC_BIKE_BADGES_SOURCE, docBikeBadgesGeoJson))
        }
        // The pictures both sets of marker layers draw, handed over once for both: DOC's places wear the
        // same glyphs the walker's do, because a hut is a hut wherever it was published.
        addPlaceMarkerImages(style, density)
        addDocLayers(style)

        if (style.getSource(ASSETS_SOURCE) == null) {
            style.addSource(GeoJsonSource(ASSETS_SOURCE, assetGeoJson))
        }
        // The line being followed, drawn **over** the walker's own work and wider than any of it: while a
        // walk is being followed, that line is the thing the screen is about, and a track the same weight as
        // every other track is not. Under the position marker, which is where the walker is against it.
        if (style.getSource(TARGET_SOURCE) == null) {
            style.addSource(GeoJsonSource(TARGET_SOURCE, targetGeoJson))
        }
        if (style.getLayer(TARGET_LAYER) == null) {
            style.addLayer(
                LineLayer(TARGET_LAYER, TARGET_SOURCE)
                    .withProperties(
                        // Its own kind's colour, like every other line of the walker's: what makes it bold
                        // is the width, because a followed bike track should still look like a bike track.
                        PropertyFactory.lineColor(Expression.get("stroke")),
                        PropertyFactory.lineWidth(Expression.literal(TARGET_WIDTH)),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    )
            )
        }
        // The recorded line's own gold edge, under it, filtered on the line's own colour so every screen that
        // draws a recorded line wears it without knowing about layers: the mock screens draw what was walked
        // with an edge, and that edge is what says "this line happened" rather than "this line is planned".
        if (style.getLayer(RECORDED_CASING) == null) {
            style.addLayer(
                LineLayer(RECORDED_CASING, ASSETS_SOURCE)
                    .withProperties(
                        PropertyFactory.lineColor(Expression.literal(AssetColors.RECORDED_EDGE)),
                        PropertyFactory.lineWidth(Expression.literal(ASSET_LINE_WIDTH + 4f)),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    )
                    .withFilter(
                        Expression.eq(Expression.get("stroke"), Expression.literal(AssetColors.RECORDED))
                    )
            )
        }
        // One line layer per kind, because line-dasharray is a constant in MapLibre
        // rather than something a feature can carry. So a track is solid and a bike track
        // is dashed, and the filter is what keeps them apart.
        addLineLayer(style, AssetLayerIds.TRACKS, AssetKind.TRACK)
        addLineLayer(style, AssetLayerIds.BIKE_TRACKS, AssetKind.BIKE_TRACK)
        // What was actually walked or ridden, in its own source: evidence rather than a place, and
        // drawn over the planned lines it is read against.
        if (style.getSource(YOUR_TRACKS_SOURCE) == null) {
            style.addSource(GeoJsonSource(YOUR_TRACKS_SOURCE, yourTracksGeoJson))
        }
        if (style.getLayer(YOUR_TRACKS_CASING) == null) {
            style.addLayer(
                LineLayer(YOUR_TRACKS_CASING, YOUR_TRACKS_SOURCE)
                    .withProperties(
                        PropertyFactory.lineColor(Expression.literal(AssetColors.RECORDED_EDGE)),
                        PropertyFactory.lineWidth(Expression.literal(ASSET_LINE_WIDTH + 4f)),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    )
                    .withFilter(
                        Expression.neq(Expression.get(TrackNames.CARRIES_NAME), Expression.literal(true))
                    )
            )
        }
        if (style.getLayer(YOUR_TRACKS_LINE) == null) {
            style.addLayer(
                LineLayer(YOUR_TRACKS_LINE, YOUR_TRACKS_SOURCE)
                    .withProperties(
                        PropertyFactory.lineColor(Expression.literal(AssetColors.RECORDED)),
                        PropertyFactory.lineWidth(Expression.literal(ASSET_LINE_WIDTH)),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    )
                    .withFilter(
                        Expression.neq(Expression.get(TrackNames.CARRIES_NAME), Expression.literal(true))
                    )
            )
        }
        // The names beside tracks: written over the tracks they belong to, and under the places so a
        // name never covers a place. See addTrackNameLayer.
        addTrackNameLayer(style)
        // A place is a marker, not a short line: drawing a waypoint as a line would claim a shape the
        // record does not have, and a marker says what the thing is rather than only where it is - the
        // same glyph the place's own row carries. The pictures themselves were handed over with DOC's
        // material, before either set of marker layers: both draw the same glyphs.
        PlaceIcons.KINDS.forEach { kind -> addPlaceLayer(style, kind) }

        // Where the phone is. Added after the assets so it draws over them: the work is what
        // the map is for, and where you are standing is read on top of it rather than under it.
        if (style.getSource(POSITION_SOURCE) == null) {
            style.addSource(GeoJsonSource(POSITION_SOURCE, positionGeoJson))
        }
        if (style.getLayer(POSITION_ACCURACY_LAYER) == null) {
            style.addLayer(
                FillLayer(POSITION_ACCURACY_LAYER, POSITION_SOURCE)
                    .withProperties(
                        PropertyFactory.fillColor(POSITION_ACCURACY_FILL),
                        // Faint, but the edge is what the eye measures against the lines
                        // around it, so the edge is the less faint of the two.
                        PropertyFactory.fillOutlineColor(POSITION_ACCURACY_OUTLINE)
                    )
                    .withFilter(partIs(PositionGeoJson.PART_ACCURACY))
            )
        }
        if (style.getLayer(POSITION_DOT_LAYER) == null) {
            style.addLayer(
                CircleLayer(POSITION_DOT_LAYER, POSITION_SOURCE)
                    .withProperties(
                        PropertyFactory.circleColor(Expression.literal(AssetColors.POSITION)),
                        PropertyFactory.circleRadius(Expression.literal(7f)),
                        // The white ring the asset dots wear, for the same reason: the dot has
                        // to separate from whatever imagery is behind it, light or dark.
                        PropertyFactory.circleStrokeColor(Expression.literal("#FFFFFF")),
                        PropertyFactory.circleStrokeWidth(Expression.literal(2.5f))
                    )
                    .withFilter(partIs(PositionGeoJson.PART_DOT))
            )
        }
        // The switches, applied to the style that has just been built, so that the first frame is
        // already the map the walker asked for rather than one that flashes everything and then
        // hides half of it.
        applyLayerVisibility(style, hiddenLayers, yourTracksHidden)
        onLoaded(style)
    }
}

/**
 * Draws or hides each layer of the work, as the switches have it.
 *
 * Visibility rather than removing and re-adding the layers: MapLibre keeps them, so a layer that
 * has been switched off costs nothing to switch back on, and the source is left alone - which is
 * the difference between this and reloading the style.
 *
 * Called as the style is built and again whenever the choice changes. A layer that is not in the
 * style is skipped: this is the map's own list, and a style that is missing one of them is a
 * reason to draw the rest rather than to stop.
 */
private fun applyLayerVisibility(
    style: Style,
    hidden: Set<AssetLayer>,
    yourTracksHidden: Boolean = false
) {
    AssetLayer.ALL.forEach { layer ->
        // DOC Tracks is DOC's alone: the walker's tracks have their own switch, so this one must not
        // hide them or take a recorded line's green core away with its gold edge. Everything else is
        // the walker's layer and DOC's material of the same kind under one switch. See
        // [DocLayerIds.idsOf] for which of DOC's layers each switch hides.
        val ids = if (layer == AssetLayer.TRACKS) {
            DocLayerIds.idsOf(layer)
        } else {
            AssetLayerIds.idsOf(layer) + DocLayerIds.idsOf(layer)
        }
        ids.forEach inner@{ id ->
            val target = style.getLayer(id) ?: return@inner
            target.setProperties(
                PropertyFactory.visibility(if (layer in hidden) Property.NONE else Property.VISIBLE)
            )
        }
    }
    // DOC Tracks is above Bike tracks for the lines: with DOC's tracks off, the rideable ones go too,
    // even if the Bike tracks switch is still on. The bike badges are bike trails in their own right,
    // though - including the orphan points with no track - so they stay under Bike tracks alone.
    if (AssetLayer.TRACKS in hidden) {
        listOf(DocLayerIds.BIKE_CASING, DocLayerIds.BIKE_TRACKS)
            .forEach { id ->
                style.getLayer(id)?.setProperties(
                    PropertyFactory.visibility(Property.NONE)
                )
            }
    }
    // The walker's tracks are one switch: every line of theirs, every name beside one, the gold edge
    // under a recorded line, and the recordings themselves. A track that was recorded is one line
    // here rather than a green core under a Tracks switch that can strip its edge away.
    listOf(
        AssetLayerIds.TRACKS,
        AssetLayerIds.TRACK_NAMES,
        RECORDED_CASING,
        YOUR_TRACKS_CASING,
        YOUR_TRACKS_LINE
    ).forEach inner@{ id ->
        val target = style.getLayer(id) ?: return@inner
        target.setProperties(
            PropertyFactory.visibility(if (yourTracksHidden) Property.NONE else Property.VISIBLE)
        )
    }
}

/**
 * Hands the style the markers a place is drawn with: one per kind of place, in the kind's own colour.
 *
 * Rendered from the same drawing the place list's glyphs come from - see [MarkerIcons] - rather than
 * painted again here, so a hut is a hut in both places. Each is rendered at the
 * device's own density, which is what draws a place at [PlaceIcons.MARKER_DP] on whatever screen it
 * lands on, and keeps the roof, the tent and the white edge sharp while it does it. There is no size
 * in the style to get wrong and nothing for the map to scale.
 */
private fun addPlaceMarkerImages(style: Style, density: Float) {
    val sizePx = (PlaceIcons.MARKER_DP * density).roundToInt()
    if (sizePx <= 0) return
    val outlinePx = PlaceIcons.MARKER_OUTLINE_DP * density
    val missing = HashMap<String, Bitmap>(PlaceIcons.IMAGE_NAMES.size + 1)
    // The bike badge: the bicycle the bike-track kind already draws, smaller than a place marker and
    // with the same white rim, so a point on a line reads as a note about the line.
    if (style.getImage(BIKE_BADGE_IMAGE) == null) {
        missing[BIKE_BADGE_IMAGE] = MarkerIcons.bitmap(
            kind = AssetKind.BIKE_TRACK,
            colorHex = AssetColors.BIKE_TRACK_KIND,
            sizePx = (BIKE_BADGE_DP * density).roundToInt().coerceAtLeast(1),
            outlinePx = outlinePx
        )
    }
    PlaceIcons.KINDS.forEach { kind ->
        val name = PlaceIcons.imageName(kind)
        // An image belongs to the style it was added to, and a style can be loaded again -
        // the same basemap with a key that has just been entered, say. Adding one twice is
        // not wrong so much as unnecessary work on the main thread.
        if (style.getImage(name) == null) {
            missing[name] = MarkerIcons.bitmap(
                kind = kind,
                colorHex = PlaceIcons.colorOf(kind),
                sizePx = sizePx,
                outlinePx = outlinePx
            )
        }
    }
    if (missing.isNotEmpty()) style.addImages(missing)
}

/**
 * Adds the marker layer for one kind of place, if it is not already there.
 *
 * **One layer per kind, each filtering to its own**, which is what lets a kind be hidden on its own:
 * one layer for every place could only be shown or hidden whole. Which picture a place wears is still
 * the feature's own business - see [AssetGeoJson.ICON_PROPERTY] - and that picture is the kind's own
 * glyph in the kind's own colour.
 */
private fun addPlaceLayer(
    style: Style,
    kind: AssetKind,
    id: String = AssetLayerIds.pointOf(kind),
    source: String = ASSETS_SOURCE
) {
    if (style.getLayer(id) != null) return

    style.addLayer(
        SymbolLayer(id, source)
            .withProperties(
                // The feature names the marker it wants, so which colour a place is drawn in is
                // decided in Kotlin, where it is tested - and the fallback means a place can never
                // be drawn as nothing at all.
                PropertyFactory.iconImage(
                    Expression.coalesce(
                        Expression.get(AssetGeoJson.ICON_PROPERTY),
                        Expression.literal(PlaceIcons.FALLBACK_IMAGE_NAME)
                    )
                ),
                // The picture is already the size a place is drawn at on this device - see
                // [addPlaceMarkerImages] - because an image is drawn at its own pixels: there is no
                // icon-size here to keep in step with the screen.
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                // Every place is drawn, wherever it is. A dot never hid from another dot, and a spot
                // that vanishes because a second one is near it reads as a spot that has been
                // deleted.
                PropertyFactory.iconAllowOverlap(Expression.literal(true)),
                PropertyFactory.iconIgnorePlacement(Expression.literal(true))
            )
            .withFilter(
                Expression.all(
                    Expression.eq(Expression.get("kind"), Expression.literal(kind.name)),
                    shapeIs(AssetShape.POINT)
                )
            )
    )
}

/**
 * Adds one of the kind's line layers if it is not already there.
 *
 * The dash pattern comes from [AssetLineStyles], so which kind is drawn dashed is
 * decided in one testable place rather than here.
 */
private fun addLineLayer(
    style: Style,
    id: String,
    kind: AssetKind,
    source: String = ASSETS_SOURCE,
    bikeFriendly: Boolean? = null
) {
    if (style.getLayer(id) != null) return

    val properties = mutableListOf<PropertyValue<*>>(
        PropertyFactory.lineColor(Expression.get("stroke")),
        PropertyFactory.lineWidth(Expression.literal(ASSET_LINE_WIDTH)),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        PropertyFactory.lineOpacity(Expression.literal(0.9f))
    )
    AssetLineStyles.forKind(kind)?.let { dash -> properties += PropertyFactory.lineDasharray(dash) }

    val filters = mutableListOf(
        Expression.eq(Expression.get("kind"), Expression.literal(kind.name)),
        shapeIs(AssetShape.LINE),
        // The one stretch of a track that carries the track's name belongs to the name layer
        // alone. Drawn here as well it would be a second line over the track - and, on a
        // half-walked track, one line in one colour over the two that say how far the walk has
        // got. A feature without the property at all passes this, which is every other feature.
        Expression.neq(Expression.get(TrackNames.CARRIES_NAME), Expression.literal(true))
    )
    bikeFriendly?.let { filters += bikeFriendlyFilter(it) }

    style.addLayer(
        LineLayer(id, source)
            .withProperties(*properties.toTypedArray())
            .withFilter(Expression.all(*filters.toTypedArray()))
    )
}

/**
 * Adds the names beside tracks, or does nothing when the style already has them.
 *
 * One name per track, written along the one stretch of it the app chose - a stretch of the track's own
 * points, so the name follows the shape of the track rather than being laid across it - and hidden with
 * the track by the same switch, through [AssetLayerIds.idsOf].
 *
 * Every number is [TrackNames]'s, which is where the desk's own style gets them too: a name the same
 * size, in the same white, with the same outline, the same distance off the line, on both maps. The
 * letters come from [GlyphFonts], which travels inside the app and is handed out by its own server.
 *
 * Added after the lines and before the places, which is where it is drawn: a name belongs over the track
 * it names, and never over a place - a marker that is not drawn reads as a marker that has been deleted.
 */
private fun addTrackNameLayer(style: Style) {
    if (style.getLayer(AssetLayerIds.TRACK_NAMES) != null) return

    val names = SymbolLayer(AssetLayerIds.TRACK_NAMES, ASSETS_SOURCE)
        .withProperties(
            PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE_CENTER),
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(arrayOf(GlyphFonts.STACK)),
            PropertyFactory.textSize(Expression.literal(TrackNames.SIZE)),
            PropertyFactory.textMaxAngle(Expression.literal(TrackNames.MAX_ANGLE)),
            PropertyFactory.textOffset(arrayOf(0f, TrackNames.OFFSET)),
            PropertyFactory.textRotationAlignment(Property.TEXT_ROTATION_ALIGNMENT_MAP),
            // Letters the right way up whatever the line does, and never hidden by each other: a
            // name that vanishes behind another name reads as a track that has lost its name.
            PropertyFactory.textKeepUpright(Expression.literal(true)),
            PropertyFactory.textAllowOverlap(Expression.literal(true)),
            PropertyFactory.textIgnorePlacement(Expression.literal(true)),
            PropertyFactory.textColor(Expression.literal(TrackNames.COLOUR)),
            PropertyFactory.textHaloColor(Expression.literal(TrackNames.HALO_COLOUR)),
            PropertyFactory.textHaloWidth(Expression.literal(TrackNames.HALO_WIDTH))
        )
        .withFilter(
            Expression.all(
                Expression.eq(Expression.get("kind"), Expression.literal(AssetKind.TRACK.name)),
                shapeIs(AssetShape.LINE),
                Expression.eq(Expression.get(TrackNames.CARRIES_NAME), Expression.literal(true))
            )
        )
    // A name waits for a close view; the line it belongs to does not. On a wide view several tracks'
    // names crowd the screen and read as a muddle.
    names.minZoom = TrackNames.MIN_ZOOM
    style.addLayer(names)
}

/**
 * Adds DOC's material: the cased tracks, and a marker layer for every kind DOC publishes a place as.
 *
 * **No switches are wired to these.** What DOC publishes is the ground the day's work sits on rather
 * than another layer of it, and the layer switches are the walker's answer about their *own* things -
 * see the plan for where the reference layer gets its own control, if it ever does.
 */
private fun addDocLayers(style: Style) {
    addDocCasing(style, DocLayerIds.CASING, bikeFriendly = false)
    addDocCasing(style, DocLayerIds.BIKE_CASING, bikeFriendly = true)
    addLineLayer(style, DocLayerIds.TRACKS, AssetKind.TRACK, source = DOC_SOURCE, bikeFriendly = false)
    addLineLayer(
        style,
        DocLayerIds.BIKE_TRACKS,
        AssetKind.TRACK,
        source = DOC_SOURCE,
        bikeFriendly = true
    )
    DocLayerIds.REACHABLE_MARKER_KINDS.forEach { kind ->
        addPlaceLayer(style, kind, id = DocLayerIds.pointOf(kind), source = DOC_SOURCE)
    }
    addDocBikeBadgeLayer(style)
}

/**
 * Adds the bicycle badge that marks a track DOC says may be ridden.
 *
 * A symbol rather than a marker layer: the bike point is a note on the line, not a place to stop at.
 * It is drawn after the tracks and places so it sits over the line it belongs to, and its own image
 * carries the white rim that lifts it off the ground.
 */
private fun addDocBikeBadgeLayer(style: Style) {
    if (style.getLayer(DocLayerIds.BIKE_BADGES) != null) return

    style.addLayer(
        SymbolLayer(DocLayerIds.BIKE_BADGES, DOC_BIKE_BADGES_SOURCE)
            .withProperties(
                PropertyFactory.iconImage(BIKE_BADGE_IMAGE),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                PropertyFactory.iconAllowOverlap(Expression.literal(true)),
                PropertyFactory.iconIgnorePlacement(Expression.literal(true))
            )
    )
}

/**
 * The white line under DOC's tracks.
 *
 * The same geometry as the line over it, drawn wider and first: a route has to read against dark bush
 * and pale scree alike, and the walker's own lines have no casing - so the casing is also what says, at
 * a glance, which lines are DOC's. The mock screens draw their track exactly this way.
 */
private fun addDocCasing(style: Style, id: String, bikeFriendly: Boolean) {
    if (style.getLayer(id) != null) return

    val filters = mutableListOf(
        Expression.eq(Expression.get("kind"), Expression.literal(AssetKind.TRACK.name)),
        shapeIs(AssetShape.LINE)
    )
    filters += bikeFriendlyFilter(bikeFriendly)

    style.addLayer(
        LineLayer(id, DOC_SOURCE)
            .withProperties(
                PropertyFactory.lineColor(Expression.literal(DocLayerIds.CASING_COLOUR)),
                PropertyFactory.lineWidth(
                    Expression.literal(ASSET_LINE_WIDTH + DocLayerIds.CASING_WIDTH_PX)
                ),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineOpacity(Expression.literal(0.75f))
            )
            .withFilter(Expression.all(*filters.toTypedArray()))
    )
}

/** A filter for the bike property: true for rideable tracks, false for the rest. */
private fun bikeFriendlyFilter(bikeFriendly: Boolean): Expression =
    if (bikeFriendly) {
        Expression.eq(Expression.get(AssetGeoJson.BIKE_FRIENDLY_PROPERTY), Expression.literal(true))
    } else {
        Expression.neq(Expression.get(AssetGeoJson.BIKE_FRIENDLY_PROPERTY), Expression.literal(true))
    }

private fun shapeIs(shape: AssetShape): Expression =
    Expression.eq(Expression.get("shape"), Expression.literal(shape.name))

/** Which of the marker's two features a layer draws: the dot, or the ring of uncertainty. */
private fun partIs(part: String): Expression =
    Expression.eq(Expression.get(PositionGeoJson.PART_PROPERTY), Expression.literal(part))

/**
 * The camera that fits a set of bounds, with the app's padding.
 *
 * One place rather than three, because three things now ask for exactly this: the first frame
 * on the work, the first frame on the phone when there is no work, and the recentre the
 * walker asks for by tapping.
 *
 * With a [maxZoom] in hand the camera is worked out here rather than by the library, because
 * the cap is a statement about the zoom the fit lands on, and a [CameraUpdate] does not know
 * its own zoom until the map applies it. That needs a map that has been laid out - which it
 * has, by the time bounds arrive - and it is what lets the asset's own page end up at 15
 * rather than at the deepest zoom the map has.
 */
private fun cameraFor(map: MapLibreMap, bounds: DomainBounds, maxZoom: Double? = null): CameraUpdate {
    val maplibreBounds = org.maplibre.android.geometry.LatLngBounds.Builder()
        .include(LatLng(bounds.minLat, bounds.minLng))
        .include(LatLng(bounds.maxLat, bounds.maxLng))
        .build()

    if (maxZoom == null) return CameraUpdateFactory.newLatLngBounds(maplibreBounds, BOUNDS_PADDING_PX)

    // Null when the library cannot work a camera out at all - a map with no size yet, or bounds
    // it refuses. Falling back to the plain fit is what every other screen does anyway, and it is
    // the shape of the screen that decides whether that is anything to worry about: this path is
    // the asset's own page, whose map is laid out long before its geometry arrives.
    val fitted = map.getCameraForLatLngBounds(
        maplibreBounds,
        intArrayOf(BOUNDS_PADDING_PX, BOUNDS_PADDING_PX, BOUNDS_PADDING_PX, BOUNDS_PADDING_PX)
    ) ?: return CameraUpdateFactory.newLatLngBounds(maplibreBounds, BOUNDS_PADDING_PX)

    return CameraUpdateFactory.newCameraPosition(
        CameraPosition.Builder(fitted).zoom(minOf(fitted.zoom, maxZoom)).build()
    )
}
