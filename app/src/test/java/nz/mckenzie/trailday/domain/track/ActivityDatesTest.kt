package nz.mckenzie.trailday.domain.track

import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a walk that arrived in a file happened.
 *
 * The date is the walk's own - the first time in its fixes - because a file's date on disk says when it
 * was copied about rather than when the walking was done. And a file with **no** times is the case worth
 * the test: it has no length either, and claiming "0s" beside a distance would be inventing a measurement.
 */
class ActivityDatesTest {

    private fun point(secondsAfterStart: Long? = null) = GeoPoint(
        lat = -41.5,
        lng = 173.8,
        // The sentinel a fix with no time carries: GeoJSON is read without times at all, and a GPX may
        // be written without `<time>`.
        timeMs = secondsAfterStart?.let { START + it * 1000L } ?: 0L
    )

    @Test
    fun `a walk with times begins at its first fix and ends at its last`() {
        val date = ActivityDates.of(listOf(point(0), point(600), point(3_600)))

        assertEquals("the walk began when its first fix was taken", START, date.startEpochMs)
        assertEquals("and ended 57 minutes later", START + 3_600_000L, date.endEpochMs)
        assertTrue(date.hasTimes)
    }

    @Test
    fun `a walk with no times at all is dated today and claims no length`() {
        val date = ActivityDates.of(listOf(point(), point(), point()), nowEpochMs = NOW)

        assertEquals("today, because the file says nothing else", NOW, date.startEpochMs)
        assertNull("and no end, so no duration is claimed", date.endEpochMs)
        assertFalse(date.hasTimes)
    }

    @Test
    fun `one real time among fixes with none is still a time`() {
        // A file can be half-timed - a pause written without a `<time>`, or a segment from a device that
        // did not have a signal - and one real time is enough to date the walk by.
        val date = ActivityDates.of(listOf(point(), point(120), point()), nowEpochMs = NOW)

        assertEquals("the file's own time, not today", START + 120_000L, date.startEpochMs)
        assertEquals("and it is the only time there is", START + 120_000L, date.endEpochMs)
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val NOW = 1_800_000_000_000L
    }
}
