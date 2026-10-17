package nz.mckenzie.trailday.domain.doc

import java.net.URLEncoder
import java.util.Locale
import nz.mckenzie.trailday.domain.geo.GeoPoint

/**
 * What to ask DOC, and which of its services to ask.
 *
 * The source is part of the question rather than something the caller keeps beside it: a query with no
 * source in it is a query somebody can send to the wrong service, and the services disagree about
 * almost everything - the name field, the fields they carry, and whether a row is a line or a place.
 */
data class DocQuery(
    val source: DocSource,
    val nameContains: String = "",
    val near: GeoPoint? = null,
    val radiusKm: Double = 25.0,
    val bounds: DocBounds? = null,
    val limit: Int = DEFAULT_LIMIT,
    val offset: Int = 0
) {
    companion object {
        /**
         * How many rows one search asks for.
         *
         * A search is for a person to read, and three hundred rows is more than anybody scrolls; the
         * offline download is the one that wants everything, and it pages in thousands.
         */
        const val DEFAULT_LIMIT = 300
    }
}

/** A box on the ground, corners first: the area a search is asked about, or a download covers. */
data class DocBounds(val minLat: Double, val minLng: Double, val maxLat: Double, val maxLng: Double)

/**
 * The URL that asks one of DOC's services one question.
 *
 * ARC GIS takes the whole question in the query string, so this is a table of parameters in one place:
 * the source decides where it goes and which fields come back, and the query decides what is filtered,
 * where, and how much.
 */
object DocUrl {

    /**
     * The whole URL for [query], against the source it names.
     *
     * GeoJSON out and WGS84 both ways, because that is what the app already reads everywhere else: the
     * service reprojects from NZTM for us rather than the app learning a second coordinate system.
     */
    fun of(query: DocQuery): String {
        val source = query.source
        val parameters = linkedMapOf("where" to where(source, query.nameContains))

        val envelope = when {
            query.bounds != null -> envelope(query.bounds)
            query.near != null -> envelope(query.near, query.radiusKm)
            else -> null
        }
        if (envelope != null) {
            parameters["geometry"] = envelope
            parameters["geometryType"] = "esriGeometryEnvelope"
            parameters["inSR"] = "4326"
            parameters["spatialRel"] = "esriSpatialRelIntersects"
        }

        parameters["outFields"] = source.outFields
        parameters["returnGeometry"] = "true"
        parameters["outSR"] = "4326"
        // Ordered by DOC's own number so paging is stable: without it, two pages of the same search can
        // overlap or skip a row, and the download would cache one thing twice and another not at all.
        parameters["orderByFields"] = DocSource.OBJECT_ID_FIELD
        parameters["resultOffset"] = query.offset.coerceAtLeast(0).toString()
        parameters["resultRecordCount"] =
            query.limit.coerceIn(1, MAX_RECORD_COUNT).toString()
        parameters["f"] = "geojson"

        val queryString = parameters.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
        return "${source.serviceUrl}?$queryString"
    }

    /**
     * The URL that asks one of DOC's services **how many** records match, and nothing else.
     *
     * The same question as [of] with the records left behind: `returnCountOnly` answers with a number
     * rather than the records, which is what a walker wants before deciding whether to take a whole area
     * onto a phone. The box and the name filter are built by the same code as a search's, so the count is
     * of exactly what a download would read - a count of something else would be worse than no count.
     *
     * [where] is overridable because one question is not about all of a service's records but about those
     * with something: what DOC addresses a picture for, which is what a download spends most of its time
     * on.
     */
    fun countOf(query: DocQuery, where: String = where(query.source, query.nameContains)): String {
        val parameters = linkedMapOf("where" to where)
        val envelope = when {
            query.bounds != null -> envelope(query.bounds)
            query.near != null -> envelope(query.near, query.radiusKm)
            else -> null
        }
        if (envelope != null) {
            parameters["geometry"] = envelope
            parameters["geometryType"] = "esriGeometryEnvelope"
            parameters["inSR"] = "4326"
            parameters["spatialRel"] = "esriSpatialRelIntersects"
        }
        parameters["returnCountOnly"] = "true"
        parameters["f"] = "json"

        val queryString = parameters.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
        return "${query.source.serviceUrl}?$queryString"
    }

    /**
     * What to filter on: the name, in whichever field **this** service keeps it.
     *
     * A hut's name is in `name` and a track's is in `TechObjectName`, so a `where` clause written once
     * for both would search the wrong field on three of the four services - and a search that quietly
     * finds nothing looks exactly like a place DOC has never heard of.
     *
     * The quote is doubled because a name with an apostrophe in it is a name, not a syntax error, and
     * DOC's own names have them ("Māori Gully", "O'Malley's").
     */
    fun where(source: DocSource, nameContains: String): String {
        val name = nameContains.trim()
        if (name.isEmpty()) return "1=1"
        val field = source.nameField
        return "UPPER($field) LIKE UPPER('%${name.replace("'", "''")}%')"
    }

    /**
     * The box to ask about, from where the walker is: a square of [radiusKm] around the fix.
     *
     * Longitude degrees are shorter than latitude ones away from the equator, so the box is widened by
     * the cosine of the latitude - and the cosine is floored, because at the pole it is zero and a box
     * of infinite width is not a question anyone can answer.
     */
    fun envelope(near: GeoPoint, radiusKm: Double): String {
        val latitudeDelta = radiusKm / KM_PER_DEGREE
        val longitudeDelta = radiusKm / (KM_PER_DEGREE * maxOf(kotlin.math.cos(Math.toRadians(near.lat)), MIN_COS))
        return box(
            minLng = near.lng - longitudeDelta,
            minLat = near.lat - latitudeDelta,
            maxLng = near.lng + longitudeDelta,
            maxLat = near.lat + latitudeDelta
        )
    }

    /** The box to ask about, as the map is showing it. */
    fun envelope(bounds: DocBounds): String = box(
        minLng = bounds.minLng,
        minLat = bounds.minLat,
        maxLng = bounds.maxLng,
        maxLat = bounds.maxLat
    )

    private fun box(minLng: Double, minLat: Double, maxLng: Double, maxLat: Double): String =
        String.format(
            Locale.US,
            "%.7f,%.7f,%.7f,%.7f",
            minLng,
            minLat,
            maxLng,
            maxLat
        )

    /** A degree of latitude, in kilometres. Longitude depends on where you are; see [envelope]. */
    private const val KM_PER_DEGREE = 111.32

    /** The floor under the cosine, so a polar box stays a box. */
    private const val MIN_COS = 0.01

    /**
     * The most rows one request may ask for.
     *
     * DOC's own limit, and asking for more does not get more: the service answers with this many and no
     * complaint. The download pages around it.
     */
    const val MAX_RECORD_COUNT = 2000
}
