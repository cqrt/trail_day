package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.geo.GeoPoint
import java.util.Locale

/**
 * The one dot a screen puts on its map while a finger is on its height chart.
 *
 * **A fraction of a line is not a place**, so the chart's finger is turned into the ground it points at
 * here rather than in a style expression: the dot carries a coordinate and the layer draws it, exactly as
 * [PositionGeoJson] does for the phone. Its own source, so running a finger along a graph rebuilds one
 * feature rather than the whole map, and its own layer, so it draws over everything and goes away without
 * anything else being restyled.
 *
 * Hand-rolled for the reason the other documents are: one feature, and a JVM test to hold it.
 */
object FingerGeoJson {

    /** An empty collection: no finger on any chart, which is most of the time. */
    const val EMPTY = "{\"type\":\"FeatureCollection\",\"features\":[]}"

    fun build(point: GeoPoint?): String {
        if (point == null) return EMPTY
        return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\"," +
            "\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[" +
            String.format(Locale.US, "%.7f", point.lng) + "," +
            String.format(Locale.US, "%.7f", point.lat) + "]}}]}"
    }
}
