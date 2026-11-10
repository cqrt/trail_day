package nz.mckenzie.trailday.domain.track

import nz.mckenzie.trailday.domain.recording.ActivityMode

/**
 * A file's tracks, read once, each under the name it will be kept as.
 *
 * **Reading is not writing.** Before this, `AssetRepository.importAssetTracks` read a file and wrote a
 * place for every track in it, in one go - which was fine while a file could only become places. It
 * can now become activities as well, so the reading stops at the reading, and what a track becomes is
 * the caller's decision. The phone's own list screen and the desk both read through here, so a file
 * they accept and a file they refuse are the same files, named the same way.
 *
 * [reading] is [TrackInterchange]'s own answer about the ground: the line first and its side tracks
 * after it, or the line every path was joined into.
 *
 * [hint] is what the file said the track was - see [ActivityHint] - and null when it said nothing. It
 * travels with the track rather than being read again by each caller, so the phone, the desk and the
 * Places import all read a file the same way: a file that says ride is a ride on all three, and a file
 * that says nothing asks on the two surfaces that ask.
 */
data class NamedTrack(
    val name: String,
    val reading: TrackInterchange.Reading,
    val hint: ActivityMode? = null
) {
    /** The line itself: everything after it is a side track, which an activity does not keep. */
    val line get() = reading.paths.firstOrNull().orEmpty()
}

object FileTracks {

    /** What a file with no usable name of its own is called. */
    const val FALLBACK_NAME = "Imported track"

    /**
     * Every track [bytes] hold, named: its own name where the format gives one, the file's otherwise.
     *
     * The name is the rule Places has always used, and it stays in one place so a track imported as an
     * activity is called exactly what the same track imported as a place would be. A file of several
     * unnamed tracks numbers them, so they can still be told apart.
     *
     * Throws in the reading's own words when the file is not a track at all, which is what both
     * callers show: a malformed file is a normal thing to pick by mistake.
     */
    fun read(fileName: String, bytes: ByteArray): List<NamedTrack> {
        val tracks = when (val outcome = TrackInterchange.readFile(bytes)) {
            is TrackInterchange.FileOutcome.Invalid -> throw IllegalArgumentException(outcome.message)
            is TrackInterchange.FileOutcome.Read -> outcome.tracks
        }
        if (tracks.isEmpty()) throw IllegalArgumentException(TrackInterchange.TOO_SHORT)
        val baseName = baseNameOf(fileName)
        return tracks.mapIndexed { index, track ->
            NamedTrack(
                name = nameOf(fileName = fileName, own = track.name, index = index, count = tracks.size),
                reading = track.reading,
                // Computed here rather than by each caller, so the phone, the desk and the Places import
                // cannot come to different conclusions about the same file.
                hint = ActivityHint.of(type = track.type, trackName = track.name, fileName = baseName)
            )
        }
    }

    /** The name one track is kept under: its own, or the file's, numbered only when it has to be. */
    fun nameOf(fileName: String, own: String?, index: Int, count: Int): String {
        val base = fileName.trim().ifBlank { FALLBACK_NAME }
        val given = own?.trim()?.takeIf { it.isNotBlank() }
        return given ?: if (count == 1) base else "$base ${index + 1}"
    }

    /**
     * A file's own name with its extension dropped: `Morning_Walk_2026-11-03.gpx` becomes
     * `Morning_Walk_2026-11-03`, which is what an activity or a place of that track is called.
     *
     * Here rather than in each caller, because the phone's picker and the desk's picker both start from
     * a file with a name, and an activity must be called what the same file imported on the other
     * surface would have been called. A name with no dot keeps all of itself, and a name that leaves
     * nothing behind - a file called `.gpx` - falls back to [FALLBACK_NAME].
     */
    fun baseNameOf(fileName: String): String =
        fileName.substringBeforeLast('.').trim().ifBlank { FALLBACK_NAME }
}
