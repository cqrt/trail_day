package nz.mckenzie.trailday.offline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import nz.mckenzie.trailday.data.db.OfflineOtherTrackAreaDao
import nz.mckenzie.trailday.data.db.OfflineOtherTrackAreaEntity
import nz.mckenzie.trailday.data.db.OfflineOtherTrackDao
import nz.mckenzie.trailday.data.db.OfflineOtherTrackEntity
import nz.mckenzie.trailday.domain.cache.FactsJson
import nz.mckenzie.trailday.domain.cache.PathsJson
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.domain.other.OtherTrackArea
import nz.mckenzie.trailday.domain.other.OtherTracksCache
import nz.mckenzie.trailday.domain.other.OtherTracksQuery
import nz.mckenzie.trailday.domain.other.joinedByName
import nz.mckenzie.trailday.domain.region.Region
import nz.mckenzie.trailday.domain.region.RegionDistricts
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.other.OtherTracksDownloadResult
import nz.mckenzie.trailday.other.OtherTracksDownloader

/**
 * The national tracks dataset's rows kept on the phone, beside the offline imagery and DOC's material.
 *
 * The same shape as [OfflineDocStore]: a download pulls what the service has for a box, and this records
 * it so the map can draw it with no reception. It is a cache rather than the record of anything -
 * clearing it costs nothing but the download.
 *
 * **Its own store rather than a second half of DOC's**, because the two are different publishers: a
 * walker who clears DOC's material keeps the tracks their council publishes, and the two are counted and
 * cleared apart on the offline screen. Stored globally rather than per area, though: a track is the same
 * track wherever it was downloaded from, so two overlapping areas end with one copy and not two, which
 * is what keying by the service's `GlobalID` is for.
 *
 * **The downloads, on the other hand, are named and listed.** The cache answers "what is on the phone";
 * [areaDao] answers "where did it come from", which is a different question and the one a walker asks when
 * they cannot remember whether the Tararuas were one of the hillsides they kept.
 */
