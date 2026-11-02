package nz.mckenzie.trailday.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import nz.mckenzie.trailday.domain.track.FileTracks

/**
 * One file the walker picked out of the phone's file picker, read off the disk.
 *
 * Both lists import files now, so both read them the same way: the bytes as they are, the name the
 * file is called when something has to be named, and the name without its extension, which is what a
 * track with no name of its own is kept under.
 *
 * [fileName] carries its extension because the sentence for a file that failed names it as the walker
 * sees it - "Ride.gpx could not be read" - where [baseName] is what a track would be called.
 */
internal data class PickedFile(
    val fileName: String,
    val baseName: String,
    val bytes: ByteArray
) {
    // A data class holding bytes is asked to compare them by value, which two reads of one file would
    // not satisfy anyway. Nothing here compares two, so identity is honest enough.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/** What came back from the picker: a file, or the name of one that would not open. */
internal sealed interface Picked {
    data class Read(val file: PickedFile) : Picked

    /**
     * The picker handed back something this app could not open - a file deleted since it was listed,
     * or one in a folder it may not read. The name is kept so the walker is told which one it was.
     */
    data class Unreadable(val fileName: String) : Picked
}

/**
 * The file behind a picker's answer.
 *
 * A failure is an answer rather than an exception, because it happens in the middle of a batch: five
 * files picked at once, one of them gone, and the other four still have to come in. The name is read
 * first, so the sentence about the one that failed can say which.
 */
internal fun pickFile(context: Context, uri: Uri): Picked {
    val fileName = fileNameOf(context, uri)
    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return Picked.Unreadable(fileName)

    // The extension is dropped for the name a track is kept under, by the same rule the desk's own
    // picker gets its name from - see [FileTracks.baseNameOf].
    return Picked.Read(
        PickedFile(fileName = fileName, baseName = FileTracks.baseNameOf(fileName), bytes = bytes)
    )
}

/** What the file is called, as the phone's own picker shows it. */
internal fun fileNameOf(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: FileTracks.FALLBACK_NAME
