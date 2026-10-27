package nz.mckenzie.trailday.domain.tiles

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan
import nz.mckenzie.trailday.domain.geo.GeoPoint

/**
 * XYZ (Web Mercator) tile maths, used to estimate the cost of downloading an
 * offline region *before* the user commits to it.
 */
object TileMath {

    /** Usable latitude limit of the Web Mercator projection. */
    const val MAX_MERCATOR_LAT: Double = 85.05112878

    /** Typical raster tile payload. LINZ serves WebP, which is smaller than this. */
    const val DEFAULT_TILE_BYTES: Long = 45_000L

    fun lonToTileX(lng: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val x = floor((normaliseLng(lng) + 180.0) / 360.0 * n).toInt()
        return x.coerceIn(0, n - 1)
    }

    fun latToTileY(lat: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val clampedLat = lat.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
        val latRad = Math.toRadians(clampedLat)
        val y = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
        return y.coerceIn(0, n - 1)
    }

    /** Inclusive tile range covering [bounds] at [zoom]. */
    fun tileRange(bounds: LatLngBounds, zoom: Int): TileRange {
        val minX = lonToTileX(bounds.minLng, zoom)
        val maxX = lonToTileX(bounds.maxLng, zoom)
        // Note the Y axis is inverted: north (max lat) maps to the smaller tile Y.
        val minY = latToTileY(bounds.maxLat, zoom)
        val maxY = latToTileY(bounds.minLat, zoom)
        return TileRange(minX, maxX, minY, maxY)
    }

    /** Total tiles needed for [bounds] across an inclusive zoom range. */
    fun tileCount(bounds: LatLngBounds, minZoom: Int, maxZoom: Int): Long {
        if (maxZoom < minZoom) return 0L
        var total = 0L
        for (zoom in minZoom..maxZoom) {
            total += tileRange(bounds, zoom).count
        }
        return total
    }

    /** Rough download size estimate in bytes for an offline region. */
    fun estimateBytes(
        bounds: LatLngBounds,
        minZoom: Int,
        maxZoom: Int,
        averageTileBytes: Long = DEFAULT_TILE_BYTES
    ): Long = tileCount(bounds, minZoom, maxZoom) * averageTileBytes

    private fun normaliseLng(lng: Double): Double {
        var value = lng
        while (value < -180.0) value += 360.0
        while (value > 180.0) value -= 360.0
        return value
    }
}

/** Inclusive tile index range at a single zoom level. */
data class TileRange(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int) {
    val count: Long get() = (maxX - minX + 1).toLong() * (maxY - minY + 1).toLong()
}

/** Geographic bounding box. */
data class LatLngBounds(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double
) {
    init {
        require(minLat <= maxLat) { "minLat must be <= maxLat" }
        require(minLng <= maxLng) { "minLng must be <= maxLng" }
    }
}

/**
 * Half the width of the app's own local view: about half a kilometre each way.
 *
 * One number in one place, because three things mean the same thing by a frame now: the first
 * frame a map opens on around the phone (`tracking/LocationFrame.kt`), the frame the drawing
 * screen opens a track on (`DrawAssetViewModel`), and the smallest frame an asset's own page
 * will show ([withMinimumSpan]).
 */
const val FRAME_HALF_WIDTH_DEGREES = 0.01

/** A degree of latitude, in kilometres. Longitude depends on where you are; see [boxAround]. */
private const val KM_PER_DEGREE = 111.32

/** The floor under the cosine, so a polar box stays a box. */
private const val MIN_COS = 0.01

/**
 * The box to ask a service about, from where the walker is: a square of [radiusKm] around the fix.
 *
 * Longitude degrees are shorter than latitude ones away from the equator, so the box is widened by the
 * cosine of the latitude - and the cosine is floored, because at the pole it is zero and a box of infinite
 * width is not a question anyone can answer.
 *
 * **The box is a service's quick filter, not the answer.** Its corners reach about 1.4x the radius, and a
 * long line that clips it passes right through, so a caller that asked about a *circle* keeps only what is
 * truly inside it - see [nz.mckenzie.trailday.domain.doc.within] and
 * [nz.mckenzie.trailday.domain.other.within].
 *
 * Here rather than beside either publisher's URL builder, because both ask it: DOC's search is a circle
 * around the phone and so is the national tracks dataset's, and two readings of one cosine rule is how one
 * of them comes to be wrong.
 */
fun boxAround(point: GeoPoint, radiusKm: Double): LatLngBounds {
    val latitudeDelta = radiusKm / KM_PER_DEGREE
    val longitudeDelta = radiusKm / (KM_PER_DEGREE * maxOf(cos(Math.toRadians(point.lat)), MIN_COS))
    return LatLngBounds(
        minLat = point.lat - latitudeDelta,
        minLng = point.lng - longitudeDelta,
        maxLat = point.lat + latitudeDelta,
        maxLng = point.lng + longitudeDelta
    )
}

/**
 * The box as a frame a map can actually be asked for: a place is one coordinate, so the box
 * around it has no width and no height.
 *
 * A camera told to fit a box with no size goes to the deepest zoom the map has - measured on
 * the asset page at 25.5, against imagery that stops at 22 - and what the walker is left
 * with is the style's dark green background and the marker on it, instead of their paddock
 * (`build/verify/bug1-camera.txt`). A track drawn between two taps a few metres apart is
 * the same box with the same ending.
 *
 * A box with a real span in **either** direction is returned exactly as it was: a camera can
 * frame that (the zoom is the one that fits the smaller span, so a box with no width but three
 * kilometres of length is fine), and nothing that framed properly before is reframed. Opening
 * a box up only ever shows more around the asset, never less, so this cannot crop one.
 */
fun LatLngBounds.withMinimumSpan(
    minSpanDegrees: Double = FRAME_HALF_WIDTH_DEGREES * 2
): LatLngBounds {
    if (maxLat - minLat >= minSpanDegrees || maxLng - minLng >= minSpanDegrees) return this

    val half = minSpanDegrees / 2.0
    val centreLat = (minLat + maxLat) / 2.0
    val centreLng = (minLng + maxLng) / 2.0
    return LatLngBounds(
        minLat = centreLat - half,
        minLng = centreLng - half,
        maxLat = centreLat + half,
        maxLng = centreLng + half
    )
}
