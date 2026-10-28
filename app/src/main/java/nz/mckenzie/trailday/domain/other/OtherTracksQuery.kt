package nz.mckenzie.trailday.domain.other

import java.net.URLEncoder
import java.util.Locale
import nz.mckenzie.trailday.domain.region.Region
import nz.mckenzie.trailday.domain.tiles.LatLngBounds

/**
 * What to ask the national tracks dataset, and the URL that asks it.
 *
 * One service, one layer, one question: a box on the ground, a name if the walker typed one, and which
 * page of the answer is wanted. Unlike DOC's four services there is nothing to choose between, so there
 * is no source field - and unlike DOC there is **no key**, so the phone asks the service directly and no
 * proxy stands in the middle.
 */
data class OtherTracksQuery(
    val nameContains: String = "",
    val bounds: LatLngBounds? = null,
    /**
     * One of New Zealand's regions, when the walker chose a region rather than drawing a box.
     *
     * A region is asked for **by its districts' names**, which is the dataset's own answer to where a track
     * is: see [nz.mckenzie.trailday.domain.region.RegionDistricts]. Asking by shape was measured and
     * rejected - a simplified coastline loses the Hauraki Gulf and its islands, and Auckland's 1,531 tracks
     * came back as 1,276.
     *
     * A region and a [bounds] can both be set, and then both narrow: a region with a box inside it is a
     * smaller question, not a contradiction. The picker sets one.
     */
    val region: Region? = null,
    val limit: Int = DEFAULT_LIMIT,
    val offset: Int = 0
) {

    /**
     * The ground this question covers, for the record a download leaves behind: the box, or the region's
     * own box when the walker chose a region.
     */
    val areaBounds: LatLngBounds? get() = bounds ?: region?.bounds

    companion object {
        /**
         * How many rows one search asks for.
         *
         * A search is for a person to read and three hundred is more than anybody scrolls; the area
         * download is the one that wants everything, and it pages in thousands.
         */
        const val DEFAULT_LIMIT = 300
    }
}

/**
 * The URL that asks the dataset one question.
 *
 * ARC GIS takes the whole question in the query string, so this is a table of parameters in one place.
 * GeoJSON out and WGS84 both ways, as everywhere else in the app: the service reprojects from NZTM for
 * us rather than the app learning a second coordinate system.
 */
object OtherTracksUrl {

    /** The Commission's own ArcGIS host. */
    const val HOST = "https://services2.arcgis.com/b5ADKIcWivL5vNaV/arcgis/rest/services"

    /** The dataset's own folder on it. A **pilot**, so this is the line to change if it moves. */
    const val SERVICE = "National_Tracks_Dataset_Pilot"

    /** The one layer the app reads. */
    const val LAYER = "FeatureServer/0"

    /**
     * The field the app keys a row by.
     *
     * A UUID rather than the dataset's `ID`, which is null on every row looked at, and rather than
     * `OBJECTID`, which a monthly rebuild may renumber - see [OtherTrack].
     */
    const val GLOBAL_ID_FIELD = "GlobalID"

    /** The service's own counter, which is only ever used to page in a stable order. */
    const val OBJECT_ID_FIELD = "OBJECTID"

    /**
     * What the publisher called the track.
     *
     * Named because two things read it rather than one: the row is drawn under this name when it is a
     * name at all, and it is the only thing allowed to join two rows into one walkway - see [joinedByName].
     */
    const val NAME_FIELD = "Name"

    /**
     * What the app asks for: the key, the name, and everything a row and a page have words for.
     *
     * **Named one by one rather than asked for whole.** A row of this dataset carries about twenty
     * fields, several of them provenance the app has no use for (`SourceDataset`, `SourceGeometry`,
     * `SpatialAccuracy`) and two it has decided against showing (horse and off-road access). An area
     * download is thousands of rows, and a field nobody reads is paid for on every one of them.
     */
    val OUT_FIELDS: List<String> = listOf(
        GLOBAL_ID_FIELD,
        NAME_FIELD,
        "Locality",
        "LocalAuthority",
        "DataSupplier",
        "ManagedBy",
        "Walk",
        "Bike",
        "DogAccess",
        "MobilityAccess",
        "ConditionsOfUse",
        "Description",
        "Status",
        "WebLink",
        "ImageLink"
    )

    /** Where the one layer answers. */
    val serviceUrl: String get() = "$HOST/$SERVICE/$LAYER/query"

