package nz.mckenzie.trailday.offline

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the map decides the imagery has stopped arriving, and what it makes of the Worker's answer.
 *
 * The two halves of the notice that are not the card's words. The rule is the whole of the judgement -
 * a walker panning over the sea gets `404`s by the dozen and a phone on a bad connection drops the odd
 * tile, so *one* failure is never news - and the parse is the half that has to cope with an answer that
 * is not JSON at all, because a Worker over its daily allowance is answered by Cloudflare instead.
 */
class TileTroubleTest {

    @After
    fun tearDown() {
        TileTrouble.reset()
    }

    @Test
    fun `the first few failed tiles are not worth saying anything about`() {
        for (failures in 0 until TileTrouble.RUN) {
            assertFalse("$failures failures is a blip, not news", isTrouble(failures, answered = false))
        }
    }

    @Test
    fun `a run of failures is said out loud`() {
        assertTrue(isTrouble(TileTrouble.RUN, answered = false))
        assertTrue(isTrouble(TileTrouble.RUN * 4, answered = false))
    }

    @Test
    fun `a walker who has answered is not asked again while nothing arrives`() {
        assertFalse(isTrouble(TileTrouble.RUN, answered = true))
        assertFalse(isTrouble(TileTrouble.RUN * 100, answered = true))
    }

    @Test
    fun `a tile arriving clears the run and the walker's answer with it`() {
        repeat(TileTrouble.RUN) { TileTrouble.failed() }
        TileTrouble.answered()

        TileTrouble.arrived()

        assertEquals(0, TileTrouble.consecutiveFailures.value)
        assertFalse(
            "a source that goes again after coming back must be said again",
            isTrouble(TileTrouble.consecutiveFailures.value, answered = false)
        )
    }

    @Test
    fun `the source a notice is about changing forgets what was counted about the last one`() {
        repeat(TileTrouble.RUN * 2) { TileTrouble.failed() }

        TileTrouble.reset()

        assertEquals(0, TileTrouble.consecutiveFailures.value)
    }

    @Test
    fun `the worker's own sentence is what is shown`() {
        // The three answers the Worker gives about a key, as it gives them.
        assertEquals(
            "LINZ accepted the key",
            proxyMessage("""{"ok":true,"key":"ok","message":"LINZ accepted the key"}""")
        )
        assertEquals(
            "LINZ rejected the key (HTTP 403): it may have expired",
            proxyMessage(
                """{"ok":false,"key":"rejected","message":"LINZ rejected the key (HTTP 403): """ +
                    """it may have expired"}"""
            )
        )
        assertEquals(
            "The proxy has no LINZ API key configured",
            proxyMessage("""{"ok":false,"key":"missing","message":"The proxy has no LINZ API key configured"}""")
        )
    }

    @Test
    fun `an answer that is not an answer is nothing, rather than a crash`() {
        // What Cloudflare itself returns once the free plan's daily 100,000 requests are spent: the
        // Worker is never reached, so what arrives here is an HTML error page.
        assertNull(proxyMessage("<html><head><title>Error 1027</title></head></html>"))
        assertNull(proxyMessage(""))
        assertNull(proxyMessage("{}"))
        assertNull(proxyMessage("""{"ok":false,"key":"rejected"}"""))
        assertNull(proxyMessage("""{"message":"   "}"""))
    }
}
