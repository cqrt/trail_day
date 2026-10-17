package nz.mckenzie.trailday.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.mckenzie.trailday.data.db.OfflineDocItemDao
import nz.mckenzie.trailday.data.db.OfflineDocPictureEntity
import nz.mckenzie.trailday.doc.DocPictures
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Whether DOC's address for a picture is one to fetch.
 *
 * DOC publishes a picture's address as `introductionThumbnail`, and for some things that field holds the
 * address of a **page about them** rather than a picture - `.aspx`, which answers with HTML. Fetching
 * those would keep a page as though it were a picture, so what is fetched is what has a picture's
 * suffix; the rest is DOC's own data being what it is, and the thing it is of is still kept whole.
 */
fun isPictureAddress(url: String): Boolean {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return PICTURE_SUFFIXES.any { path.endsWith(it) }
}

private val PICTURE_SUFFIXES = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")

/**
 * The pictures DOC publishes for its huts, campsites and bike locations, kept in the cache the material
 * itself is kept in.
 *
 * Kept for the same reason the material is: a hut with no reception should still show what it looks
 * like, and the walker at the shelter is exactly the person who cannot fetch it. One row per address, so
 * keeping two overlapping areas costs one copy of a shared picture, and a download that stops short
 * still leaves every picture that did arrive.
 *
 * **A picture that will not come is not a failed download.** Each is fetched on its own, and what
 * arrived is kept whatever the others did - DOC's site being down for a thumbnail is not a reason to
 * lose the hut.
 */
class DocPictureCache(
    private val dao: OfflineDocItemDao,
    /** How a picture is fetched. The app's own fetcher, overridable so a test can hold the network. */
    private val fetch: suspend (String) -> ByteArray? = ::fetchPicture,
    private val now: () -> Long = System::currentTimeMillis,
    /** The largest picture worth keeping. DOC's are thumbnails; anything larger is not what was asked for. */
    private val maxBytes: Int = MAX_PICTURE_BYTES
) {

    /** How many pictures are held, for the offline screen's own line. */
    suspend fun count(): Int = dao.pictureCount()

    /** The picture at DOC's address for it, or null when it is not held. */
    suspend fun read(url: String): ByteArray? = dao.picture(url)?.bytes

    /** Forgets every picture. The material they are pictures of goes by its own door. */
    suspend fun clear() {
        dao.deletePictures()
    }

    /**
     * Keeps the pictures at [urls] that are not already here, and answers how many are now on the phone.
     *
     * Read once each, in order, so a download of a hundred pictures is a hundred requests and not a
     * thousand: DOC's site is somebody else's, and asking politely is the app's own rule. A picture
     * already held is counted but not fetched again, so re-downloading an area costs no traffic for what
     * it already has.
     */
    suspend fun keep(urls: List<String>): DocPictures {
        val wanted = urls.filter { isPictureAddress(it) }.distinct()
        if (wanted.isEmpty()) return DocPictures.NONE

        var kept = 0
        for (url in wanted) {
            if (dao.picture(url) != null) {
                kept++
                continue
            }
            val bytes = runCatching { fetch(url) }.getOrNull() ?: continue
            if (bytes.isEmpty() || bytes.size > maxBytes) continue
            dao.insertPicture(OfflineDocPictureEntity(url = url, bytes = bytes, fetchedAtEpochMs = now()))
            kept++
        }
        return DocPictures(asked = wanted.size, kept = kept)
    }

    companion object {
        /** 2 MB: far more than any of DOC's thumbnails, and small enough not to be worth defending. */
        const val MAX_PICTURE_BYTES = 2 * 1024 * 1024
    }
}

/**
 * Fetches one of DOC's pictures.
 *
 * DOC serves these from its own website rather than from the open-data services, so there is no service
 * to ask twice and nothing to page: one GET, and anything but a `200` is nothing to keep. A picture's
 * address that answers with a page - which DOC's own data sometimes holds - is a page, and reading it
 * into the cache would put HTML where a picture goes.
 *
 * The read is bounded: a picture far larger than a thumbnail is not the thing that was asked for, and
 * neither DOC's site nor the walker's storage should be at the mercy of an address that grows one.
 */
private suspend fun fetchPicture(url: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", sprayDayUserAgent())
            setRequestProperty("Accept", "image/*")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            connection.inputStream.use { stream ->
                val held = ByteArrayOutputStream()
                val chunk = ByteArray(READ_CHUNK_BYTES)
                var read = stream.read(chunk)
                while (read > 0) {
                    held.write(chunk, 0, read)
                    if (held.size() > DocPictureCache.MAX_PICTURE_BYTES) return@use null
                    read = stream.read(chunk)
                }
                held.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 20_000
private const val READ_CHUNK_BYTES = 16 * 1024
