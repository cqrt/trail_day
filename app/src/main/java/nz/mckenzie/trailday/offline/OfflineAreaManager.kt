package nz.mckenzie.trailday.offline

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nz.mckenzie.trailday.data.db.OfflineAreaDao
import nz.mckenzie.trailday.data.db.OfflineAreaEntity
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.LatLngBounds

/**
 * A downloaded offline area, as the screen shows it: **one named box a walker chose, with a size and a
 * state.**
 *
 * [storedTiles] counts the plan's tiles that are on disk, which is not the same
 * as "tiles this download fetched": tiles browsed on the map beforehand count
 * too, and a resumed download starts from what is already there. [missingTiles]
 * is therefore the honest answer to "what is not covered", whether because LINZ
 * has no imagery for those tiles or because they failed to download.
 */
data class OfflineArea(
    val id: Long,
    val name: String,
    /** The box the walker chose on the map, so the list can show where the area is. */
    val bounds: LatLngBounds,
    val plannedTiles: Int,
    val storedTiles: Int,
    val bytes: Long,
    val isComplete: Boolean,
    /** Which pack's tiles it is, for a screen that shows more than one list. */
    val pack: OfflinePack = OfflinePack.IMAGERY,
    /** Why the last attempt stopped early, if it did. Only real failures. */
    val lastError: String? = null
) {
    /**
     * 0-100. Reports 100 only once the download has finished: an interrupted area
     * must not look done. An area that is *finished* but short of tiles LINZ has
     * no imagery for does report 100 - there is nothing left to wait for, and the
     * shortfall is reported through [missingTiles] rather than by a progress bar
     * that would sit at 99% for ever.
     */
    val percent: Int
        get() = when {
            plannedTiles <= 0 -> 0
            isComplete -> 100
            else -> ((storedTiles * 100L) / plannedTiles).toInt().coerceIn(0, 99)
        }

    val sizeLabel: String get() = formatBytes(bytes)

    val missingTiles: Int get() = (plannedTiles - storedTiles).coerceAtLeast(0)

    /** Complete, but with tiles LINZ had no imagery for. Worth saying out loud. */
    val isShortButComplete: Boolean get() = isComplete && missingTiles > 0
}

/** What is actually on the device, for the screen's summary line. */
data class TileStoreSummary(val tiles: Long, val bytes: Long) {
    val sizeLabel: String get() = formatBytes(bytes)
}

/**
 * One pass of a pack's download: **where its tiles go, and where they come from.**
 *
 * A pack with one source has one pass; the drawn map has two - its tiles, then the heights its hillshade is
 * drawn from - which are the same z/x/y fetched from two places into two stores. Saying it as a list is what
 * lets one manager download either pack, and what gives the live sentence its "the map" and "the heights".
 */
data class PackPass(
    val store: OfflineTileStore,
    /** What this pass is, in the walker's words, when a pack has more than one. */
    val phase: String? = null,
    /** Built per download rather than held, so a download always starts from a fresh client. */
    val fetcherFor: () -> TileFetcher
)

/**
 * Downloads and records offline areas **for one pack.**
 *
 * Tiles come straight from LINZ into [OfflineTileStore] - the same store the
 * map's own tile server reads - so a downloaded area is usable the moment it
 * lands, with no export or import step. This replaces MapLibre's OfflineManager
 * for imagery, which could only be pointed at LINZ's hosted style and measured
 * 3,175 resources / 74.6 MB for an area needing ~304 aerial tiles (~13 MB),
 * because that style declares two terrain sources its own layers never use and
 * MapLibre walks every source in a style.
 *
 * **One manager per pack**, rather than one manager with a mode: what a pack is fetched from and where it
 * lands is fixed when the manager is built (see the companion), and a walker's two lists - imagery and the
 * drawn map - are two managers reading two sets of rows out of one table.
 *
 * The Room row is the *record* for the screen. The tiles are shared between
 * areas and with ordinary map browsing, so forgetting a row takes only the tiles no other area of the
 * pack needs - see [deleteArea] - and [clearTiles] is the action that takes the rest.
 */
