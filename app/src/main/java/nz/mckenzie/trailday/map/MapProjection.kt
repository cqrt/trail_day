package nz.mckenzie.trailday.map

import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * The way between the glass and the ground.
 *
 * Handed to a screen that has to put something *on* a place - the handles on the corners of a box being
 * chosen for download - so it can ask where a place lands, and which place a finger is on, without being
 * given the whole map to reach into. Two questions and no others, which is what keeps this from becoming
 * a second way to drive the map.
 *
 * The answers are in pixels of the map view, which is the same glass a Compose overlay is drawn on: both
 * fill the same box, so a point from here can be used there without arithmetic.
 */
class MapProjection internal constructor(private val map: MapLibreMap) {

    /** Where [point] lands on the glass, in pixels. */
    fun toScreen(point: GeoPoint): Offset {
        val at = map.projection.toScreenLocation(LatLng(point.lat, point.lng))
        return Offset(at.x, at.y)
    }

    /** The place under [at] on the glass. */
    fun toGround(at: Offset): GeoPoint {
        val place = map.projection.fromScreenLocation(PointF(at.x, at.y))
        return GeoPoint(place.latitude, place.longitude)
    }
}
