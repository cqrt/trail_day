package nz.mckenzie.trailday.domain.recording

/**
 * The mark a place carries when a walk made it: `recording:<the trip's number>`.
 *
 * A place created at the end of a recording is the walk's own line, and the number is what says so - the
 * same field DOC's saved material is marked in, with a different word in front of it. A place recorded
 * *for*, rather than created by a walk, carries no mark: it was the walker's before the walk.
 */
fun recordingSourceRef(sessionId: Long): String = "$RECORDING_REF_PREFIX$sessionId"

/** Whether a place was made by a walk at all - by any walk, of any number. */
fun madeByWalk(sourceRef: String?): Boolean = sourceRef?.startsWith(RECORDING_REF_PREFIX) == true

private const val RECORDING_REF_PREFIX = "recording:"

/**
 * Whether deleting the trip called [sessionName] should delete the place called [placeName] as well.
 *
 * **A place a walk made goes with the walk.** Deleting the trip and leaving its line behind as a place is
 * the app asking the walker to tidy up after it; deleting a place the walker made for themselves, or one
 * that other walks were made of, would take away more than the trip that was asked about.
 *
 * Three answers, and the strict one wins:
 *
 * - **A marked place** ([madeByWalk]) was created by a walk. With no other walk of it, this trip made it,
 *   and it goes too.
 * - **An unmarked place with the same name and no other walk** is one a walk made before places carried the
 *   mark. The name is the test: a trip made *for* a place is named after it with the date on the end, so an
 *   identical name is the walk's own, not the place's.
 * - **Everything else stays**: the place was the walker's before the walk, or other walks were made of it,
 *   and one trip's end is not the end of the place.
 */
fun placeMadeByWalk(
    placeName: String,
    sessionName: String,
    placeSourceRef: String?,
    walksOfPlace: Int
): Boolean {
    if (walksOfPlace > 1) return false
    if (madeByWalk(placeSourceRef)) return true
    return placeName == sessionName
}
