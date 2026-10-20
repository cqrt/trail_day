package nz.mckenzie.trailday.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The photographs the walker attaches to their own places.
 *
 * A photograph is taken for a spot, not archived: it is scaled so its long edge is at most
 * [MAX_EDGE_PX] and compressed as JPEG before it reaches the database. That keeps a place's page
 * quick and a backup file from filling with camera output, while leaving enough detail to recognise
 * the campsite, the water source or the junction the waypoint is about.
 */
object WaypointPhotos {

    /** The longest side kept, in pixels: enough to recognise a place, small enough to load at once. */
    const val MAX_EDGE_PX = 1600

    /** JPEG quality for the stored copy. */
    const val JPEG_QUALITY = 85

    /** Where the camera writes its full-size original until it has been read and scaled. */
    private const val CACHE_DIR = "waypoint-photos"

    /** A file the camera may write to, and the content URI handed to the camera app. */
    data class CameraTarget(val uri: Uri, val file: File)

    /**
     * A fresh file in the cache for the camera to write to.
     *
     * A cache file rather than the database: the camera app needs a URI before there is an asset
     * id, and the original is deleted once [fromUri] has produced the stored copy.
     */
    fun newCameraTarget(context: Context): CameraTarget {
        val directory = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val file = File.createTempFile("waypoint-", ".jpg", directory)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return CameraTarget(uri = uri, file = file)
    }

    /**
     * Reads the picture at [uri] and returns the compressed JPEG to store, or null when it cannot
     * be read as a picture.
     *
     * The image is sampled down while decoding rather than after, so a modern phone's 50-megapixel
     * original does not have to fit in memory first.
     */
    suspend fun fromUri(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE_PX || bounds.outHeight / sample > MAX_EDGE_PX) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@withContext null

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val oriented = decoded.oriented(orientation)
        if (oriented !== decoded) decoded.recycle()

        ByteArrayOutputStream().use { output ->
            oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            oriented.recycle()
            output.toByteArray()
        }
    }

    /** A stored photograph as a bitmap for a preview. */
    fun bitmap(bytes: ByteArray): Bitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    /**
     * [bitmap] turned the way the camera said it was, when the camera stored the pixels sideways
     * and left the direction in EXIF.
     */
    private fun Bitmap.oriented(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
