package nz.mckenzie.trailday.domain.track

import nz.mckenzie.trailday.domain.recording.ActivityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a file says about itself, and when it is worth believing.
 *
 * The two ways this goes wrong are both somebody reading their trip back: a ride asked about when the
 * file already said "cycling" - which is the paperwork this rule exists to remove - and a walk called a
 * ride because the word "bike" was in the name of the track it was walked on.
 */
class ActivityHintTest {

    @Test
    fun `a type the file wrote is the strongest answer`() {
        assertEquals("Garmin writes cycling", ActivityMode.RIDE, ActivityHint.of(type = "cycling", trackName = null, fileName = null))
        assertEquals("and biking", ActivityMode.RIDE, ActivityHint.of(type = "Biking", trackName = null, fileName = null))
        assertEquals("hiking is a walk", ActivityMode.WALK, ActivityHint.of(type = "hiking", trackName = null, fileName = null))
        assertEquals("as is a run", ActivityMode.WALK, ActivityHint.of(type = "Running", trackName = null, fileName = null))
        assertEquals("mtb is a ride", ActivityMode.RIDE, ActivityHint.of(type = "MTB", trackName = null, fileName = null))
        assertEquals("an e-bike is a ride", ActivityMode.RIDE, ActivityHint.of(type = "e-bike", trackName = null, fileName = null))
    }

    @Test
    fun `a type that says both, or neither, is not an answer`() {
        assertNull("a file claiming both is a file not worth believing", ActivityHint.of(type = "walking and cycling", trackName = null, fileName = null))
        assertNull(ActivityHint.of(type = "other", trackName = null, fileName = null))
        assertNull(ActivityHint.of(type = null, trackName = null, fileName = null))
    }

    @Test
    fun `the names are read when the type says nothing`() {
        assertEquals(
            "Taupo_rides says it in its own name, with an underscore for a space",
            ActivityMode.RIDE,
            ActivityHint.of(type = null, trackName = null, fileName = "Taupo_rides")
        )
        assertEquals(
            "and a file called Walk is a walk",
            ActivityMode.WALK,
            ActivityHint.of(type = null, trackName = null, fileName = "Walk")
        )
        assertEquals(
            "a track's own name counts as much as the file's",
            ActivityMode.RIDE,
            ActivityHint.of(type = null, trackName = "Wanaka bike loop", fileName = "2026-11-03")
        )
    }

    @Test
    fun `a name that holds both words is a name to ask about`() {
        assertNull(
            "Walk the bike shop could be either, so it is neither",
            ActivityHint.of(type = null, trackName = "Walk the bike shop", fileName = null)
        )
    }

    @Test
    fun `the type outranks the name`() {
        assertEquals(
            "a file that says it was cycling is cycling, whatever the track is called",
            ActivityMode.RIDE,
            ActivityHint.of(type = "cycling", trackName = "Morning walk", fileName = "Morning_walk")
        )
    }

    @Test
    fun `nothing at all is nothing, which is a question rather than a guess`() {
        assertNull(ActivityHint.of(type = "   ", trackName = "  ", fileName = ""))
    }
}