class OfflineAreaManager(
    /** Which pack's areas this manager records and downloads. */
    val pack: OfflinePack,
    /** What fetching that pack means: one pass, or several. */
    private val passes: List<PackPass>,
    private val dao: OfflineAreaDao,
    private val now: () -> Long = System::currentTimeMillis
) {

    /** The screen's list for this pack, straight from the database, updating as progress lands. */
    fun observeAreas(): Flow<List<OfflineArea>> =
        dao.observePack(pack.id).map { rows -> rows.map { it.toOfflineArea() } }

    suspend fun listAreas(): List<OfflineArea> = dao.byPack(pack.id).map { it.toOfflineArea() }

    suspend fun area(id: Long): OfflineArea? = dao.getById(id)?.toOfflineArea()

    /** Tiles and bytes actually on disk for this pack, covering every area and map browsing. */
    suspend fun summary(): TileStoreSummary = TileStoreSummary(
        tiles = passes.sumOf { it.store.storedTileCount() },
        bytes = passes.sumOf { it.store.storedBytes() }
    )

    /**
     * Validates the plan and records it, so progress has somewhere to be written
     * before the first tile arrives. Throws [IllegalArgumentException] for a plan
     * too large to be a sensible offline pack.
     *
     * The count is **every pass's tiles**, because that is what the download will fetch and what the
     * progress beside it has to be a fraction of: a two-pass pack counting one pass would sit at 50% when
     * it had finished.
     */
    suspend fun createArea(plan: OfflineAreaPlan): OfflineArea {
        val planned = plan.tileCount * passes.size
        require(planned <= MAX_TILES_PER_AREA) {
            "That area needs $planned tiles, more than the $MAX_TILES_PER_AREA limit. " +
                "Narrow the zoom range or the area."
        }
        val id = dao.insert(
            OfflineAreaEntity(
                name = plan.name,
                pack = pack.id,
                minLat = plan.bounds.minLat,
                minLng = plan.bounds.minLng,
                maxLat = plan.bounds.maxLat,
                maxLng = plan.bounds.maxLng,
                minZoom = plan.minZoom,
                maxZoom = plan.maxZoom,
                plannedTiles = planned,
                createdAtEpochMs = now()
            )
        )
        return area(id) ?: error("area $id was not stored")
    }

    /**
     * Downloads a recorded area, resuming from the tiles already on disk.
     *
     * Every pass in turn, each one resuming from what it already has. Cancelling is safe and cheap to
     * resume: tiles land on disk as they arrive and progress is written to the database as it goes, so a
     * second call picks up where this one stopped rather than starting over.
     */
    suspend fun download(areaId: Long): OfflineArea {
        val row = dao.getById(areaId) ?: error("no offline area with id $areaId")
        val bounds = LatLngBounds(row.minLat, row.minLng, row.maxLat, row.maxLng)

        var lastWrite = 0L
        var downloaded = 0L
        var bytes = 0L
        var failures = 0
        try {
            passes.forEach { pass ->
                val downloader = TileDownloader(store = pass.store, fetcher = pass.fetcherFor())
                val progress = downloader.download(bounds, row.minZoom, row.maxZoom) { current ->
                    // What a walker watching this reads: one sentence, the same one the offline screen and
                    // the notification show, and per pass - a single bar across two passes would appear to
                    // go backwards when the second one started.
                    DownloadState.report(
                        DownloadProgress(
                            pack = pack,
                            unit = TILE_UNIT,
                            phase = pass.phase,
                            done = current.stored,
                            total = current.total,
                            bytes = current.bytes,
                            elapsedMs = current.elapsedMs,
                            unavailable = current.unavailable,
                            failed = current.failed
                        )
                    )
                    val at = now()
                    // The screen reads the database, so progress is written as it happens
                    // - but not once per chunk of four tiles.
                    if (at - lastWrite >= PROGRESS_WRITE_MS) {
                        lastWrite = at
                        dao.updateProgress(
                            areaId,
                            downloaded + current.stored,
                            bytes + current.bytes
                        )
                    }
                }
                downloaded += progress.stored
                bytes += progress.bytes
                failures += progress.failed
            }
        } finally {
            // Finished, failed, or cancelled by the walker leaving: either way there is nothing left to
            // watch, and a sentence left behind would outlive the download it describes.
            DownloadState.clear()
        }

        if (failures == 0) {
            // Tiles LINZ serves nothing for - the edge of coverage, offshore - will never arrive. The area
            // is as complete as it can be: mark it finished and let missingTiles report the shortfall.
            dao.markComplete(areaId, now(), downloaded, bytes)
        } else {
            dao.updateProgress(areaId, downloaded, bytes)
            dao.recordError(areaId, "$failures of the tiles failed to download.")
        }
        return area(areaId) ?: error("area $areaId vanished during download")
    }

    /**
     * Forgets an area, **and frees the tiles of it that nothing else needs.**
     *
     * A tile is shared: two overlapping areas hold one copy between them, and a tile the walker browsed past
     * sits in the same store. So what goes is this area's own plan minus every other area's of the same pack
     * - exact, and the reason this is a subtraction rather than a delete of the box. Tiles merely looked at
     * are left alone: they were never this area's to take.
     *
     * Returns how many files went, so a screen can say what was reclaimed.
     */
    suspend fun deleteArea(areaId: Long): Int {
        val row = dao.getById(areaId) ?: return 0
        val mine = plannedTiles(row.bounds(), row.minZoom, row.maxZoom)
        val others = dao.byPack(pack.id)
            .filter { it.id != areaId }
            .flatMap { plannedTiles(it.bounds(), it.minZoom, it.maxZoom) }

        val removed = tilesToFree(mine, others).sumOf { tile ->
            passes.count { pass -> pass.store.delete(tile.zoom, tile.x, tile.y) }
        }
        dao.delete(areaId)
        return removed
    }

    /**
     * Removes every downloaded tile of this pack and every one of its records, returning how many files
     * went. The only action that actually reclaims the pack's disk space.
     */
    suspend fun clearTiles(): Int {
        val removed = passes.sumOf { it.store.deleteAll() }
        dao.deletePack(pack.id)
        return removed
    }

    companion object {
        /**
         * ~540 MB at the 45 KB average tile size our estimates assume. High
         * enough for a large block across zoom 10-16, low enough that a
         * mis-typed zoom range cannot fill the device.
         */
        const val MAX_TILES_PER_AREA = 12_000L

        /** What the downloads count, as the live sentence reads it. */
        private const val TILE_UNIT = "tiles"

        private const val PROGRESS_WRITE_MS = 1_000L

        /** The two halves of the drawn map's download, as the live sentence names them. */
        const val MAP_PHASE = "the map"
        const val HEIGHTS_PHASE = "the heights"

        /**
         * The imagery pack: LINZ's aerial tiles, into the store an offline area is downloaded into.
         */
        fun imagery(context: Context, dao: OfflineAreaDao): OfflineAreaManager = OfflineAreaManager(
            pack = OfflinePack.IMAGERY,
            passes = listOf(
                PackPass(
                    store = TileServerHolder.imageryStore(context),
                    fetcherFor = { LinzTileFetcher() }
                )
            ),
            dao = dao
        )

        /**
         * The drawn map's pack: its tiles, then the heights its hillshade is drawn from.
         *
         * Two passes rather than one, and both are what the map needs to draw a hillside: the tiles say
         * where the roads and the contours are, and the heights say what shape the ground is. A box with
         * one and not the other is a map with no relief.
         */
        fun topography(context: Context, dao: OfflineAreaDao): OfflineAreaManager = OfflineAreaManager(
            pack = OfflinePack.TOPOGRAPHY,
            passes = listOf(
                PackPass(
                    store = TileServerHolder.topographyStore(context),
                    phase = MAP_PHASE,
                    fetcherFor = {
                        LinzTileFetcher(
                            urlFor = { zoom, x, y ->
                                Basemap.LINZ_TOPO_LIDAR_HILLSHADE.tileUrl(zoom, x, y)
                            },
                            accept = "application/x-protobuf"
                        )
                    }
                ),
                PackPass(
                    store = TileServerHolder.elevationStore(context),
                    phase = HEIGHTS_PHASE,
                    fetcherFor = {
                        LinzTileFetcher(
                            urlFor = { zoom, x, y -> Basemap.elevationUrl(zoom, x, y) },
                            // The DEM endpoint answers PNG, and the renderer decodes it as PNG.
                            accept = "image/png"
                        )
                    }
                )
            ),
            dao = dao
        )
    }
}

