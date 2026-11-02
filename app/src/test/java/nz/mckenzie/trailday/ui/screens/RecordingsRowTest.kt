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
        publisher: String? = null,
        distanceM: Double = 2350.0,
        durationMs: Long? = 750_000L,
        imported: Boolean = false
    ) = RecordingRow(
        id = 1L,
        name = "Home block 14 Sep",
        startedAtEpochMs = 0L,
        distanceM = distanceM,
        durationMs = durationMs,
        pointCount = 412,
        status = status,
        assetId = assetId,
        assetName = assetName,
        docRef = docRef,
        publisher = publisher,
        imported = imported
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
            "A new track",
            recordingAsset(row(assetId = null, assetName = null))
        )
    }

    @Test
    fun `a trip that followed one of DOC's lines says so`() {
        // Published material never becomes a place, so the row cannot say "For ..." - it says whose line it was.
        assertEquals(
            "Following a DOC track",
            recordingAsset(row(assetId = null, assetName = null, docRef = "doc-tracks:35237557"))
        )
    }

    @Test
    fun `a trip that followed somebody else's track does not call it DOC's`() {
        // Kew Bush Walkway is from the national tracks dataset and is nobody's in this app, so a walk of it
        // read "Following a DOC track" - the reference, not its presence, is what says whose line it was.
        assertEquals(
            "Following another publisher's track",
            recordingAsset(row(assetId = null, assetName = null, docRef = "other:kew-bush-walkway"))
        )
    }

    @Test
    fun `a trip that followed somebody else's track names the publisher when the row is held`() {
        assertEquals(
            "Following a track from Environment Southland Regional Council",
            recordingAsset(
                row(
                    assetId = null,
                    assetName = null,
                    docRef = "other:kew-bush-walkway",
                    publisher = "Environment Southland Regional Council"
                )
            )
        )
    }

    @Test
    fun `a walk that came in a file says where it came from`() {
        // An imported activity is an ordinary finished recording, so the one line that tells it apart is
        // the one that says it was not walked with this phone - where a walk of the walker's own with no
        // place says "A new track", which would be a lie about a file.
        assertEquals(
            "Imported from a file",
            recordingAsset(row(assetId = null, assetName = null, imported = true))
        )
    }

    @Test
    fun `a walk from a file with no times claims no length`() {
        // A GPX with no `<time>` in it says when it was brought in and not when it was walked, so the row
        // leaves the duration out rather than writing "0s" beside a distance.
        val detail = recordingDetail(row(durationMs = null))

        assertTrue("the distance is still there: $detail", detail.contains("2.35 km"))
        assertTrue("and the fixes: $detail", detail.contains("412 points"))
        assertTrue("but no duration at all: $detail", !detail.contains("0s"))
    }

    @Test
    fun `the list says what it holds, and one trip is its own sentence`() {
        assertEquals(
            "2 activities \u00b7 14.35 km between them",
            summaryOf(listOf(row(distanceM = 10_000.0), row(distanceM = 4_350.0)))
        )
        assertEquals(
            "a crowd of one is not a crowd",
            "1 activity \u00b7 2.35 km",
            summaryOf(listOf(row(distanceM = 2_350.0)))
        )
        assertEquals("no distance is no distance to say", "2 activities", summaryOf(listOf(row(distanceM = 0.0), row(distanceM = 0.0))))
        assertEquals("No activities", summaryOf(emptyList()))
    }
}
