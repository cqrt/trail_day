package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.PlaceLabels
import nz.mckenzie.trailday.domain.tiles.Roads

/**
 * The MapLibre style document for a basemap.
 *
 * One raster source and one raster layer, built here rather than loaded from the provider's own
 * hosted style, for three reasons that all still hold: the tile URL can be swapped for the app's
 * tile server, so a downloaded area works with no reception; the key - where a source needs one -
 * is injected at runtime; and what is in the document is exactly what the app draws, rather than
 * whatever a hosted style happens to declare today.
 *
 * The credit the licence requires goes in with it, in the source's own attribution, so the map
 * itself carries it; the screens also draw an always-visible strip, because a credit behind a
 * tap is a credit that is not visible.
 */
object BasemapStyles {

    /**
     * The style for [basemap], pointing at [tileUrlTemplate].
     *
     * [tileUrlTemplate] is either the app's own tile server or the provider's own URL - the map
     * cannot tell the difference, which is the point of the server existing.
     *
     * [glyphsUrl] is where the letters for a track's name come from, or null when there is nowhere to
     * get them from. The letters travel inside the app and are handed out by the app's own server, so a
     * style built to talk to a provider directly has none: it writes no names, which is honest rather
     * than broken, because there is nowhere to get the letters from.
     *
     * [placeNamesUrl] is where LINZ's place-name tiles come from - see [PlaceLabels] - or null when the
     * walker has not asked for them. **Both** are needed: names are drawn where there is a server to
     * fetch the tiles from and letters to write them with, so a style pointed straight at a provider
     * draws no names at all rather than drawing boxes.
     *
     * [roadsUrl] is the same tile address again, or null when the walker has not asked for roads. It
     * is the same tileset as the names - see [Roads] - so the two share **one** source: a walker with
     * both on fetches each tile once, not twice, and either one alone brings the source with it.
     */
    fun rasterStyleJson(
        basemap: Basemap,
        tileUrlTemplate: String,
        glyphsUrl: String? = null,
        placeNamesUrl: String? = null,
        roadsUrl: String? = null,
        /** The terrain-rgb elevation, when the basemap draws a LiDAR hillshade. */
        elevationUrlTemplate: String? = null
    ): String {
        val names = if (placeNamesUrl != null && glyphsUrl != null) placeNamesUrl else null
        val road = if (roadsUrl != null && glyphsUrl != null) roadsUrl else null
        val vectors = names ?: road

        return """
            {
              "version": 8,
              "name": "Trail Day - ${basemap.displayName}",
              ${glyphsUrl?.let { "\"glyphs\": \"$it\"," } ?: ""}
              "sources": {
                "${basemap.id}": {
                  "type": "raster",
                  "tiles": ["$tileUrlTemplate"],
                  "tileSize": 256,
                  "minzoom": 0,
                  "maxzoom": ${basemap.maxZoom},
                  "attribution": "${basemap.attributionHtml()}"
                }${vectors?.let { ",\n" + placeLabelsSource(it) }.orEmpty()}${
            elevationUrlTemplate?.let { ",\n" + elevationSource(it, basemap) }.orEmpty()
        }
              },
              "layers": [
                {
                  "id": "background",
                  "type": "background",
                  "paint": { "background-color": "#0B1F13" }
                },
                {
                  "id": "${basemap.id}",
                  "type": "raster",
                  "source": "${basemap.id}",
                  "minzoom": 0,
                  "maxzoom": ${basemap.maxZoom}
                }${
            elevationUrlTemplate?.let { ",\n" + hillshadeLayer(basemap) }.orEmpty()
        }${road?.let { ",\n" + roadsLayers() }.orEmpty()}${names?.let { ",\n" + labelsLayers() }.orEmpty()}
              ]
            }
        """.trimIndent()
    }

    /** The terrain-rgb source the LiDAR shade is drawn from, as a style entry. */
    private fun elevationSource(url: String, basemap: Basemap): String = """
        "${Basemap.ELEVATION_SOURCE_ID}": {
          "type": "raster-dem",
          "tiles": ["$url"],
          "tileSize": 256,
          "minzoom": 0,
          "maxzoom": ${basemap.maxZoom},
          "attribution": "${basemap.attributionHtml()}"
        }
    """.trimIndent()

