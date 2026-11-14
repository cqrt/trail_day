package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.geo.GeoPoint
import java.util.Locale

/**
 * Where a trip began and where it stopped: **two points, and nothing else**.
 *
 * An activity's own page draws its line and these two marks, so the walker can see at a glance which
 * end of the day the first fix is on and which the last. No words are added to the page's sheet and no
 * other map gets them: an end is a fact about *this* trip, and a dot on the map tab would be the app
 * marking a walker's last visit rather than their walk.
 *
 * Two features rather than two sources, with a `part` on each in the idiom of [PositionGeoJson]'s dot
 * and ring: one source is one hand-over, and one layer per part is what lets a dot and a flag be drawn
 * as the different things they are.
 *
 * The list is the **whole trip rather than a stretch**, so a break does not move an end - what the trip
 * stopped for is part of the trip. A loop, or a walk that finishes a few metres from where it started,
 * is two features at one coordinate: the flag stands on the dot, and that is the truth about the day
 * rather than something to special-case here.
 *
 * Hand-rolled for the reason the other documents are: the payload is two points, and keeping it pure
 * Kotlin means a fast JVM test holds it.
 */
object EndsGeoJson {

    /** An empty collection: no trip's page, and every map that is not one. */
    const val EMPTY = "{\"type\":\"FeatureCollection\",\"features\":[]}"

    /** The property the two layers filter on, in the same idiom as the assets' kind and shape. */
    internal const val PART_PROPERTY = "part"

    /** The dot at the first fix. */
    internal const val PART_START = "start"

    /** The flag at the last. */
    internal const val PART_FINISH = "finish"

    /**
     * The two ends of [points], start first.
     *
     * **Fewer than two points is empty.** One fix has no two ends to tell apart, and none has nothing
     * at all; a page whose trip has one fix is a page with a line of one point and no claim about
     * where it went.
     */
    fun build(points: List<GeoPoint>): String {
        if (points.size < 2) return EMPTY

        val builder = StringBuilder(180)
        builder.append("{\"type\":\"FeatureCollection\",\"features\":[")
        appendFeature(builder, PART_START, points.first())
        builder.append(',')
        appendFeature(builder, PART_FINISH, points.last())
        builder.append("]}")
        return builder.toString()
    }

    private fun appendFeature(builder: StringBuilder, part: String, point: GeoPoint) {
        builder.append("{\"type\":\"Feature\",\"properties\":{\"")
            .append(PART_PROPERTY).append("\":\"").append(part).append("\"},")
            .append("\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
            // GeoJSON is [longitude, latitude] - the opposite of how humans say it.
            .append(format(point.lng)).append(',')
            .append(format(point.lat))
            .append("]}}")
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.7f", value)
}
