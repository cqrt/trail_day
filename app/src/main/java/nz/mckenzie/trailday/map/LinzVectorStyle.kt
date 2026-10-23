package nz.mckenzie.trailday.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import java.net.HttpURLConnection
import java.net.URL

/**
 * LINZ's hosted vector Topographic style, adapted for the app.
 *
 * **Why it is fetched rather than loaded by URL.** LINZ's style document carries a `sky` block that
 * the bundled renderer may refuse, and its glyph and sprite URLs have no key. Loading it straight
 * into MapLibre is the path that took the app down; fetching it first lets the app drop the sky,
 * add the key to the fonts and sprites, and point the two sources at its own tile server.
 *
 * The two sources are kept, and named as LINZ names them, because the style's 287 layers reference
 * them by name: the vector Topographic tiles, and the terrain-rgb elevation the `hillshade` layer
 * is drawn from.
 */
object LinzVectorStyle {

    /**
     * The adapted style for [basemap], or null when LINZ cannot be read.
     *
     * [vectorTileTemplate] and [elevationTileTemplate] are the app's own tile-server templates when
     * it is running; null falls back to LINZ directly, with the key in each URL.
     */
    suspend fun load(
        apiKey: String,
        basemap: Basemap,
        vectorTileTemplate: String?,
        elevationTileTemplate: String?,
        demHillshadeTileTemplate: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val url = basemap.hostedStyleUrl(apiKey) ?: return@withContext null
        val body = read(url) ?: return@withContext null
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return@withContext null

        val sources = buildJsonObject {
            put(
                VECTOR_SOURCE,
                buildJsonObject {
                    put("type", "vector")
                    put(
                        "tiles",
                        buildJsonArray { add(vectorTileTemplate ?: basemap.tileTemplate(apiKey)) }
                    )
                    put("minzoom", 0)
                    put("maxzoom", basemap.maxZoom)
                }
            )
            put(
                ELEVATION_SOURCE,
                buildJsonObject {
                    put("type", "raster-dem")
                    put(
                        "tiles",
                        buildJsonArray { add(elevationTileTemplate ?: Basemap.elevationTemplate(apiKey)) }
                    )
                    put("tileSize", 256)
                    put("minzoom", 0)
                    put("maxzoom", basemap.maxZoom)
                }
            )
            // LINZ's own pre-rendered DEM hillshade, drawn over the vector map because the style's
            // own shade is subtle enough to be missed.
            put(
                Basemap.DEM_HILLSHADE_SOURCE_ID,
                buildJsonObject {
                    put("type", "raster")
                    put(
                        "tiles",
                        buildJsonArray {
                            add(demHillshadeTileTemplate ?: Basemap.demHillshadeTemplate(apiKey))
                        }
                    )
                    put("tileSize", 256)
                    put("minzoom", 0)
                    put("maxzoom", basemap.maxZoom)
                }
            )
        }

        val cleaned = JsonObject(
            root.toMutableMap().apply {
                // The renderer has no sky, and a style it will not read is a blank rectangle.
                remove("sky")
                put("sources", sources)
                put("layers", layersWithDemHillshade(root, basemap))
                root["glyphs"]?.jsonPrimitive?.contentOrNull?.let { put("glyphs", withKey(it, apiKey)) }
                root["sprite"]?.jsonPrimitive?.contentOrNull?.let { put("sprite", withKey(it, apiKey)) }
            }
        )
        cleaned.toString()
    }

    /**
     * The style's layers with its own shade strengthened and the DEM hillshade inserted under the
     * labels.
     *
     * The insert point is the first `symbol` layer: the hillshade has to be over the vector fills
     * and lines, or it is hidden, and under the text, or it washes the names out.
     */
    private fun layersWithDemHillshade(root: JsonObject, basemap: Basemap): JsonArray {
        val original = (root["layers"] as? JsonArray).orEmpty()
        val strengthened = original.map { element ->
            val layer = element as? JsonObject ?: return@map element
            if (layer["id"]?.jsonPrimitive?.contentOrNull != "hillshade") return@map element
            val paint = (layer["paint"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            paint["hillshade-exaggeration"] = JsonPrimitive(0.8)
            JsonObject(layer.toMutableMap().apply { put("paint", JsonObject(paint)) })
        }
        val insertAt = strengthened.indexOfFirst { element ->
            (element as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "symbol"
        }.let { if (it < 0) strengthened.size else it }

        val hillshade = buildJsonObject {
            put("id", "nz-dem-hillshade")
            put("type", "raster")
            put("source", Basemap.DEM_HILLSHADE_SOURCE_ID)
            put("minzoom", 0)
            put("maxzoom", basemap.maxZoom)
            put("paint", buildJsonObject { put("raster-opacity", 0.55) })
        }
        return JsonArray(strengthened.toMutableList().apply { add(insertAt, hillshade) })
    }

    /** A LINZ asset URL with the key added, whichever separator it already needs. */
    private fun withKey(url: String, apiKey: String): JsonPrimitive {
        val separator = if (url.contains('?')) '&' else '?'
        return JsonPrimitive("$url$separator" + "api=${Basemap.sanitiseKey(apiKey)}")
    }

    private fun read(url: String): String? {
        val connection = runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", ArcGisDoc.USER_AGENT)
            }
        }.getOrNull() ?: return null
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { it.readBytes().decodeToString() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** The names LINZ uses in its own style; the layers reference these, so they are kept. */
    private const val VECTOR_SOURCE = "LINZ Basemaps"
    private const val ELEVATION_SOURCE = "LINZ-Elevation-Hillshade"

    private val Json = Json { ignoreUnknownKeys = true }
}
