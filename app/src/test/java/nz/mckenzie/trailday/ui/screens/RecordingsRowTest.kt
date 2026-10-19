package nz.mckenzie.trailday.ui.screens

import nz.mckenzie.trailday.domain.recording.RecordingStatus
import nz.mckenzie.trailday.viewmodel.RecordingRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a trip's row says about it.
 *
 * A trip is the record of a walk or a ride, so the row has to be honest about two things
 * in particular: a session that is still running, and a trip whose place has since been
 * deleted. Saying nothing in either case makes the list read as a set of finished jobs,
 * which is exactly what it is not.
 */
class RecordingsRowTest {

    private fun row(
        status: RecordingStatus = RecordingStatus.FINISHED,
        assetId: Long? = 7L,
        assetName: String? = "Home block",
        docRef: String? = null,
        distanceM: Double = 2350.0
    ) = RecordingRow(
        id = 1L,
        name = "Home block 14 Sep",
        startedAtEpochMs = 0L,
        distanceM = distanceM,
        durationMs = 750_000L,
        pointCount = 412,
        status = status,
        assetId = assetId,
        assetName = assetName,
        docRef = docRef
    )

    @Test
    fun `a finished recording says how far it went, for how long, and on how many fixes`() {
        val detail = recordingDetail(row())

        assertTrue("the distance, in kilometres: $detail", detail.contains("2.35 km"))
        assertTrue("the elapsed time: $detail", detail.contains("12m 30s"))
        assertTrue("and the fixes kept: $detail", detail.contains("412 points"))
    }

    @Test
    fun `a recording is described as running until it is finished`() {
        assertEquals("still recording", recordingStatus(row(status = RecordingStatus.RECORDING)))
        assertEquals("paused", recordingStatus(row(status = RecordingStatus.PAUSED)))
    }

    @Test
    fun `a finished recording has nothing left to say about its state`() {
        assertNull(recordingStatus(row()))
    }

    @Test
    fun `a trip whose place has gone says so, rather than saying nothing`() {
        assertEquals(
            "Its place has since been deleted",
            recordingAsset(row(assetId = 7L, assetName = null))
        )
    }

    @Test
    fun `a trip made without a place does not claim its place was deleted`() {
        assertEquals(
            "A new line",
            recordingAsset(row(assetId = null, assetName = null))
        )
    }

    @Test
    fun `a trip that followed one of DOC's lines says so`() {
        // DOC's material never becomes a place, so the row cannot say "For ..." - it says whose line it was.
        assertEquals(
            "Following a DOC track",
            recordingAsset(row(assetId = null, assetName = null, docRef = "doc-tracks:35237557"))
        )
    }

    @Test
    fun `the list says what it holds, and one trip is its own sentence`() {
        assertEquals(
            "2 trips \u00b7 14.35 km between them",
            summaryOf(listOf(row(distanceM = 10_000.0), row(distanceM = 4_350.0)))
        )
        assertEquals(
            "a crowd of one is not a crowd",
            "1 trip \u00b7 2.35 km",
            summaryOf(listOf(row(distanceM = 2_350.0)))
        )
        assertEquals("no distance is no distance to say", "2 trips", summaryOf(listOf(row(distanceM = 0.0), row(distanceM = 0.0))))
        assertEquals("No trips", summaryOf(emptyList()))
    }
}
