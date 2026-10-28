package nz.mckenzie.trailday.domain.region

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.tiles.LatLngBounds

/**
 * New Zealand's sixteen regions, with their outlines and their districts.
 *
 * Built once from the bundled boundary file and handed to the offline picker, which is the only screen that
 * asks the question: **a region is a way of choosing ground without drawing a box.** The outlines are used
 * for exactly two things - drawing the shapes and working out which one a finger landed in - while what
 * gets downloaded is decided by [Region.districts], because the dataset has district names and no shapes.
 *
 * A region whose outline would not read, or one the district table does not know, is **dropped rather than
 * guessed at**: an outline with no districts could be tapped and would then download nothing, which reads
 * as the app being broken rather than the region being empty.
 */
class Regions(val all: List<Region>) {

    /** The region with this name, ignoring case, macrons and punctuation. */
    fun byName(name: String): Region? {
        val wanted = RegionDistricts.plain(name)
        return all.firstOrNull { RegionDistricts.plain(it.name) == wanted }
    }

    /** The region a tapped point falls in, or null over the sea. */
    fun at(point: GeoPoint): Region? = all.firstOrNull { it.isOffered && it.holds(point) }

    /**
     * The box that holds every region: **the country, as this picker draws it.**
     *
     * A region picker opens on this rather than on the walker's own street. The regions are the choice, and
     * a map zoomed to one suburb shows none of them.
     */
    val bounds: LatLngBounds? get() {
        val boxes = all.mapNotNull { it.bounds }
        if (boxes.isEmpty()) return null
        return LatLngBounds(
            minLat = boxes.minOf { it.minLat },
            minLng = boxes.minOf { it.minLng },
            maxLat = boxes.maxOf { it.maxLat },
            maxLng = boxes.maxOf { it.maxLng }
        )
    }

    companion object {

        /** What a picker with no boundary file has: no regions, and a screen that says so. */
        val EMPTY = Regions(emptyList())

        private val json = Json { ignoreUnknownKeys = true }

        private val serializer = ListSerializer(Boundary.serializer())

        /**
         * The bundled outlines as regions.
         *
         * **The file is GeoJSON's own order - longitude first, then latitude** - because it is a copy of
         * what Stats NZ publishes. Everywhere else in the app a point is latitude first, so the swap is
         * made here and nowhere else.
         *
         * A file that will not read gives [EMPTY] rather than an exception: a picker with no regions is a
         * screen saying so, and a walker can still draw a box.
         */
        fun parse(text: String): Regions = runCatching {
            val boundaries = json.decodeFromString(serializer, text)
            Regions(
                boundaries.mapNotNull { boundary ->
                    val rings = boundary.rings.mapNotNull { ring ->
                        val points = ring.mapNotNull { pair ->
                            if (pair.size >= 2) GeoPoint(lat = pair[1], lng = pair[0]) else null
                        }
                        points.takeIf { it.size >= 3 }
                    }
                    val districts = RegionDistricts.of(boundary.name)
                    if (rings.isEmpty() || districts.isEmpty()) {
                        null
                    } else {
                        Region(name = boundary.name, districts = districts, rings = rings)
                    }
                }.sortedBy { it.name }
            )
        }.getOrDefault(EMPTY)

        /** One region as the boundary file carries it: a name and its rings of `[lng, lat]` pairs. */
        @Serializable
        private data class Boundary(val name: String, val rings: List<List<List<Double>>>)
    }
}