/**
 * The tiles of one area that **no other area of the pack needs.**
 *
 * A tile is shared: two overlapping areas hold one copy between them, so forgetting an area takes its own
 * plan minus every other area's - which is why this is a subtraction rather than a delete of the box. A
 * tile that only this area asked for goes; a tile another area still describes stays, and its own row is
 * still an honest description of what is on disk.
 */
internal fun tilesToFree(mine: Collection<TileRef>, others: Collection<TileRef>): List<TileRef> {
    val held = others.toHashSet()
    return mine.filterNot { it in held }
}

/** A stored row's box, as the tile maths wants it. */
private fun OfflineAreaEntity.bounds() =
    LatLngBounds(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng)

/**
 * The sentence a Topographic download earns: **what came of the map, and of the heights it is shaded
 * from.**
 *
 * One figure for the two together, because they are one thing to a walker - the map either draws this
 * ground or it does not - and the record is where the two have already been added up.
 */
fun topoDownloadedMessage(area: OfflineArea): String =
    "Kept ${area.storedTiles} tiles of the map and its heights, about ${area.sizeLabel}."

private fun OfflineAreaEntity.toOfflineArea() = OfflineArea(
    id = id,
    name = name,
    bounds = LatLngBounds(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng),
    plannedTiles = plannedTiles.toInt(),
    storedTiles = downloadedTiles.toInt(),
    bytes = bytes,
    isComplete = isComplete,
    pack = OfflinePack.fromStorage(pack),
    lastError = lastError
)
