package nz.mckenzie.trailday.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import nz.mckenzie.trailday.doc.ArcGisDoc
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.LinzAccess
import java.net.HttpURLConnection
import java.net.URL

/**
 * LINZ's hosted vector Topographic style, adapted for the app.
 *
 * **Why it is fetched rather than loaded by URL.** LINZ's style document carries a `sky` block that
 * the bundled renderer may refuse, and its glyph and sprite URLs have no key. Loading it straight
 * into MapLibre is the path that took the app down; fetching it first lets the app drop the sky,
 * give its fonts and sprites a home it can reach with no reception, and point its two sources at its
 * own tile server.
 *
 * The two sources are kept, and named as LINZ names them, because the style's 287 layers reference
 * them by name: the vector Topographic tiles, and the terrain-rgb elevation the `hillshade` layer
 * is drawn from.
 *
 * **Everything the style reaches for is fetched through the app**, and every one of those fetches is
 * kept in [HostedStyleStore] - the document, the sprite - or served from a store that is already kept
 * (the tiles, the elevation). That is what makes this basemap drawable with no reception: a style that
 * cannot be fetched is a blank rectangle, however many of its tiles are on the phone.
 */
object LinzVectorStyle {

    /**
     * The adapted style for [basemap], or null when the proxy cannot be read and nothing has been kept.
     *
     * [vectorTileTemplate] and [elevationTileTemplate] are the app's own tile-server templates when it
     * is running; a null elevation template falls back to the proxy directly. [glyphsUrl] and
     * [spriteUrl] are the app's own addresses for the letters and the icons, and [store] is what
     * earlier loads kept.
     */
    suspend fun load(
        basemap: Basemap,
        vectorTileTemplate: String?,
        /** The app's own elevation route, when its tile server is up; null reaches the proxy directly. */
        elevationTileTemplate: String? = null,
        /** The app's own letters, when its tile server is up; null keeps LINZ's own. */
        glyphsUrl: String? = null,
        /** Where the app serves [basemap]'s sprite, when its tile server is up. */
        spriteUrl: String? = null,
        /** What earlier loads kept, so this one can be answered with no network at all. */
        store: HostedStyleStore? = null
    ): String? = withContext(Dispatchers.IO) {
        val url = basemap.hostedStyleUrl() ?: return@withContext null
        // The proxy first, what was kept second: a phone with no reception still has the document from
        // the last time it had one, which is the whole point of keeping it.
        val body = readText(url)?.also { store?.writeStyle(basemap, it) }
            ?: store?.readStyle(basemap)
            ?: return@withContext null
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return@withContext null

        // The sprite is fetched here rather than left to the renderer, because the renderer asks the
        // app's own server for it and that server has nothing to answer with until something has.
        if (store != null) keepSprite(basemap, root, store)

        val sources = buildJsonObject {
            put(
                VECTOR_SOURCE,
                buildJsonObject {
                    put("type", "vector")
                    put(
                        "tiles",
                        buildJsonArray { add(vectorTileTemplate ?: basemap.tileTemplate()) }
                    )
                    put("minzoom", 0)
                    put("maxzoom", basemap.maxZoom)
                }
            )
            put(
                ELEVATION_SOURCE,
                buildJsonObject {
                    put("type", "raster-dem")
                    // Through the app's own server when it is up, so the heights it shades are in a
                    // store the app keeps and an offline area can cover them. Straight from the proxy
                    // otherwise, which is what LINZ's own style does.
                    put(
                        "tiles",
                        buildJsonArray {
                            add(elevationTileTemplate ?: Basemap.elevationTemplate())
                        }
                    )
                    put("tileSize", 256)
                    put("minzoom", 0)
                    put("maxzoom", basemap.maxZoom)
                    // MapLibre Native accepts only `mapbox` and `terrarium` here. LINZ's
                    // `pipeline=terrain-rgb` is Mapbox Terrain-RGB, so this is the correct value -
                    // and an unrecognised one is an error the native parser does not recover from.
                    put("encoding", "mapbox")
                }
            )
        }

        val cleaned = JsonObject(
            root.toMutableMap().apply {
                // The renderer has no sky, and a style it will not read is a blank rectangle.
                remove("sky")
                put("sources", sources)
                put("layers", layersWithStrongerHillshade(root, appFonts = glyphsUrl != null))
                // The letters the app ships rather than LINZ's, when the app's server is up: they are
                // already on the phone, so a name draws with no reception and for no extra bytes. See
                // [GlyphFonts]. LINZ's own address, through the proxy, is the fallback.
                val glyphs = glyphsUrl ?: root["glyphs"]?.jsonPrimitive?.contentOrNull?.let {
                    throughProxy(it)
                }
                glyphs?.let { put("glyphs", JsonPrimitive(it)) }
                // The app's own server for the sprite, which is where the copy kept above is served
                // from; LINZ's own address, through the proxy, is the fallback for a load with no
                // server behind it.
                val sprite = spriteUrl ?: root["sprite"]?.jsonPrimitive?.contentOrNull?.let {
                    throughProxy(it)
                }
                sprite?.let { put("sprite", JsonPrimitive(it)) }
            }
        )
        cleaned.toString()
    }