    /** The shade itself, over the Topo raster and under any names. */
    private fun hillshadeLayer(basemap: Basemap): String = """
        {
          "id": "hillshade",
          "type": "hillshade",
          "source": "${Basemap.ELEVATION_SOURCE_ID}",
          "minzoom": 0,
          "maxzoom": ${basemap.maxZoom},
          "paint": {
            "hillshade-exaggeration": 0.4,
            "hillshade-illumination-anchor": "map",
            "hillshade-illumination-direction": 315,
            "hillshade-shadow-color": "#0c0c0c",
            "hillshade-highlight-color": "#e1e5e0",
            "hillshade-accent-color": "#646464"
          }
        }
    """.trimIndent()

    /**
     * The place names' own source, as a style entry.
     *
     * A vector source rather than a raster one: LINZ publishes these as tiles of features, and the
     * app draws the one layer of them that carries a name. That is also what makes them worth having
     * over a picture of somebody else's map - the name is text the map writes, so it is written in the
     * same letters, at the same weight and in the same colour on both maps, and it can be switched off
     * without switching the imagery off with it.
     */
    private fun placeLabelsSource(url: String): String = """
        "${PlaceLabels.ID}": {
          "type": "vector",
          "tiles": ["$url"],
          "minzoom": 0,
          "maxzoom": ${PlaceLabels.MAX_ZOOM},
          "attribution": "${PlaceLabels.ATTRIBUTION}"
        }
    """.trimIndent()

    /**
     * Every band of names, as style layers - see [PlaceLabels.BANDS].
     *
     * One layer per band because what changes between them is the zoom they appear at, which a
     * MapLibre layer says and a filter cannot.
     */
    private fun labelsLayers(): String =
        PlaceLabels.BANDS.joinToString(",\n") { band -> placeLabelLayer(band) }

    /**
     * One band of place names, as a style layer.
     *
     * Two things are the map's rather than the app's, and deliberately: a name that will not fit
     * without touching another is **dropped** rather than drawn on top of it (the opposite of how the
     * app treats its own track names, which are never allowed to vanish) - because a place name is
     * furniture the walker did not ask for by name, and a screen of overlapping town names is worse
     * than a town name that is not there yet. Everything else - the size, when it appears, the colours,
     * the outline - comes from [PlaceLabels], so both maps write a town the same way.
     */
    private fun placeLabelLayer(band: PlaceLabels.Band): String = """
        {
          "id": "${band.id}",
          "type": "symbol",
          "source": "${PlaceLabels.ID}",
          "source-layer": "${band.sourceLayer}",
          "minzoom": ${band.minZoom},
          "filter": ["in", ["get", "${band.property}"], ["literal", [${
        band.kinds.joinToString(", ") { "\"$it\"" }
    }]]],
          "layout": {
            "text-field": ["get", "${band.nameProperty}"],
            "text-font": ["${GlyphFonts.STACK}"],
            "text-size": ["interpolate", ["linear"], ["zoom"], ${band.minZoom}, ${
        band.sizeAtMinZoom
    }, ${PlaceLabels.MAX_ZOOM}, ${band.sizeAtMaxZoom}],${
        if (band.placement != PlaceLabels.Placement.LINE) "" else """

            "symbol-placement": "line",
            "text-max-angle": ${PlaceLabels.LINE_MAX_ANGLE},"""
    }
            "text-allow-overlap": false,
            "text-padding": 4
          },
          "paint": {
            "text-color": "${PlaceLabels.COLOUR}",
            "text-halo-color": "${PlaceLabels.HALO_COLOUR}",
            "text-halo-width": ${PlaceLabels.HALO_WIDTH}
          }
        }
    """.trimIndent()

