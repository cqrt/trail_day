package nz.mckenzie.trailday.domain.tiles

/**
 * A map to draw under the work.
 *
 * There are two kinds of thing here, and the difference matters more than the code:
 *
 *  - **Aerial imagery** (LINZ): a photograph of the ground, which is what a route is planned
 *    from, and which the app may *download ahead of time* into the offline store.
 *  - **A drawn map** (OpenStreetMap): roads, tracks, gates, streams and names, which is what the
 *    walker reads to find a way in or to say where they are - but it shows nothing about what is
 *    growing, and its tiles may only be fetched as they are looked at. See [prefetchable].
 *
 * **No source here asks the walker for anything.** LINZ's tiles are fetched through the app's own
 * proxy, which holds the key - so there is nothing to paste into Settings, and nothing that expires on
 * a phone. See [LINZ_TILES_HOST].
 *
 * Everything specific to a source lives in this table: the tile URL, how tiles are named on
 * disk, the zoom it is published to, and the attribution its licence requires. That is on
 * purpose - the alternative is the same facts scattered across a style builder, a fetcher, a
 * settings screen and a licence notice, and drifting apart.
 */
enum class Basemap(
    /** Stable id: it names the source in the tile URL and in preferences. Never renamed. */
    val id: String,
    /** What the picker calls it. */
    val displayName: String,
    /** One line for the picker, in the walker's terms. */
    val summary: String,
    /** How a tile file is named on disk and in the URL, e.g. `.webp`. */
    val tileSuffix: String,
    /** What the server answers with, e.g. `image/webp`. */
    val contentType: String,
    /** The deepest zoom the source publishes; the map overzooms beyond it. */
    val maxZoom: Int,
    /**
     * Whether the app may fetch these tiles **before** anyone looks at them.
     *
     * True for imagery, which is what offline areas are for: a block is downloaded once, on
     * purpose, and the walker drives around it with no reception. **False for OpenStreetMap,
     * and this is a licence term rather than a preference**: their tile usage policy prohibits
     * pre-emptive fetching - "bulk downloading" - and offline packs, and points anyone who wants
     * offline tiles at self-hosted ones instead. The tiles that have been looked at are still
     * cached as they are looked at, which is what the same policy requires.
     */
    val prefetchable: Boolean,
    /** The credit its licence requires, shown on every map that draws it. */
    val attribution: String,
    /** Where that credit links to, as the licence requires. */
    val attributionLink: String
) {

    LINZ_AERIAL(
        id = "linz-aerial",
        displayName = "Aerial imagery",
        summary = "Aerial photography from LINZ. Can be downloaded for offline use.",
        tileSuffix = ".webp",
        contentType = "image/webp",
        maxZoom = 22,
        prefetchable = true,
        attribution = "LINZ CC BY 4.0 \u00a9 Imagery Basemap contributors",
        attributionLink = "https://www.linz.govt.nz/products-services/data/licensing-and-using-" +
            "data/attributing-linz-basemaps-data"
    ),

    OPENSTREETMAP(
        id = "osm",
        displayName = "OpenStreetMap",
        summary = "Their own drawn map. Cached as you browse; cannot be downloaded.",
        tileSuffix = ".png",
        contentType = "image/png",
        maxZoom = 19,
        prefetchable = false,
        attribution = "\u00a9 OpenStreetMap contributors",
        attributionLink = "https://www.openstreetmap.org/copyright"
    ),

    TOPOGRAPHIC(
        id = "topo",
        displayName = "Topographic (OSM)",
        summary = "The same, with contours and heights. Cached as you browse.",
        tileSuffix = ".png",
        contentType = "image/png",
        maxZoom = 17,
        // Their tile policy is the same shape as OpenStreetMap's: looked at as you go, never fetched ahead.
        prefetchable = false,
        attribution = "\u00a9 OpenStreetMap contributors, SRTM | \u00a9 OpenTopoMap (CC BY-SA)",
        attributionLink = "https://opentopomap.org/about"
    ),

    /**
     * LINZ's clean vector Topographic map with its LiDAR hillshade.
     *
     * **A vector style, not a raster tile set.** LINZ publishes `topographic-v2` as vector tiles plus
     * a terrain-rgb elevation source, and its own style already draws the hillshade. The app fetches
     * that style, points its two sources at the app's own proxy, and loads it, so both tile sets are
     * cached like every other basemap.
     */
    LINZ_TOPO_LIDAR_HILLSHADE(
        id = "linz-topo-lidar-hillshade",
        displayName = "Topographic (LINZ)",
        summary = "LINZ's clean Topographic map with LiDAR hillshade. Cached as you browse.",
        tileSuffix = ".pbf",
        contentType = "application/x-protobuf",
        maxZoom = 15,
        prefetchable = false,
        attribution = "LINZ CC BY 4.0 \u00a9 Topographic data",
        attributionLink = "https://www.linz.govt.nz/products-services/data/licensing-and-using-" +
            "data/attributing-linz-basemaps-data"
    );

    /**
     * The XYZ template to hand MapLibre, with the key in it where the source needs one.
     *
     * OpenStreetMap's URL is the one their policy names, over https: `http://` is refused, and a
     * library's default user agent is not enough - which is why their tiles are fetched by the
     * app's own tile server rather than by the map itself. See
     * [nz.mckenzie.trailday.offline.DrawnTileFetcher].
     */
    fun tileTemplate(): String = when (this) {
        LINZ_AERIAL ->
            "$LINZ_TILES_HOST/v1/tiles/aerial/$LINZ_TILE_MATRIX/{z}/{x}/{y}.webp"

        OPENSTREETMAP ->
            "$OSM_HOST/{z}/{x}/{y}.png"

        TOPOGRAPHIC ->
            "$TOPO_HOST/{z}/{x}/{y}.png"

        LINZ_TOPO_LIDAR_HILLSHADE ->
            "$LINZ_TILES_HOST/v1/tiles/$LINZ_TOPOGRAPHIC_V2_TILESET/$LINZ_TILE_MATRIX/" +
                "{z}/{x}/{y}.pbf"
    }

    /** One specific tile, as the fetchers need it. */
    fun tileUrl(zoom: Int, x: Int, y: Int): String = when (this) {
        LINZ_AERIAL ->
            "$LINZ_TILES_HOST/v1/tiles/aerial/$LINZ_TILE_MATRIX/$zoom/$x/$y.webp"

        OPENSTREETMAP ->
            "$OSM_HOST/$zoom/$x/$y.png"

        TOPOGRAPHIC ->
            "$TOPO_HOST/$zoom/$x/$y.png"

        LINZ_TOPO_LIDAR_HILLSHADE ->
            "$LINZ_TILES_HOST/v1/tiles/$LINZ_TOPOGRAPHIC_V2_TILESET/$LINZ_TILE_MATRIX/" +
                "$zoom/$x/$y.pbf"
    }

    /**
     * One tile to show beside this basemap in the picker.
     *
     * A real tile from the source, at a fixed place and zoom, so the walker sees what each map
     * actually looks like. The vector Topographic map cannot be drawn as a raster tile, so its
     * preview is LINZ's clean gridless Topo raster of the same ground - the closest honest picture.
     */
    fun previewTileUrl(zoom: Int, x: Int, y: Int): String = when (this) {
        LINZ_AERIAL -> tileUrl(zoom, x, y)
        OPENSTREETMAP, TOPOGRAPHIC -> tileUrl(zoom, x, y)
        LINZ_TOPO_LIDAR_HILLSHADE ->
            "$LINZ_TILES_HOST/v1/tiles/$PREVIEW_TOPO_TILESET/$LINZ_TILE_MATRIX/" +
                "$zoom/$x/$y.webp"
    }

    /** Whether this basemap is one of LINZ's hosted style documents rather than a single tile set. */
    val usesHostedStyle: Boolean get() = this == LINZ_TOPO_LIDAR_HILLSHADE

    /**
     * LINZ's own style document for the vector Topographic basemap.
     *
     * The style is fetched by the app and adapted before it is loaded: LINZ's hosted document carries a
     * `sky` block, names its sprite and letters at LINZ's own host, and its sources are pointed at the
     * app's proxy so the whole of it is one hop from the phone. See [LinzVectorStyle].
     */
    fun hostedStyleUrl(): String? =
        if (!usesHostedStyle) {
            null
        } else {
            "$LINZ_TILES_HOST/v1/tiles/$LINZ_TOPOGRAPHIC_V2_TILESET/$LINZ_TILE_MATRIX/" +
                "style/$LINZ_TOPOGRAPHIC_V2_TILESET.json"
        }

    /** The credit as HTML, single-quoted so it drops into a style document without escaping. */
    fun attributionHtml(): String = "<a href='$attributionLink'>$attribution</a>"

    companion object {
        /** What the map draws until the walker chooses otherwise. */
        val DEFAULT: Basemap = LINZ_AERIAL

        /**
         * Where LINZ's tiles are fetched from: **the app's own proxy, not LINZ**.
         *
         * Every LINZ URL in the app is built from this one constant, and every one of them is a LINZ
         * path on the proxy's host - so the proxy is a swap of this line and nothing else. The key it
         * needs lives there as a Worker secret, which is why nothing here carries one and why nothing
         * expires on a phone. See `worker-linz/README.md`, and [LINZ_ORIGIN] for the host this stands
         * in for.
         */
        const val LINZ_TILES_HOST = "https://trailday-linz-proxy.le-mckenzies.workers.dev"

        /**
         * LINZ's own host, which the app no longer talks to.
         *
         * Named for the one thing that still needs it: a style document LINZ serves names its sprite
         * and its letters at LINZ's host, and those are rewritten to the proxy before the style is
         * loaded. See [nz.mckenzie.trailday.map.LinzVectorStyle].
         */
        const val LINZ_ORIGIN = "https://basemaps.linz.govt.nz"

        /**
         * OpenTopoMap's tile host: a drawn topographic map of the whole world - contours, tracks,
         * streams and names - under the same look-at-it-as-you-go terms as OpenStreetMap's layer.
         * Their tiles are built from OpenStreetMap data and SRTM heights, so their credit names both.
         */
        const val TOPO_HOST = "https://tile.opentopomap.org"
        const val LINZ_TILE_MATRIX = "WebMercatorQuad"

        /** LINZ's clean vector Topographic map, whose own style carries the LiDAR hillshade. */
        const val LINZ_TOPOGRAPHIC_V2_TILESET = "topographic-v2"

        /** LINZ's gridless Topo raster, used only as the picker's picture of the vector map. */
        const val PREVIEW_TOPO_TILESET = "topo-raster"

        /** The hidden elevation source behind the hillshade, served by the app's tile server. */
        const val ELEVATION_SOURCE_ID = "linz-elevation"

        /** LINZ's pre-rendered New Zealand DEM Hillshade, drawn over the vector Topographic map. */
        const val DEM_HILLSHADE_TILESET = "hillshade"
        const val DEM_HILLSHADE_SOURCE_ID = "linz-dem-hillshade"

        /**
         * OpenStreetMap's standard tile layer.
         *
         * Over https, because their policy refuses `http://`, and their own host rather than a
         * mirror, because a mirror has its own policy.
         */
        const val OSM_HOST = "https://tile.openstreetmap.org"

        /** LINZ's own attribution page, as the table has it. */
        val LINZ_ATTRIBUTION_LINK: String get() = LINZ_AERIAL.attributionLink

        /** Where OpenStreetMap's attribution must point, as the table has it. */
        val OSM_COPYRIGHT_LINK: String get() = OPENSTREETMAP.attributionLink

        /** Their recommended way for a walker to report a wrong road or a missing gate. */
        const val OSM_REPORT_LINK = "https://www.openstreetmap.org/fixthemap"

        /**
         * The basemap a stored value names, or the default.
         *
         * Anything unrecognised reads as the default rather than throwing: a preference written
         * by a later build, or a value edited by hand, must not stop the app drawing a map - and
         * imagery is the answer that always makes sense, because it is what the app is for.
         */
        fun fromStorage(value: String?): Basemap =
            entries.firstOrNull { it.id == value } ?: DEFAULT

        /**
         * LINZ's terrain-rgb elevation, which a renderer shades into the hillshade.
         *
         * The hosted style carries this source for the phone. The desk cannot read a hosted style, so
         * it is given the same source through the phone's own tile server and draws the shade itself.
         */
        fun elevationTemplate(): String =
            "$LINZ_TILES_HOST/v1/tiles/elevation/$LINZ_TILE_MATRIX/{z}/{x}/{y}.png" +
                "?pipeline=terrain-rgb"

        /** One elevation tile, as the fetchers need it. */
        fun elevationUrl(zoom: Int, x: Int, y: Int): String =
            "$LINZ_TILES_HOST/v1/tiles/elevation/$LINZ_TILE_MATRIX/$zoom/$x/$y.png" +
                "?pipeline=terrain-rgb"

        /** LINZ's pre-rendered DEM hillshade, as a template for the desk and the phone's server. */
        fun demHillshadeTemplate(): String =
            "$LINZ_TILES_HOST/v1/tiles/$DEM_HILLSHADE_TILESET/$LINZ_TILE_MATRIX/" +
                "{z}/{x}/{y}.webp"

        /** One DEM hillshade tile, as the fetchers need it. */
        fun demHillshadeUrl(zoom: Int, x: Int, y: Int): String =
            "$LINZ_TILES_HOST/v1/tiles/$DEM_HILLSHADE_TILESET/$LINZ_TILE_MATRIX/" +
                "$zoom/$x/$y.webp"

        /** LINZ's hosted topographic style, for a basemap this build does not offer yet. */
        fun linzTopographicStyleUrl(): String =
            "$LINZ_TILES_HOST/v1/tiles/topographic/$LINZ_TILE_MATRIX/style/topographic.json"
    }
}
