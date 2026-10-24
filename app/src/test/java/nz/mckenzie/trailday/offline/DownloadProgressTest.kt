package nz.mckenzie.trailday.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sentence a download in flight is described by, and the arithmetic under it.
 *
 * The rate and the time left are the parts worth pinning: both are **derived from elapsed time rather than
 * measured**, so the rules about when they may be claimed at all are the whole of their honesty - and the
 * sentence is read on three surfaces, so a wrong figure here is wrong in three places.
 */
class DownloadProgressTest {

    private fun progress(
        done: Int = 0,
        total: Int = 100,
        bytes: Long = 0L,
        elapsedMs: Long = 0L
    ) = DownloadProgress(
        pack = OfflinePack.IMAGERY,
        unit = "tiles",
        done = done,
        total = total,
        bytes = bytes,
        elapsedMs = elapsedMs
    )

    @Test
    fun `no rate is claimed before there is time behind it`() {
        val justStarted = progress(done = 4, elapsedMs = 300)

        assertNull("the first chunk has not earned a speed", justStarted.perSecond)
        assertNull("and there is nothing honest to say about time left", justStarted.remainingSeconds)
    }

    @Test
    fun `a rate and a time left are worked out once there is time behind them`() {
        val going = progress(done = 200, total = 1_000, elapsedMs = 10_000)

        assertEquals(20.0, going.perSecond!!, 0.001)
        assertEquals("800 tiles at 20 a second", 40L, going.remainingSeconds)
    }

    @Test
    fun `the sentence says what is known and leaves out what is not`() {
        assertEquals(
            "nothing but the count while it is still early",
            "4 of 100 tiles",
            downloadProgressWords(progress(done = 4, total = 100, elapsedMs = 200))
        )

        val later = downloadProgressWords(
            progress(done = 200, total = 1_000, bytes = 18_400_000, elapsedMs = 10_000)
        )
        assertTrue("how many", later.contains("200 of 1000 tiles"))
        assertTrue("how big", later.contains("MB"))
        assertTrue("how fast", later.contains("20 tiles a second"))
        assertTrue("and how long is left", later.contains("left"))
    }

    @Test
    fun `a phase is named when a pack has more than one part`() {
        val words = downloadProgressWords(progress(done = 5, total = 10).copy(phase = "the heights"))

        assertTrue("which half is running", words.startsWith("the heights: 5 of 10 tiles"))
    }

    @Test
    fun `a time left under a minute is said in seconds, not rounded up to one`() {
        // 20 tiles a second with 140 to go: seven seconds, and "about 1 min left" would be a lie rather
        // than a rough figure.
        val nearlyDone = progress(done = 860, total = 1_000, elapsedMs = 43_000)

        assertEquals(7L, nearlyDone.remainingSeconds)
        assertTrue(
            "seconds, not minutes",
            downloadProgressWords(nearlyDone).contains("about 7s left")
        )
    }

    @Test
    fun `a download that is short of its total does not read as finished`() {
        assertEquals(100, progress(done = 100, total = 100).percent)
        assertEquals(
            "99 while there is still something to wait for",
            99,
            progress(done = 99, total = 100).percent
        )
    }
}