    /**
     * The rows that are the app's to show, and the name filter when there is one.
     *
     * Three exclusions, each **measured against the live service** rather than assumed:
     *
     *  - **DOC's rows, 7,335 of 25,349.** `%DOC%` looks like the blunt way to do it and is in fact the
     *    exact way: every supplier name the dataset holds that contains "DOC" is `DOC Tracks` or
     *    `DOC MTB Routes` - both measured, and no council's name contains it anywhere - so the clause
     *    cannot catch somebody else's track. A row DOC and a council both publish is *one* row naming
     *    both, and it goes: what was asked for is the tracks that are not DOC's.
     *  - **Removed and decommissioned tracks, 284.** The rest of the status field is junk inherited from
     *    the sources ("Com", "Stratford", " ", "Closed for lambing"), so nothing else in it is filtered
     *    on and a track that is *temporarily* closed still comes through to be read.
     *  - **Tracks nobody may walk on, 1,114.** `Walk = 0` is "Not allowed for Walking or Tramping" - a
     *    bike or 4WD track - and this is a walking app. "Unknown" is 99 rather than 0, so an unrecorded
     *    grade is kept, and `Walk` is never null in the dataset, so no row is dropped by a comparison
     *    against nothing.
     *
     * **Te Araroa is deliberately NOT excluded**, and that is a reversal worth knowing about. It was, on
     * the argument that the app already carries the Trust's own route as a bundled layer; what that cost
     * was every **local** track a council and the Trust both publish - one row naming both, gone - so a
     * resident's own walkway went missing because a national trail happened to run along it. Including
     * them adds **920** rows, and where the two layers are both switched on a trail's line is drawn twice.
     * That is the cheaper mistake: a duplicated line is visible and harmless, and a missing walkway is
     * neither.
     *
     * Together they leave **16,893** of the dataset's 25,349.
     */
    fun where(nameContains: String = "", districts: List<String> = emptyList()): String {
        val name = nameContains.trim()
        val clauses = mutableListOf(EXCLUSIONS)
        // The quote is doubled because a name with an apostrophe in it is a name, not a syntax error -
        // and "Central Hawke's Bay District" is one.
        if (districts.isNotEmpty()) {
            val quoted = districts.joinToString(",") { "'${it.replace("'", "''")}'" }
            clauses += "LocalAuthority IN ($quoted)"
        }
        if (name.isNotEmpty()) clauses += "UPPER(Name) LIKE UPPER('%${name.replace("'", "''")}%')"
        return clauses.joinToString(" AND ")
    }

    /**
     * `Decommisioned` is **the dataset's own spelling**, misspelt in their coded value domain, and it is
     * their value that the clause has to name.
     */
    private const val REMOVED = "Decommisioned"

    /** The three exclusions, in the order they are worth reading. */
    val EXCLUSIONS: String =
        "NOT (DataSupplier LIKE '%DOC%')" +
            " AND Status <> '$REMOVED'" +
            " AND Walk <> 0"

    /**
     * The whole URL for [query].
     *
     * Ordered by the service's own counter so that paging is stable: without an order, two pages of one
     * download can overlap or skip a row, and the cache would hold one track twice and another not at
     * all.
     */
    fun of(query: OtherTracksQuery): String {
        val parameters = linkedMapOf("where" to where(query.nameContains, query.region?.districts.orEmpty()))
        query.bounds?.let { area ->
            parameters["geometry"] = envelope(area)
            parameters["geometryType"] = "esriGeometryEnvelope"
            parameters["inSR"] = "4326"
            parameters["spatialRel"] = "esriSpatialRelIntersects"
        }
        parameters["outFields"] = OUT_FIELDS.joinToString(",")
        parameters["returnGeometry"] = "true"
        parameters["outSR"] = "4326"
        parameters["orderByFields"] = OBJECT_ID_FIELD
        parameters["resultOffset"] = query.offset.coerceAtLeast(0).toString()
        parameters["resultRecordCount"] = query.limit.coerceIn(1, MAX_RECORD_COUNT).toString()
        parameters["f"] = "geojson"
        return "$serviceUrl?${encoded(parameters)}"
    }

    /**
     * The URL that asks **how many** rows match, and nothing else.
     *
     * The same question as [of] with the records left behind, which is what a walker wants before
     * deciding to take an area onto a phone. The box and the name filter are built by the same code, so
     * the count is of exactly what a download would read.
     */
    fun countOf(query: OtherTracksQuery): String {
        val parameters = linkedMapOf("where" to where(query.nameContains, query.region?.districts.orEmpty()))
        query.bounds?.let { area ->
            parameters["geometry"] = envelope(area)
            parameters["geometryType"] = "esriGeometryEnvelope"
            parameters["inSR"] = "4326"
            parameters["spatialRel"] = "esriSpatialRelIntersects"
        }
        parameters["returnCountOnly"] = "true"
        parameters["f"] = "json"
        return "$serviceUrl?${encoded(parameters)}"
    }

    /** The box to ask about, west and south first, which is the order an ARC GIS envelope is written in. */
    fun envelope(bounds: LatLngBounds): String = String.format(
        Locale.US,
        "%.7f,%.7f,%.7f,%.7f",
        bounds.minLng,
        bounds.minLat,
        bounds.maxLng,
        bounds.maxLat
    )

    /**
     * The most rows one request may ask for.
     *
     * The service's own `maxRecordCount`, and asking for more does not get more. **A full page is not
     * the only thing to go by**: this service sets `exceededTransferLimit` on answers of three rows, so
     * the download pages until a page comes back *short*, and never trusts that flag to say whether
     * there is more.
     */
    const val MAX_RECORD_COUNT = 2000

    private fun encoded(parameters: Map<String, String>): String =
        parameters.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
}
