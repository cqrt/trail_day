package nz.mckenzie.trailday.domain.track

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.recording.ActivityMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a file is asked a question before anything is written.
 *
 * Two questions, in this order: **where it goes**, because four tracks could be a week of walks or four
 * places, and then **what it was**, because a ride saved as a walk is a green line where a bike belongs.
 * A file that answers either question itself is not asked it.
 */
class ImportDecisionTest {

    private fun track(hint: ActivityMode? = null) = NamedTrack(
        name = "Gully track",
        reading = TrackInterchange.Reading(
            paths = listOf(listOf(GeoPoint(-41.5, 173.8), GeoPoint(-41.6, 173.9))),
            sideTracks = 0,
            segmentsDidNotMeet = false
        ),
        hint = hint
    )

    @Test
    fun `a silent single track going to Activities is asked what it was`() {
        assertTrue(
            "nothing in the file says, and a ride drawn as a walk is a green line where a bike belongs",
            ImportDecision.asksMode(listOf(track()))
        )
    }

    @Test
    fun `a track that carries its own hint is not asked about`() {
        assertFalse(ImportDecision.asksMode(listOf(track(hint = ActivityMode.RIDE))))
        assertFalse(
            "a file whose every track says what it was has answered the question",
            ImportDecision.asksMode(listOf(track(hint = ActivityMode.RIDE), track(hint = ActivityMode.WALK)))
        )
        assertTrue(
            "one silent track in a file of two is still a file to ask about",
            ImportDecision.asksMode(listOf(track(hint = ActivityMode.RIDE), track()))
        )
    }

    @Test
    fun `a file that has been answered is written rather than asked again`() {
        assertFalse(
            ImportDecision.asksMode(listOf(track()), answered = ActivityMode.WALK)
        )
    }

    @Test
    fun `the destination is asked first, and only a file of several tracks is asked it`() {
        assertTrue(
            ImportDecision.asks(tracks = 4, destination = ImportDestination.ACTIVITIES)
        )
        assertFalse(
            ImportDecision.asks(tracks = 1, destination = ImportDestination.ACTIVITIES)
        )
        assertFalse(
            "Places asks nothing, and never has",
            ImportDecision.asks(tracks = 4, destination = ImportDestination.PLACES)
        )
    }
}
