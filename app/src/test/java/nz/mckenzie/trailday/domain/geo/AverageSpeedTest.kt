package nz.mckenzie.trailday.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Moving time and the average over it.
 *
 * The number here is the one a trip's page prints and the one the walk-or-ride rule compares against, so
 * these are the cases that keep the two from disagreeing: a pause taken off, a pause that never ended
 * taken to the end of the recording, and the rounding that decides whether 5.95 reads as 6.0.
 */
class AverageSpeedTest {

    private val start = 1_790_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun `no pauses at all is all moving time`() {
        assertEquals(hour, AverageSpeed.movingMs(start, start + hour, emptyList()))
    }

    @Test
    fun `a closed pause comes off the moving time`() {
        val pause = RecordingBreak(fromEpochMs = start + 600_000L, toEpochMs = start + 1_800_000L)

        assertEquals(
            "an hour out, twenty minutes of it stopped: forty minutes moving",
            2_400_000L,
            AverageSpeed.movingMs(start, start + hour, listOf(pause))
        )
    }

    @Test
    fun `a pause that never ended runs to the end of the recording`() {
        // Finished while paused, which is what storing a pause with no end means.
        val pause = RecordingBreak(fromEpochMs = start + 2_700_000L, toEpochMs = null)

        assertEquals(
            "fifteen minutes moving, then stopped to the end",
            2_700_000L,
            AverageSpeed.movingMs(start, start + hour, listOf(pause))
        )
    }

    @Test
    fun `no end to the recording is no moving time to claim`() {
        assertEquals(
            "a file with no times says nothing about how long it took",
            0L,
            AverageSpeed.movingMs(start, endedAtEpochMs = null, breaks = emptyList())
        )
    }

    @Test
    fun `an end before the start, and more pause than pass, are both zero rather than negative`() {
        assertEquals(
            0L,
            AverageSpeed.movingMs(start, start - hour, emptyList())
        )
        assertEquals(
            "a stored pause can lie outside the fixes, and nobody moved for minus twenty minutes",
            0L,
            AverageSpeed.movingMs(
                start,
                start + hour,
                listOf(RecordingBreak(fromEpochMs = start, toEpochMs = start + 2 * hour))
            )
        )
    }

    @Test
    fun `the average says nothing without a distance or a time`() {
        assertNull("no distance", AverageSpeed.kmh(distanceM = 0.0, movingMs = hour))
        assertNull("no time", AverageSpeed.kmh(distanceM = 5_000.0, movingMs = 0L))
        assertEquals(5.0, AverageSpeed.kmh(distanceM = 5_000.0, movingMs = hour)!!, 1e-9)
    }

    @Test
    fun `the shown average is one decimal, rounded half up`() {
        assertEquals(
            "5.94 reads as 5.9",
            5.9,
            AverageSpeed.shownKmh(distanceM = 5_940.0, movingMs = hour)!!,
            1e-9
        )
        assertEquals(
            "5.95 reads as 6.0, because a person expects the boundary to be the number",
            6.0,
            AverageSpeed.shownKmh(distanceM = 5_950.0, movingMs = hour)!!,
            1e-9
        )
        assertEquals(
            "and it is the hour's worth of ground that is rounded, not the other way round",
            12.0,
            AverageSpeed.shownKmh(distanceM = 12_040.0, movingMs = hour)!!,
            1e-9
        )
        assertNull(
            "nothing to divide by is a dash on the page, not a zero",
            AverageSpeed.shownKmh(distanceM = 5_000.0, movingMs = 0L)
        )
    }
}
