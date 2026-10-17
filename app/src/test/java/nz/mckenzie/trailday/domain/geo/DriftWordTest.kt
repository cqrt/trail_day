package nz.mckenzie.trailday.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether the walker counts as off the line.
 *
 * The rule that matters is not the distance but **the gap between the two distances**: a line that goes in
 * and out of a clearing by ten metres either way must not make the word flicker, and coming back has to be
 * decisively closer than going off was.
 */
class DriftWordTest {

    @Test
    fun `a walker beside the line is on it`() {
        val word = DriftWord()

        assertFalse(word.isOff(0.0))
        assertFalse(word.isOff(100.0))
        assertFalse(word.isOff(149.0))
    }

    @Test
    fun `properly off the line is off, and stays off while they are out there`() {
        val word = DriftWord()

        assertTrue(word.isOff(150.0))
        assertTrue("further off is still off", word.isOff(400.0))
        assertTrue("and drifting about is still off", word.isOff(120.0))
        assertTrue("still not back", word.isOff(76.0))
    }

    @Test
    fun `coming properly back takes the word away, and a new drift brings it back`() {
        val word = DriftWord()

        assertTrue(word.isOff(200.0))
        assertFalse("back within half the distance", word.isOff(75.0))
        assertFalse("and still on it a moment later", word.isOff(90.0))
        assertTrue("until the next drift", word.isOff(160.0))
    }

    @Test
    fun `a new line starts the rule again`() {
        val word = DriftWord()

        assertTrue(word.isOff(180.0))
        word.reset()

        assertFalse("being on the new line is on it", word.isOff(100.0))
        assertTrue("and drifting off it is worth a word", word.isOff(180.0))
    }

    @Test
    fun `getting back is half of getting lost, which is what stops the word flickering`() {
        // Equal distances would flutter the word on and off at exactly the boundary, which is the noisiest
        // thing a warning can do.
        assertEquals(DriftWord.DRIFT_OFF_M / 2.0, DriftWord.BACK_ON_M, 0.0)
    }
}
