package nz.mckenzie.trailday.domain.other

import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint

/**
 * One track from Herenga ā Nuku's National Tracks Dataset (Pilot): what the app keeps of a row.
 *
 * **This is not DOC's material and must not be kept as if it were.** The dataset is a merge of
 * sixteen-odd suppliers - district and regional councils, DOC, Te Araroa, Waka Kōtahi, trail trusts and
 * a couple of aggregators - rebuilt monthly, and the app reads only the part of it that is nobody
 * else's: [OtherTracksUrl.where] is where DOC's and Te Araroa's rows are left behind, because the app
 * already carries both from their own publishers.
 *
 * **A row's only identifier is its `GlobalID`.** The dataset publishes an `ID` field too, and it is
 * null on every row that has been looked at; `OBJECTID` is the service's own counter, which is the one
 * thing a monthly rebuild is free to renumber. So the UUID is what the cache keys a row by. Whether it
 * really does survive a rebuild is **unverified** - it needs two months of data to check - which is the
 * second reason [facts] travels whole: a row that comes back under a new id is a row the app cannot
 * recognise, and the app's own "same name, same place, same point" rule is what would catch it.
 */
data class OtherTrack(
    /** The service's UUID for the row. */
    val globalId: String,
    /** What the dataset calls it, or what [OtherTracksJson] names it when the dataset named it nothing. */
    val name: String,
    /**
     * The line, as the paths the service published it in.
     *
     * Kept apart rather than joined, for the reason DOC's tracks are: a service that publishes a track
     * in pieces means it, and joining paths that do not meet draws a line across whatever is between
     * them.
     */
    val paths: List<List<GeoPoint>>,
    /** The row exactly as it arrived, by the service's own field names. Never carries `GlobalID`. */
    val facts: Map<String, String> = emptyMap()
) {

    /** How long the line is, which is what a row and a page say about it. */
    val lengthM: Double get() = AssetGeometry(paths).lengthM
}
