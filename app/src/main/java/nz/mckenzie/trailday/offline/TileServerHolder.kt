package nz.mckenzie.trailday.offline

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.BuildConfig
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.PlaceLabels
import nz.mckenzie.trailday.map.GlyphFonts
import nz.mckenzie.trailday.map.HostedStyleStore
import java.io.File

/**
 * The app's single tile server, started once from [nz.mckenzie.trailday.TrailDayApplication]
 * and shared by every map screen.
 *
 * A process-wide holder is a deliberate simplification, in the same spirit as
 * `TrackingState`: there is exactly one server and one store per source, every map must
 * use the same tile URL for the offline store to be worth anything, and threading
 * the port through every screen would add plumbing without adding safety. The
 * authoritative data still lives in Room and on disk.
 */
object TileServerHolder {

    /** Where downloaded aerial imagery goes: the offline feature's own directory. */
    const val TILES_DIR = "tiles"

    /**
     * Where the vector Topographic map's tiles and its elevation go, once downloaded or browsed.
     *
     * The app's own files rather than the cache, for the same reason the imagery is: a walker can choose
     * to download this map for an area, and a deliberate download that the storage trimmer may take away
     * is not a download. See [store].
     */
    const val TOPOGRAPHY_DIR = "topography"

    /** Where a browsed basemap's tiles are cached, e.g. `tiles-cache-osm`. */
    const val CACHE_DIR_PREFIX = "tiles-cache-"

    /**
     * The hidden source behind the LiDAR hillshade.
     *
     * It is not a basemap a walker picks; it is the elevation the desk shades when the chosen
     * basemap is LINZ's hosted Topo + hillshade. Served through the same server so the key stays on
     * the phone and the tiles are cached like everything else.
     */
    const val ELEVATION_ID = Basemap.ELEVATION_SOURCE_ID

    /** LINZ answers the terrain-rgb elevation as PNG. */
    const val ELEVATION_SUFFIX = ".png"
    const val ELEVATION_CONTENT_TYPE = "image/png"

    /** LINZ's pre-rendered DEM hillshade, drawn over the vector Topographic map. */
    const val HILLSHADE_ID = Basemap.DEM_HILLSHADE_SOURCE_ID
    const val HILLSHADE_SUFFIX = ".webp"
    const val HILLSHADE_CONTENT_TYPE = "image/webp"

    @Volatile
    private var server: LocalTileServer? = null

    @Volatile
    private var currentKey: String = ""

    /** The XYZ template for [basemap] to give the map, or null if the server is not up yet. */
    fun templateUrl(basemap: Basemap): String? =
        server?.takeIf { it.isRunning }?.tileUrlTemplate(basemap.id)

    /** The XYZ template for the hidden elevation source, or null if the server is not up yet. */
    fun elevationTemplateUrl(): String? =
        server?.takeIf { it.isRunning }?.tileUrlTemplate(ELEVATION_ID)

    /** The XYZ template for the hidden DEM hillshade, or null if the server is not up yet. */
    fun hillshadeTemplateUrl(): String? =
        server?.takeIf { it.isRunning }?.tileUrlTemplate(HILLSHADE_ID)

    /**
     * Where a hosted style should look for [basemap]'s sprite, or null if the server is not up yet.
     *
     * A style's `sprite` key wants a stem: the renderer appends `.json`, `.png` and their doubled
     * resolutions to it and asks this server for each. See [LocalTileServer.spriteUrl].
     */
    fun spriteUrl(basemap: Basemap): String? =
        server?.takeIf { it.isRunning }?.spriteUrl(basemap.id)

