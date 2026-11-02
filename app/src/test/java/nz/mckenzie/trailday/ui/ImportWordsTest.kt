package nz.mckenzie.trailday.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * What the walker is told when a file comes in, and the question one can ask first.
 *
 * These sentences are shown on both of the phone's lists and on the desk, so they are pinned here: a
 * single import reads as it always has, a batch is counted, a file that failed is named in the same
 * breath as the ones that worked, and the question carries the numbers on its own buttons.
 *
 * The date inside a sentence is written by [formatDate], which has its own tests; what these hold to is
 * where it sits in the sentence and what stands beside it.
 */
class ImportWordsTest {

    /** 3 November 2026, midday UTC - a Tuesday in the walker's own afternoon either side of the line. */
    private val november3 = Instant.parse("2026-11-03T12:00:00Z").toEpochMilli()
    private val theThird = formatDate(november3)

    @Test
    fun `one place keeps the sentence Places has always said`() {
        assertEquals(
            "Imported \"Morning Walk\" with 412 points",
            ImportWords.onePlace(trackName = "Morning Walk", points = 412, sideTracks = 0, segmentsDidNotMeet = false)
        )
        assertEquals(
            "Imported \"Morning Walk\" with 412 points: the track and 2 side tracks.",
            ImportWords.onePlace(trackName = "Morning Walk", points = 412, sideTracks = 2, segmentsDidNotMeet = false)
        )
    }

    @Test
    fun `one activity is named, dated from its own file, and counted`() {
        assertEquals(
            "Imported \"Morning Walk\" as an activity, $theThird, 1,240 points.",
            ImportWords.oneActivity(name = "Morning Walk", dateEpochMs = november3, points = 1_240)
        )
    }

    @Test
    fun `an activity from a file with branches says how many were left out`() {
        // A walk is one line, so a branch hanging off it is counted rather than kept - and said, so the
        // walker knows the file held more than what arrived.
        assertEquals(
            "Imported \"Morning Walk\" as an activity, $theThird, 1,240 points; " +
                "2 side tracks were left out.",
            ImportWords.oneActivity(
                name = "Morning Walk",
                dateEpochMs = november3,
                points = 1_240,
                sideTracks = 2
            )
        )
    }

    @Test
    fun `several files are counted rather than listed`() {
        assertEquals(
            "Imported 5 tracks from 3 files.",
            ImportWords.summary(activities = 0, tracks = 5, files = 3)
        )
        assertEquals(
            "Imported 2 activities and 3 tracks from 3 files.",
            ImportWords.summary(activities = 2, tracks = 3, files = 3)
        )
    }

    @Test
    fun `a file that could not be read is named beside the ones that came in`() {
        assertEquals(
            "Imported 2 activities from 3 files; Ride.gpx could not be read.",
            ImportWords.summary(activities = 2, tracks = 0, files = 3, failures = listOf("Ride.gpx"))
        )
        assertEquals(
            "Nothing was imported. Ride.gpx could not be read.",
            ImportWords.summary(activities = 0, tracks = 0, files = 1, failures = listOf("Ride.gpx"))
        )
    }

    @Test
    fun `the desk says where the walk went, because a laptop sees nothing arrive`() {
        assertEquals(
            "Imported \"Morning Walk\" as an activity on the phone, $theThird, 1,240 points.",
            ImportWords.oneActivity(
                name = "Morning Walk",
                dateEpochMs = november3,
                points = 1_240,
                onPhone = true
            )
        )
        assertEquals(
            "Imported 4 tracks on the phone.",
            ImportWords.summary(activities = 0, tracks = 4, files = 1, onPhone = true)
        )
    }

    @Test
    fun `the question says which file, how many, and offers the count on both buttons`() {
        val question = ImportWords.Question(fileName = "Taupo rides", tracks = 4)

        assertEquals(
            "Taupo rides holds 4 tracks. Keep them as 4 activities, or as 4 places?",
            question.sentence
        )
        assertEquals("4 activities", question.activitiesLabel)
        assertEquals("4 places", question.placesLabel)
    }

    @Test
    fun `names are joined as a sentence rather than as a list`() {
        assertEquals("Ride.gpx", ImportWords.namesOf(listOf("Ride.gpx")))
        assertEquals("Ride.gpx and Walk.gpx", ImportWords.namesOf(listOf("Ride.gpx", "Walk.gpx")))
        assertEquals(
            "A.gpx, B.gpx and C.gpx",
            ImportWords.namesOf(listOf("A.gpx", "B.gpx", "C.gpx"))
        )
    }
}
