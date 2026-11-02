package nz.mckenzie.trailday.ui

import java.util.Locale

/**
 * What the walker is told when a file comes in, and the one question one can ask first.
 *
 * **One set of words for both screens and the desk.** The same file is imported from the phone's two
 * lists and from a laptop, and three copies of "Imported 5 tracks from 2 files" would be three copies
 * to keep in step. So the sentences are built here, from facts each caller already has, and every
 * caller shows the same thing.
 *
 * A single track is its own sentence - the file named, and when it was, and how much of it was kept -
 * because that is the common case and the one a walker reads most. Several are counted, because a
 * list of five names is not a message.
 */
object ImportWords {

    /**
     * The question a file of several tracks asks, sent to Activities.
     *
     * The file's own name is in it, so the walker can see which file of several is being asked about,
     * and the numbers are on the buttons as well as in the sentence: the answer is a count.
     */
    data class Question(val fileName: String, val tracks: Int) {
        val sentence: String
            get() = "$fileName holds $tracks tracks. Keep them as $tracks activities, or as $tracks places?"

        val activitiesLabel: String get() = "$tracks activities"

        val placesLabel: String get() = "$tracks places"
    }

    /**
     * One place, read out of one file, in the words Places has always used - point count and all, and
     * what became of the file's other paths.
     *
     * Deliberately unchanged by activities arriving: a walker who imports a track into Places reads
     * exactly what they read before.
     */
    fun onePlace(trackName: String, points: Int, sideTracks: Int, segmentsDidNotMeet: Boolean): String =
        when {
            segmentsDidNotMeet ->
                "Imported \"$trackName\" with $points points as one track: the file's " +
                    "own track segments do not meet, so they were joined up."

            sideTracks == 1 ->
                "Imported \"$trackName\" with $points points: the track and 1 side track."

            sideTracks > 1 ->
                "Imported \"$trackName\" with $points points: the track and $sideTracks side tracks."

            else -> "Imported \"$trackName\" with $points points"
        }

    /**
     * One activity, dated from its own track: `Imported "Morning Walk" as an activity, 3 Nov 2026,
     * 1,240 points.`
     *
     * [onPhone] is the desk's version of the same sentence, which says where it went - a walker reading
     * it on a laptop has not seen the activity appear anywhere.
     *
     * [sideTracks] is what an activity cannot keep: it is a walk, and a branch hanging off the line is
     * not one. The number is said rather than the branch dropped quietly.
     */
    fun oneActivity(
        name: String,
        dateEpochMs: Long,
        points: Int,
        sideTracks: Int = 0,
        onPhone: Boolean = false
    ): String {
        val where = if (onPhone) " on the phone" else ""
        val head = "Imported \"$name\" as an activity$where, ${formatDate(dateEpochMs)}, " +
            "${grouped(points)} points"
        return clauses(head, leftOut(sideTracks))
    }

    /**
     * Anything more than one track: `Imported 5 tracks from 3 files`, and what could not be read.
     *
     * A place is called a track, because that is what it was - files hold tracks - and that is the
     * word Places' own import has always used. A file that failed is named in the same sentence rather
     * than stopping the rest, because a batch of five with one bad file is four imports and one thing
     * to go and look at.
     */
    fun summary(
        activities: Int,
        tracks: Int,
        files: Int,
        sideTracks: Int = 0,
        failures: List<String> = emptyList(),
        onPhone: Boolean = false
    ): String {
        val written = listOfNotNull(
            if (activities > 0) counted(activities, "activity", "activities") else null,
            if (tracks > 0) counted(tracks, "track", "tracks") else null
        ).joinToString(" and ")

        if (written.isEmpty()) {
            return if (failures.isEmpty()) {
                "Nothing was imported."
            } else {
                "Nothing was imported. ${namesOf(failures)} could not be read."
            }
        }

        val from = when {
            files > 1 -> " from $files files"
            files == 1 && !onPhone -> " from 1 file"
            else -> ""
        }
        val where = if (onPhone) " on the phone" else ""
        return clauses("Imported $written$from$where", leftOut(sideTracks), couldNotBeRead(failures))
    }

    /** The file names as one phrase: "Ride.gpx", "Ride.gpx and Walk.gpx", "A, B and C". */
    fun namesOf(names: List<String>): String = when {
        names.isEmpty() -> ""
        names.size == 1 -> names.single()
        names.size == 2 -> "${names[0]} and ${names[1]}"
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    /** "1,240" - a count with its thousands shown, which is how the plan writes a long track's points. */
    fun grouped(count: Int): String = String.format(Locale.US, "%,d", count)

    private fun counted(count: Int, one: String, many: String): String =
        "$count ${if (count == 1) one else many}"

    private fun leftOut(count: Int): String? = when {
        count <= 0 -> null
        count == 1 -> "1 side track was left out"
        else -> "$count side tracks were left out"
    }

    private fun couldNotBeRead(files: List<String>): String? =
        if (files.isEmpty()) null else "${namesOf(files)} could not be read"

    /** A head and its asides, as one sentence: `head; aside; aside.` */
    private fun clauses(head: String, vararg asides: String?): String =
        (listOf(head) + asides.filterNotNull()).joinToString("; ") + "."
}