    /** Safe to ask for twice: the second ask finds the server up - the app and the tests both ask. */
    fun start(context: Context, initialKey: String = BuildConfig.LINZ_API_KEY) {
        if (server != null) return

        val appContext = context.applicationContext
        currentKey = initialKey

        // The letters a track's name is written with are read out of the app, here and nowhere else: the
        // server itself knows nothing about where they live - see GlyphFonts.
        val started = LocalTileServer(
            sources = sources(appContext),
            readFont = { path -> GlyphFonts.read(appContext, path) },
            // The icons a hosted style draws, out of the same store the style document is kept in.
            readSprite = { path -> sprite(appContext, path) }
        )
        started.start()
        server = started

        // Follow the key from settings for the life of the process.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            SettingsRepository(appContext).linzApiKey.collect { currentKey = it }
        }
    }

    /**
     * One source per basemap, so each is cached and named where its own licence wants it, **and the
     * place names as one more source beside them**.
     *
     * Shared rather than built twice: the editor's server serves the tiles the app's own map has
     * already fetched, from the same stores on disk, which is what makes a block downloaded for a
     * trip with no reception draw on the desk as well. A second list would be a second store, and
     * the two would fill up with the same tiles. The names are in this list for the same reason: the
     * desk draws them over the same imagery, from the same phone, out of the same store.
     */
    fun sources(context: Context): List<TileSource> =
        Basemap.entries.map { basemap ->
            TileSource(
                id = basemap.id,
                store = store(context, basemap),
                suffix = basemap.tileSuffix,
                contentType = basemap.contentType,
                // Read per request, so a key pasted into Settings takes effect at once.
                upstream = {
                    when (basemap) {
                        Basemap.LINZ_AERIAL ->
                            currentKey.takeIf { it.isNotBlank() }?.let { key -> LinzTileFetcher(key) }

                        Basemap.OPENSTREETMAP, Basemap.TOPOGRAPHIC -> DrawnTileFetcher(
                            urlFor = { zoom, x, y -> basemap.tileUrl("", zoom, x, y) }
                        )

                        // The vector Topographic tiles, served through the same server as everything
                        // else so the key stays on the phone and the desk can draw them too.
                        Basemap.LINZ_TOPO_LIDAR_HILLSHADE ->
                            currentKey.takeIf { it.isNotBlank() }?.let { key ->
                                LinzTileFetcher(
                                    key,
                                    urlFor = { zoom, x, y -> basemap.tileUrl(key, zoom, x, y) },
                                    accept = "application/x-protobuf"
                                )
                            }
                    }
                }
            )
        } + listOf(
            placeLabelsSource(context),
            elevationSource(context),
            hillshadeSource(context)
        )

    /**
     * The place names, as one more source in the same server.
     *
     * LINZ's topographic vector tiles, and only their `place_labels` layer is ever drawn - see
     * [PlaceLabels]. They go through this server for the reason everything else does: one place the
     * map fetches from, one store on disk, the key never leaving the phone, and the desk drawing the
     * names from the phone rather than from LINZ itself. Fetched on the way through like any other
     * tile, so the ground that has been looked at keeps its names with no reception.
     *
     * No key, no names: the upstream is null and the route answers 404, exactly as the imagery does.
     */
    private fun placeLabelsSource(context: Context): TileSource = TileSource(
        id = PlaceLabels.ID,
        store = labelsStore(context),
        suffix = PlaceLabels.TILE_SUFFIX,
        contentType = PlaceLabels.CONTENT_TYPE,
        upstream = {
            currentKey.takeIf { it.isNotBlank() }?.let { key ->
                LinzTileFetcher(key, urlFor = { zoom, x, y -> PlaceLabels.tileUrl(key, zoom, x, y) })
            }
        }
    )

    /**
     * The elevation behind the LiDAR hillshade, as one more source in the same server.
     *
     * No key, no shade: the upstream is null and the route answers 404, exactly as the imagery does.
     * It is fetched on the way through like any other tile, so the desk can draw the shade from the
     * phone rather than from LINZ itself.
     */
    private fun elevationSource(context: Context): TileSource = TileSource(
        id = ELEVATION_ID,
        store = elevationStore(context),
        suffix = ELEVATION_SUFFIX,
        contentType = ELEVATION_CONTENT_TYPE,
        upstream = {
            currentKey.takeIf { it.isNotBlank() }?.let { key ->
                LinzTileFetcher(
                    key,
                    urlFor = { zoom, x, y -> Basemap.elevationUrl(key, zoom, x, y) },
                    // The DEM endpoint answers PNG, and MapLibre decodes it as PNG: asking for WebP
                    // first gets bytes the renderer cannot read, which is a hillshade that never draws.
                    accept = "image/png"
                )
            }
        }
    )

    /**
     * Where the elevation tiles are kept: a store an offline area covers, and the desk shades from.
     *
     * The app's own files, because a downloaded Topographic area carries these with it - the heights are
     * half of what the map needs to draw, and a cache the system may reclaim would leave a downloaded
     * area flat. The `-png` in the folder name is a version: an earlier build asked LINZ for WebP while
     * declaring PNG, and those undecodable bytes must not be served now that the request is fixed.
     */
    fun elevationStore(context: Context): OfflineTileStore = OfflineTileStore(
        File(context.applicationContext.filesDir, "$TOPOGRAPHY_DIR/elevation-png"),
        ELEVATION_SUFFIX
    )

    /**
     * LINZ's pre-rendered DEM hillshade, as one more source in the same server.
     *
     * No key, no shade: the upstream is null and the route answers 404, exactly as the imagery does.
     */
    private fun hillshadeSource(context: Context): TileSource = TileSource(
        id = HILLSHADE_ID,
        store = hillshadeStore(context),
        suffix = HILLSHADE_SUFFIX,
        contentType = HILLSHADE_CONTENT_TYPE,
        upstream = {
            currentKey.takeIf { it.isNotBlank() }?.let { key ->
                LinzTileFetcher(
                    key,
                    urlFor = { zoom, x, y -> Basemap.demHillshadeUrl(key, zoom, x, y) }
                )
            }
        }
    )

    /** Where the DEM hillshade tiles are kept: a cache of what has been looked at. */
    fun hillshadeStore(context: Context): OfflineTileStore = OfflineTileStore(
        File(context.applicationContext.cacheDir, "$CACHE_DIR_PREFIX$HILLSHADE_ID"),
        HILLSHADE_SUFFIX
    )

    /**
     * Where the place names are kept: a cache of what has been looked at.
     *
     * The cache directory rather than the app's own files, and the difference is the one
     * [store] draws for the drawn map: nothing here is downloaded on purpose yet, so it is not a
     * thing to carry in a backup or to keep under storage pressure - it is what the map picked up on
     * its way past. Anywhere the walker has looked at, the names are there for good; anywhere they
     * have not, they arrive as they look at it.
     */
    fun labelsStore(context: Context): OfflineTileStore = OfflineTileStore(
        File(context.applicationContext.cacheDir, "$CACHE_DIR_PREFIX${PlaceLabels.ID}"),
        PlaceLabels.TILE_SUFFIX
    )

    /**
     * The tile store for one basemap.
     *
     * The two live in different places on purpose, and it is the licence that decides it:
     *
     *  - **Aerial imagery** goes in the app's own files as it always has, because it is the thing
     *    offline areas are downloaded *into*: deliberate, kept until the walker clears it, and
     *    carried in a phone backup with everything else. The path is unchanged, so a season of
     *    downloaded imagery is not re-fetched by this feature existing.
     *  - **Any other basemap** is a cache of what has been looked at, so it goes in the cache
     *    directory: Android may reclaim it under storage pressure, and it is not backed up. What
     *    it must never be is *pre-fetched* - see [Basemap.prefetchable] - and nothing but the map
     *    itself ever asks for these tiles.
     */
    fun store(context: Context, basemap: Basemap): OfflineTileStore {
        val appContext = context.applicationContext
        val root = when (basemap) {
            Basemap.LINZ_AERIAL -> File(appContext.filesDir, TILES_DIR)
            // The vector Topographic map is the one drawn map a walker can choose to download, so its
            // tiles are kept with the imagery rather than in the cache: see [TOPOGRAPHY_DIR].
            Basemap.LINZ_TOPO_LIDAR_HILLSHADE -> File(appContext.filesDir, TOPOGRAPHY_DIR)
            // Every other drawn map is a cache of what has been looked at: see the comment above.
            Basemap.OPENSTREETMAP, Basemap.TOPOGRAPHIC ->
                File(appContext.cacheDir, "$CACHE_DIR_PREFIX${basemap.id}")
        }
        return OfflineTileStore(root, basemap.tileSuffix)
    }

    /**
     * The store the vector Topographic map's tiles are kept in, and downloaded into.
     *
     * Named rather than defaulted for the same reason [imageryStore] is: adding a basemap must not
     * quietly change what an offline area downloads into.
     */
    fun topographyStore(context: Context): OfflineTileStore =
        store(context, Basemap.LINZ_TOPO_LIDAR_HILLSHADE)

    /**
     * The store the offline imagery feature owns: the aerial one.
     *
     * Named rather than defaulted so that adding a basemap cannot quietly change what an offline
     * area downloads into.
     */
    /**
     * One file of a basemap's sprite, by the `/sprites/<basemap>/<name>` path the map asked for.
     *
     * Judged against the table of basemaps rather than by taking the request's word for it, so a path
     * cannot name a file outside the folder it is kept in - the same rule the letters and the tiles are
     * judged by, and [LocalTileServer.spritePathFor] has already refused anything that is not one of the
     * four files a renderer asks for.
     */
    private fun sprite(context: Context, path: String): ByteArray? {
        val parts = path.split('/')
        if (parts.size != 2) return null
        val basemap = Basemap.entries.firstOrNull { it.id == parts[0] } ?: return null
        return HostedStyleStore.on(context).readSprite(basemap, parts[1])
    }

    fun imageryStore(context: Context): OfflineTileStore = store(context, Basemap.LINZ_AERIAL)

    internal fun stopForTest() {
        server?.stop()
        server = null
    }
}