    /**
     * The roads, as style layers - see [Roads].
     *
     * **Every casing first, then every line**, which is what makes one road pass under another
     * cleanly: a highway's edge is drawn, then a street's edge, and only then the gold of both. Drawn
     * one road at a time, a street would cut a hole in the highway it meets.
     */
    private fun roadsLayers(): String {
        val casings = Roads.LINES.filter { it.casing }
        val filled = Roads.LINES
        return (casings.map { roadLineLayer(it, casing = true) } +
            filled.map { roadLineLayer(it, casing = false) } +
            roadNameLayer()).joinToString(",\n")
    }

    /**
     * One line of roads, as a style layer: the casing under it, or the line itself.
     *
     * A dashed line's casing is not dashed. The dash is the road's condition - sealed or not - and an
     * edge that broke with it would read as two roads rather than one road in two states.
     */
    private fun roadLineLayer(line: Roads.Line, casing: Boolean): String {
        val width = if (casing) line.width.map { (zoom, px) -> "$zoom, ${px + Roads.CASING_WIDTH}" }
        else line.width.map { (zoom, px) -> "$zoom, $px" }
        val dash = if (!casing && line.dash != null) """
            "line-dasharray": [${line.dash.joinToString(", ")}],""" else ""
        return """
        {
          "id": "${line.id}${if (casing) "-casing" else ""}",
          "type": "line",
          "source": "${PlaceLabels.ID}",
          "source-layer": "${Roads.SOURCE_LAYER}",
          "minzoom": ${if (casing) line.minZoom else line.minZoom},
          "filter": ${line.filter},
          "layout": {
            "line-cap": "round",
            "line-join": "round"
          },
          "paint": {
            "line-color": "${if (casing) Roads.CASING_COLOUR else line.colour}",
            "line-opacity": ${if (casing) Roads.CASING_OPACITY else line.opacity},
            "line-width": ["interpolate", ["linear"], ["zoom"], ${
            width.joinToString(", ")
        }],$dash
            "line-blur": 0
          }
        }
        """.trimIndent()
    }

    /**
     * A road's name, written along it - see [Roads.NAME_PROPERTY].
     *
     * Along the line rather than at a point, because a road is a line and its name belongs on it;
     * where a section is too short or too crooked for the name, the map simply does not write it,
     * which is what the engine's placement does for free and what a name laid flat could not.
     */
    private fun roadNameLayer(): String = """
        {
          "id": "roads-name",
          "type": "symbol",
          "source": "${PlaceLabels.ID}",
          "source-layer": "${Roads.SOURCE_LAYER}",
          "minzoom": ${Roads.NAME_MIN_ZOOM},
          "filter": ${Roads.NAME_FILTER},
          "layout": {
            "text-field": ["get", "${Roads.NAME_PROPERTY}"],
            "text-font": ["${GlyphFonts.STACK}"],
            "text-size": ["interpolate", ["linear"], ["zoom"], ${Roads.NAME_MIN_ZOOM}, ${
        Roads.NAME_SIZE_AT_MIN_ZOOM
    }, ${Roads.MAX_ZOOM}, ${Roads.NAME_SIZE_AT_MAX_ZOOM}],
            "symbol-placement": "line",
            "text-max-angle": ${Roads.NAME_MAX_ANGLE},
            "text-allow-overlap": false,
            "text-padding": 4
          },
          "paint": {
            "text-color": "${Roads.NAME_COLOUR}",
            "text-halo-color": "${Roads.NAME_HALO_COLOUR}",
            "text-halo-width": ${Roads.NAME_HALO_WIDTH}
          }
        }
    """.trimIndent()

    /**
     * A style with no network sources at all, used while a basemap that needs a key has none -
     * so the app shows a calm placeholder instead of firing thousands of tiles that will come
     * back HTTP 400.
     *
     * OpenStreetMap never needs this: it has no key to be missing, and its tiles are fetched by
     * the app's tile server whether or not a LINZ key exists.
     */
    fun blankStyleJson(): String = """
        {
          "version": 8,
          "name": "Trail Day - no basemap key",
          "layers": [
            {
              "id": "background",
              "type": "background",
              "paint": { "background-color": "#10241A" }
            }
          ]
        }
    """.trimIndent()
}
