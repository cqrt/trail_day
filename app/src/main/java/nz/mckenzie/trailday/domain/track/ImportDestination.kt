package nz.mckenzie.trailday.domain.track

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
 * The one question a file can raise, and the rule about when it is asked.
 *
 * **A file of several tracks sent to Activities has to be asked about.** A week's walks exported from
 * a watch is a file of four tracks, and so is a file of four trails somebody drew elsewhere; nothing in
 * the file says which the walker means. Sent to Places there is nothing to ask: every track in a file
 * has always become a place, and still does, without a question.
 */
object ImportDecision {

    /**
     * Whether this file has to be asked about before anything is written.
     *
     * [answered] is what the walker has already said for this file, if anything: a file that has been
     * answered is written, not asked about again.
     */
    fun asks(
        tracks: Int,
        destination: ImportDestination,
        answered: ImportDestination? = null
    ): Boolean = destination == ImportDestination.ACTIVITIES && tracks > 1 && answered == null
}