class OfflineOtherTrackStore(
    private val dao: OfflineOtherTrackDao,
    /**
     * The walker's own record of each download, or null where this store is only ever read.
     *
     * The browser's offline fallback reads the cache and never downloads into it, so it has no names to
     * write - and a store that cannot record a download is a smaller thing to hand it than a second way of
     * reaching the database.
     */
    private val areaDao: OfflineOtherTrackAreaDao? = null,
    private val downloader: OtherTracksDownloader = OtherTracksDownloader(),
    private val now: () -> Long = System::currentTimeMillis
) : OtherTracksCache {

    /**
     * Everything held, for the map to draw and the browser to list - **with a walkway the dataset cut into
     * pieces shown as one track**.
     *
     * Joined on the way out as well as on the way in, because a cache filled by two overlapping downloads
     * can hold a joined track and a loose piece of it: the rule only ever merges rows that are the same
     * walkway, so applying it again changes nothing that was already joined.
     */
    override suspend fun cached(): List<OtherTrack> = dao.getAll().mapNotNull { it.toTrack() }.joinedByName()

    override suspend fun count(): Int = dao.count()

    /** A live count, for the offline screen to say what is stored, and for the map to redraw on. */
    override fun observeCount(): Flow<Int> = dao.observeCount()

    /**
     * What the kept tracks cost on disk, in bytes.
     *
     * The card says a size for the reason the imagery's does. A walker deciding whether to download
     * another region is deciding about room on the phone, and a count of tracks is not that.
     */
    suspend fun bytes(): Long = dao.bytes()

    suspend fun clear() {
        dao.deleteAll()
        areaDao?.deleteAll()
    }

    /** The named downloads, newest first, for the offline screen's list. */
    fun observeAreas(): Flow<List<OtherTrackArea>> = areaDao?.observeAll()
        ?.map { areas -> areas.map { it.toArea() } }
        ?: emptyFlow()

    /**
     * When the newest download of this source happened, or null when nothing is held.
     *
     * The licence asks for the moment the data was acquired to be stated, and this is the only place the
     * app knows it.
     */
    override suspend fun newestDownloadAtEpochMs(): Long? = dao.newestDownloadAtEpochMs()
    /**
     * What the service says matches [selection], for the picker to show before anything is downloaded.
     *
     * Null when it could not be counted, which is a different thing from nothing being there: a walker
     * who is told "0" and then finds tracks on the hill was told something untrue.
     */
    suspend fun inventory(selection: OtherTracksQuery): Int? = downloader.count(selection)

    /**
     * Downloads every track [selection] asks for and stores it, under [name].
     *
     * A download that stopped short **stores what arrived**: the next attempt asks again rather than
     * starting from a blank cache, and what did arrive is already usable offline. It is named and listed
     * for the same reason - what came back is what the walker has, and telling them otherwise would be
     * worse than a short list.
     *
     * An area that turned out to hold nothing is not recorded: the walker was told so at the time, and a
     * row reading "0 tracks" is a list of places where there is nothing to walk, which is not the list
     * this is.
     */
    suspend fun download(
        selection: OtherTracksQuery,
        /** What the walker called this box or region: "the Tararuas", "Otago". Named before it gets here. */
        name: String,
        /** How many have been read so far, called after each page. */
        onProgress: (read: Int) -> Unit = {}
    ): OtherTracksDownloadResult {
        val result = downloader.download(selection, onProgress)
        store(result.tracks)
        record(selection.areaBounds, name, result.tracks.size)
        return result
    }

    private suspend fun record(area: LatLngBounds?, name: String, count: Int) {
        val areas = areaDao ?: return
        if (count <= 0 || area == null) return
        val trimmed = name.trim().ifEmpty { UNNAMED_AREA }
        areas.insert(
            OfflineOtherTrackAreaEntity(
                // **The same region downloaded again updates its row** rather than adding a second one: two
                // rows for one region would each claim the same tracks, and deleting one would leave the
                // other describing a region that is no longer on the phone.
                id = areas.find(trimmed)?.id ?: 0L,
                name = trimmed,
                minLat = area.minLat,
                minLng = area.minLng,
                maxLat = area.maxLat,
                maxLng = area.maxLng,
                trackCount = count,
                downloadedAtEpochMs = now()
            )
        )
    }

    /** Whether a download for [name] is already on the phone: downloading it again updates that row. */
    suspend fun hasArea(name: String): Boolean = areaDao?.find(name.trim()) != null

    /**
     * Removes the row only, for an area that predates regions.
     *
     * Such a name is not one of the sixteen, so there is nothing that can be said about the ground it
     * covered. Nothing in this build makes one; this is for rows written by an older one.
     */
    suspend fun forgetArea(id: Long) = areaDao?.delete(id)

    /**
     * Deletes the tracks a region brought, and nothing else - then its row.
     *
     * **By district name, which is exact.** Every row carries one, and the table gives each district to
     * exactly one region, so no two regions can claim the same row: what is deleted here cannot belong to
     * another region's row.
     *
     * A row downloaded before regions existed is deleted too when its district is in the region - it is the
     * same track in the same place, and a row the walker cannot see is one they cannot remove.
     */
    suspend fun deleteRegion(region: Region, areaId: Long): Int {
        val districts = region.districts.map { RegionDistricts.plain(it) }.toSet()
        val doomed = dao.getAll()
            .filter { row ->
                val authority = FactsJson.read(row.factsJson)[LOCAL_AUTHORITY].orEmpty()
                RegionDistricts.plain(authority) in districts
            }
            .map { it.globalId }
        // In chunks, because a region's thousands of identifiers in one statement is more than SQLite
        // will take as parameters: a country's worth of tracks is over its limit, and a delete that threw
        // half way would leave a row saying a region was downloaded when half of it was not.
        doomed.chunked(DELETE_CHUNK).forEach { dao.deleteByIds(it) }
        areaDao?.delete(areaId)
        return doomed.size
    }

    private suspend fun store(tracks: List<OtherTrack>) {
        if (tracks.isEmpty()) return
        dao.insertAll(tracks.map { it.toEntity(now()) })
    }

    private companion object {
        /** What a download nobody named is still called, so the list has no blank rows in it. */
        const val UNNAMED_AREA = "Unnamed area"

        /** The dataset's own field for the district a track is in. */
        const val LOCAL_AUTHORITY = "LocalAuthority"

        /** How many identifiers go into one statement: well under SQLite's own limit on parameters. */
        const val DELETE_CHUNK = 400
    }
}