    /**
     * The style's layers, with its own shade raised from LINZ's subtle setting.
     *
     * The pre-rendered DEM hillshade was tried over the vector map and washed the colours out; the
     * built-in layer at a higher exaggeration is the better answer.
     */
    private fun layersWithStrongerHillshade(root: JsonObject, appFonts: Boolean): JsonArray {
        val layers = (root["layers"] as? JsonArray).orEmpty().toMutableList()

        val shadeIndex = layers.indexOfFirst { element ->
            (element as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull == "hillshade"
        }
        if (shadeIndex >= 0) {
            val shade = layers.removeAt(shadeIndex) as JsonObject
            // LINZ's own colours fade to almost nothing at walking zooms (the highlight is 7% opaque at
            // z11-14), and their interpolate expressions are another thing for the native parser to
            // carry. Plain, stronger colours are what make the shade read on a phone.
            val stronger = JsonObject(
                shade.toMutableMap().apply {
                    put(
                        "paint",
                        buildJsonObject {
                            put("hillshade-exaggeration", 0.85)
                            put("hillshade-illumination-anchor", "map")
                            put("hillshade-illumination-direction", 315)
                            put("hillshade-shadow-color", "rgba(12, 12, 12, 0.65)")
                            put("hillshade-highlight-color", "rgba(255, 255, 255, 0.30)")
                            put("hillshade-accent-color", "rgba(70, 70, 70, 0.40)")
                        }
                    )
                }
            )

            // Under the labels, over the fills: in LINZ's own order the shade sits below the landcover,
            // which is why it was invisible. The first symbol layer is the boundary that keeps the names
            // readable.
            val insertAt = layers.indexOfFirst { element ->
                (element as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "symbol"
            }.let { if (it < 0) layers.size else it }
            layers.add(insertAt, stronger)
        }

        return JsonArray(if (appFonts) layers.map { withAppFonts(it) } else layers)
    }

    /**
     * One layer with its lettering set to the app's own set, which the phone already has.
     *
     * LINZ's style names its own font stacks, and its glyph URL points at LINZ: every range of every
     * stack would have to be downloaded and kept for a name to draw with no reception. The app ships one
     * set and serves it from its own server for every other map it draws, so a name offline costs
     * nothing at all - at the price of LINZ's bold and italic, which become the one weight.
     */
    private fun withAppFonts(layer: JsonElement): JsonElement {
        val styled = layer as? JsonObject ?: return layer
        val layout = styled["layout"] as? JsonObject ?: return styled
        if (!layout.containsKey(TEXT_FONT)) return styled
        return JsonObject(
            styled.toMutableMap().apply {
                put(
                    "layout",
                    JsonObject(
                        layout.toMutableMap().apply {
                            put(TEXT_FONT, buildJsonArray { add(GlyphFonts.STACK) })
                        }
                    )
                )
            }
        )
    }

    /**
     * Fetches whichever of [basemap]'s sprite files are not already kept.
     *
     * Best-effort: a file LINZ does not publish, or a phone with no network, leaves it absent and the
     * app's server answers 404 for it - which costs icons, not the map.
     */
    private fun keepSprite(
        basemap: Basemap,
        root: JsonObject,
        store: HostedStyleStore
    ) {
        val base = root["sprite"]?.jsonPrimitive?.contentOrNull ?: return
        HostedStyleStore.SPRITE_NAMES.forEach { name ->
            if (store.readSprite(basemap, name) != null) return@forEach
            // "sprite.json" asks for `<base>.json`: the "sprite" part of the name is the app's, not
            // LINZ's, and is what the app's own route is addressed by.
            val wanted = base + name.removePrefix(SPRITE_PREFIX)
            readBytes(throughProxy(wanted))?.let { store.writeSprite(basemap, name, it) }
        }
    }

    /**
     * A LINZ asset URL, fetched the way everything else here is.
     *
     * LINZ's style document names its own sprite and its own letters at LINZ's host, so they have to be
     * moved with everything else - to the app's proxy by default, or to LINZ itself with the walker's key
     * when they have asked for that. [LinzAccess.rewrite] is that decision, made once.
     */
    private fun throughProxy(url: String): String = LinzAccess.rewrite(url)

    private fun readText(url: String): String? = readBytes(url)?.decodeToString()

    private fun readBytes(url: String): ByteArray? {
        val connection = runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 20_000
                // The sprite's pictures and its index travel as different things, and LINZ answers a
                // request for one with the other only if it is asked wrongly.
                setRequestProperty("Accept", if (url.endsWith(PNG_SUFFIX)) "image/png" else "application/json")
                setRequestProperty("User-Agent", ArcGisDoc.USER_AGENT)
            }
        }.getOrNull() ?: return null
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** The names LINZ uses in its own style; the layers reference these, so they are kept. */
    private const val VECTOR_SOURCE = "LINZ Basemaps"
    private const val ELEVATION_SOURCE = "LINZ-Elevation-Hillshade"

    /** The layout key a label's lettering is named by. */
    private const val TEXT_FONT = "text-font"

    /** What a sprite file's name starts with, before its own extension. */
    private const val SPRITE_PREFIX = "sprite"

    private const val PNG_SUFFIX = ".png"

    private val Json = Json { ignoreUnknownKeys = true }
}
