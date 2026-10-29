package nz.mckenzie.trailday.offline

import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.domain.other.OtherTrackArea
import nz.mckenzie.trailday.other.OtherTracksDownloadResult
import nz.mckenzie.trailday.other.OtherTracksDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sentences a download of somebody else's tracks earns.
 *
 * Three things can be true of one and all three are worth saying: what arrived, that the service stopped
 * answering part-way, and that the box held more than one download keeps. The last two are the ones a
 * walker would otherwise find out on the hill, so they are held still here.
 */
class OtherTracksWordsTest {

    @Test
    fun `one track is one track, and several are tracks`() {
        assertEquals("1 track", otherTracksHeldWords(1))
        assertEquals("412 tracks", otherTracksHeldWords(412))
    }

    @Test
    fun `the card says how many places, how many tracks, and the room they take`() {
        // Not the regions' names: the rows underneath name every one of them, and a walker reading this
        // line is asking whether another region will fit.
        assertEquals("2 areas \u00b7 412 tracks \u00b7 9 MB", otherTracksSummary(2, 412, "9 MB"))
        assertEquals("1 area \u00b7 1 track \u00b7 0.4 MB", otherTracksSummary(1, 1, "0.4 MB"))
    }

    @Test
    fun `tracks with no named area are counted and not named`() {
        // Everything downloaded before this build was kept without a name, and inventing one would be
        // making it up - so the card says only how much is there.
        assertEquals("412 tracks", otherTracksSummary(0, 412, "9 MB"))
        assertEquals("Nothing downloaded yet", otherTracksSummary(0, 0, "0 B"))
    }

    @Test
    fun `an area's own row says how many tracks came back`() {
        assertEquals("1 track", otherTrackAreaWords(area(7, "Home", 1)))
        assertEquals("412 tracks", otherTrackAreaWords(area(7, "Home", 412)))
    }

    @Test
    fun `an area with nothing in it says so`() {
        assertEquals("Nothing was in that area.", otherTracksDownloadedMessage(done(0)))
    }

    @Test
    fun `what arrived is counted in the words the card uses`() {
        assertEquals("Kept 412 tracks.", otherTracksDownloadedMessage(done(412)))
        assertEquals("Kept 1 track.", otherTracksDownloadedMessage(done(1)))
    }

    @Test
    fun `a download that stopped short says what arrived and why it stopped`() {
        val said = otherTracksDownloadedMessage(
            OtherTracksDownloadResult.Failed(
                message = "The tracks service answered HTTP 503. Try again later.",
                tracks = tracks(1200)
            )
        )

        assertTrue(said, said.startsWith("Kept 1200 tracks."))
        assertTrue("the reason a walker would otherwise find out on the hill", said.contains("HTTP 503"))
    }

    @Test
    fun `a box that reached the ceiling says there is more behind it`() {
        // A truncated download that reads as a complete one is the app telling a walker there is no track
        // where there is one.
        val said = otherTracksDownloadedMessage(done(OtherTracksDownloader.MAX_TRACKS))

        assertTrue(said, said.contains("narrow the area"))
    }

    private fun done(count: Int) = OtherTracksDownloadResult.Done(tracks(count))

    private fun tracks(count: Int): List<OtherTrack> = List(count) { index ->
        OtherTrack(globalId = "id-$index", name = "Track $index", paths = emptyList())
    }

    private fun areas(vararg names: String): List<OtherTrackArea> =
        names.mapIndexed { index, name -> area(index.toLong() + 1, name, 10) }

    private fun area(id: Long, name: String, count: Int) =
        OtherTrackArea(id = id, name = name, trackCount = count, downloadedAtEpochMs = 0L)
}