/**
 * A cache row back into a track.
 *
 * A row whose shape will not read is dropped rather than guessed at: a saved copy is a copy of something
 * still published, and a copy that cannot be read is not worth a screen failing over. See [PathsJson] and
 * [FactsJson].
 */
private fun OfflineOtherTrackEntity.toTrack(): OtherTrack? {
    val paths = PathsJson.read(pathsJson)
    if (paths.isEmpty()) return null
    return OtherTrack(
        globalId = globalId,
        name = name,
        paths = paths,
        facts = FactsJson.read(factsJson)
    )
}

private fun OtherTrack.toEntity(at: Long) = OfflineOtherTrackEntity(
    globalId = globalId,
    name = name,
    lengthM = lengthM,
    pathsJson = PathsJson.write(paths),
    factsJson = FactsJson.write(facts),
    downloadedAtEpochMs = at
)

/** A named download's row back into the walker's own view of it. */
private fun OfflineOtherTrackAreaEntity.toArea() = OtherTrackArea(
    id = id,
    name = name,
    trackCount = trackCount,
    downloadedAtEpochMs = downloadedAtEpochMs
)

/**
 * What is held of somebody else's tracks, in the walker's own terms: "412 tracks", "1 track".
 *
 * One place for the words, so the offline screen's card and the sentence a download earns cannot come to
 * name the same thing differently.
 */
fun otherTracksHeldWords(count: Int): String =
    if (count == 1) "1 track" else "$count tracks"

/**
 * What the card says: **"3 areas - 412 tracks - 9 MB"**, or what is held with no areas on it.
 *
 * A size rather than the names of the regions, because the rows underneath already name every one of
 * them. What those names cannot say is whether another region will fit, and that is what a walker is
 * deciding when they read this line.
 *
 * With tracks held and no areas listed, it says only how many: those were downloaded before areas were
 * recorded, and inventing names for them would be making them up.
 */
fun otherTracksSummary(areaCount: Int, count: Int, sizeLabel: String): String {
    if (areaCount == 0) {
        return if (count == 0) "Nothing downloaded yet" else otherTracksHeldWords(count)
    }
    val boxes = if (areaCount == 1) "1 area" else "$areaCount areas"
    return "$boxes \u00b7 ${otherTracksHeldWords(count)} \u00b7 $sizeLabel"
}

/** One named download in the card: what it is called, and what came back from it. */
fun otherTrackAreaWords(area: OtherTrackArea): String =
    if (area.trackCount == 1) "1 track" else "${area.trackCount} tracks"

/**
 * The sentence a download of somebody else's tracks earns, in the walker's own terms.
 *
 * Three things can be true of it and all three are worth saying: what arrived, that the service stopped
 * answering part-way, and that the box held more than one download keeps. The second is the part a walker
 * would otherwise find out on the hill, where nothing can be fetched; the third is the difference between
 * "that is all of it" and "that is as much as this keeps".
 */
fun otherTracksDownloadedMessage(download: OtherTracksDownloadResult): String {
    val kept = download.tracks.size
    val said = if (kept == 0) {
        "Nothing was in that area."
    } else {
        "Kept ${otherTracksHeldWords(kept)}."
    }

    // The ceiling is the downloader's, and a box that reached it has more behind it. Said rather than
    // silent, because a truncated download that reads as a complete one is the app telling a walker
    // there is no track where there is one.
    if (download is OtherTracksDownloadResult.Done && kept >= OtherTracksDownloader.MAX_TRACKS) {
        return "$said That is as many as one download keeps: narrow the area for the rest."
    }

    return when (download) {
        is OtherTracksDownloadResult.Done -> said
        is OtherTracksDownloadResult.Failed -> "$said ${download.message}"
    }
}
