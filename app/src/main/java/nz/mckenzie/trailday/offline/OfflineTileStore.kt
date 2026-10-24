package nz.mckenzie.trailday.offline

import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.domain.tiles.TileMath
import java.io.File

/** One aerial tile in the XYZ scheme. */
data class TileRef(val zoom: Int, val x: Int, val y: Int)

/**
 * Every tile covering [bounds] across the inclusive zoom range.
 *
 * A free function rather than a method on the downloader, because **two things need it and only one of them
 * downloads**: the downloader asks for exactly these tiles, and forgetting an area has to know which tiles
 * were its own before it can work out which of them nothing else needs.
 */
fun plannedTiles(bounds: LatLngBounds, minZoom: Int, maxZoom: Int): List<TileRef> {
    if (maxZoom < minZoom) return emptyList()
    val tiles = ArrayList<TileRef>(TileMath.tileCount(bounds, minZoom, maxZoom).toInt().coerceAtLeast(0))
    for (zoom in minZoom..maxZoom) {
        val range = TileMath.tileRange(bounds, zoom)
        for (x in range.minX..range.maxX) {
            for (y in range.minY..range.maxY) {
                tiles += TileRef(zoom, x, y)
            }
        }
    }
    return tiles
}

/**
 * Imagery tiles on disk, laid out as `{z}/{x}/{y}.webp` under a root directory.
 *
 * Deliberately plain files rather than MapLibre's offline database or an MBTiles
 * file: it is trivially inspectable, resumable tile-by-tile, safe to delete, and
 * it can be served straight back to the map by the local tile server.
 *
 * A directory of files also copes with the real world: LINZ has no imagery for
 * some tiles, and a missing file is simply a 404 rather than a hole in a
 * database that has to be reasoned about.
 *
 * The suffix follows the source: the aerial tiles are `.webp`, OpenStreetMap's are `.png`. One
 * store holds one source, so a store knows which it is - see
 * [nz.mckenzie.trailday.domain.tiles.Basemap.tileSuffix].
 */
class OfflineTileStore(
    private val root: File,
    /** How this source's tiles are named on disk, e.g. `.webp`. */
    private val suffix: String = TILE_SUFFIX
) {

    fun tileFile(zoom: Int, x: Int, y: Int): File = File(root, "$zoom/$x/$y$suffix")

    fun contains(zoom: Int, x: Int, y: Int): Boolean = tileFile(zoom, x, y).isFile

    fun read(zoom: Int, x: Int, y: Int): ByteArray? {
        val file = tileFile(zoom, x, y)
        return if (file.isFile) file.readBytes() else null
    }

    /** Writes a tile, creating directories as needed. Returns the bytes stored. */
    fun write(zoom: Int, x: Int, y: Int, bytes: ByteArray): Int {
        val file = tileFile(zoom, x, y)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return bytes.size
    }

    /**
     * Removes one tile, and the directories it leaves empty. True when there was a file to remove.
     *
     * The directories matter: a store of ten thousand tiles is a tree of small folders, and forgetting an
     * area that leaves its whole skeleton behind is a tidier list and no more disk. Only the folder the tile
     * was in is looked at - a parent shared with a tile that is still held is not empty, so it stays.
     */
    fun delete(zoom: Int, x: Int, y: Int): Boolean {
        val file = tileFile(zoom, x, y)
        if (!file.isFile) return false
        val removed = file.delete()
        file.parentFile?.takeIf { it.listFiles()?.isEmpty() == true }?.delete()
        return removed
    }

    /** Tiles currently held, by walking the tree (used for reporting on launch). */
    fun storedTileCount(): Long = root.walkTopDown().count { it.isFile && it.name.endsWith(suffix) }.toLong()

    fun storedBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Removes everything. Returns how many files were deleted. */
    fun deleteAll(): Int {
        val files = root.walkTopDown().filter { it.isFile }.count()
        root.deleteRecursively()
        return files
    }

    companion object {
        const val TILE_SUFFIX = ".webp"
    }
}
