package nz.mckenzie.trailday.ui.screens

import nz.mckenzie.trailday.domain.geo.ElevationProfile
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.RecordingBreak
import nz.mckenzie.trailday.domain.recording.RecordingStatus
import nz.mckenzie.trailday.viewmodel.RecordingDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A trip's figures, as its page puts them in cells.
 *
 * The two that are easy to get wrong are **moving time** - the total less the pauses the walker pressed - and
 * the average, which is over the moving time for the same reason: a long lunch is not a slow walk.
 */
class RecordingFiguresTest {

    private val start = 1_000_000L

    /** A pass of [minutes] with the breaks given, and whatever heights the points carry. */
    private fun trip(
        minutes: Long,
        breaks: List<RecordingBreak> = emptyList(),
        heights: List<Double?> = emptyList(),
        distanceM: Double = 5_000.0
    ): RecordingDetail {
        val points = (0..3).map { index ->
            GeoPoint(
                lat = -41.6 + index * 0.001,
                lng = 172.7,
                altitudeM = heights.getOrNull(index)
            )
        }
        return RecordingDetail(
            id = 1L,
            name = "Track 18 Oct",
            startedAtEpochMs = start,
            endedAtEpochMs = start + minutes * 60_000L,
            status = RecordingStatus.FINISHED,
            recordedDistanceM = distanceM,
            geometryDistanceM = distanceM,
            points = points,
            breaks = breaks,
            assetId = null,
            assetName = null
        )
    }

    private fun value(rows: List<List<Figure>>, label: String): String =
        rows.flatten().first { it.label == label }.value

    @Test
    fun `moving time is the total less the pauses, and the average is over the moving`() {
        // An hour out, with a twenty-minute lunch and a ten-minute stop: fifty minutes moving.
        val recording = trip(
            minutes = 60,
            breaks = listOf(
                RecordingBreak(fromEpochMs = start + 10 * 60_000L, toEpochMs = start + 30 * 60_000L),
                RecordingBreak(fromEpochMs = start + 40 * 60_000L, toEpochMs = start + 50 * 60_000L)
            )
        )

        val rows = figuresOf(recording, elevation = null)

        assertEquals("1h 00m", value(rows, "Total time"))
        assertEquals("30m 00s", value(rows, "Moving"))
        // Five kilometres in thirty minutes is ten kilometres an hour.
        assertEquals("10.0 km/h", value(rows, "Average"))
    }

    @Test
    fun `a break that never ended runs to the end of the recording`() {
        // Finished while paused: the stop runs from the pause to the end.
        val recording = trip(
            minutes = 60,
            breaks = listOf(RecordingBreak(fromEpochMs = start + 45 * 60_000L, toEpochMs = null))
        )

        val rows = figuresOf(recording, elevation = null)

        assertEquals("45m 00s", value(rows, "Moving"))
    }

    @Test
    fun `no pause at all is all moving, and no distance is no average rather than a zero`() {
        val recording = trip(minutes = 60)
        val standing = trip(minutes = 60, distanceM = 0.0)

        assertEquals("1h 00m", value(figuresOf(recording, elevation = null), "Moving"))
        assertEquals("\u2013", value(figuresOf(standing, elevation = null), "Average"))
    }

    @Test
    fun `climb and descent come from the stored heights, and say nothing where there are none`() {
        val climbing = trip(minutes = 60, heights = listOf(100.0, 400.0, 250.0, 300.0))
        val flat = trip(minutes = 60)

        val rows = figuresOf(climbing, ElevationProfile.of(climbing.points))
        assertEquals("350 m", value(rows, "Climb"))
        assertEquals("150 m", value(rows, "Descent"))

        val bare = figuresOf(flat, elevation = null)
        assertEquals("\u2013", value(bare, "Climb"))
        assertEquals("\u2013", value(bare, "Descent"))
    }

    @Test
    fun `the figures are the mock screens' three rows of two`() {
        val rows = figuresOf(trip(minutes = 60), elevation = null)

        assertEquals(3, rows.size)
        assertTrue("every row has two cells", rows.all { it.size == 2 })
        assertEquals(
            listOf("Distance", "Total time", "Moving", "Average", "Climb", "Descent"),
            rows.flatten().map { it.label }
        )
    }
}
