package nz.mckenzie.trailday.domain.track

import nz.mckenzie.trailday.domain.recording.ActivityMode

/**
 * What a file's tracks are written as.
 *
 * The two Import buttons differ only in this: Places writes every track in a file as a place, and
 * Activities writes them as activities. The reading before it is the same, and so is the name each
 * track gets - see [FileTracks].
 */
enum class ImportDestination {
    ACTIVITIES,
    PLACES
}

/**
 * The questions a file can raise, and the rules about when they are asked.
 *
 * **A file of several tracks sent to Activities has to be asked where it goes.** A week's walks exported
 * from a watch is a file of four tracks, and so is a file of four trails somebody drew elsewhere; nothing
 * in the file says which the walker means. Sent to Places there is nothing to ask: every track in a file
 * has always become a place, and still does, without a question.
 *
 * **And a file that says nothing has to be asked what it was.** A ride saved as a walk is a green line
 * where a bike belongs, and a file from another app very often says nothing at all. The two questions
 * are asked in that order - where first, because a file sent to Places is never asked what it was - so
 * a silent file of four tracks asks twice and a silent file of one asks once.
 */
object ImportDecision {

    /**
     * Whether this file has to be asked where it goes before anything is written.
     *
     * [answered] is what the walker has already said for this file, if anything: a file that has been
     * answered is written, not asked about again.
     */
    fun asks(
        tracks: Int,
        destination: ImportDestination,
        answered: ImportDestination? = null
    ): Boolean = destination == ImportDestination.ACTIVITIES && tracks > 1 && answered == null

    /**
     * Whether this file has to be asked whether it was a walk or a ride.
     *
     * Only a file going to Activities, and only when one of its tracks says nothing: a file whose every
     * track carries its own type has answered the question already. [answered] is what the walker said
     * when it was asked, if it has been.
     */
    fun asksMode(
        tracks: List<NamedTrack>,
        answered: ActivityMode? = null
    ): Boolean = answered == null && tracks.any { it.hint == null }
}
