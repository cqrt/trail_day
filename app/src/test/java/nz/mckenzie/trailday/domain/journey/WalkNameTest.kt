package nz.mckenzie.trailday.domain.journey

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a walk that followed more than one track is called.
 *
 * The number that matters is three: a title is a line on a list, and a walk that changed over five times
 * has to stay a line rather than push its own figures off the screen.
 */
class WalkNameTest {

    private val date = "5 Nov 2026"

    @Test
    fun `one track reads as it always has`() {
        assertEquals("Major Hornbrook Track \u2013 $date", joinedWalkName(listOf("Major Hornbrook Track"), date))
    }

    @Test
    fun `two tracks are both named`() {
        assertEquals(
            "Major Hornbrook Track / Mount Pleasant Track \u2013 $date",
            joinedWalkName(listOf("Major Hornbrook Track", "Mount Pleasant Track"), date)
        )
    }

    @Test
    fun `three tracks are all named`() {
        assertEquals(
            "A / B / C \u2013 $date",
            joinedWalkName(listOf("A", "B", "C"), date)
        )
    }

    @Test
    fun `past three, the rest are counted`() {
        assertEquals(
            "A / B / C +1 more \u2013 $date",
            joinedWalkName(listOf("A", "B", "C", "D"), date)
        )
        assertEquals(
            "A / B / C +3 more \u2013 $date",
            joinedWalkName(listOf("A", "B", "C", "D", "E", "F"), date)
        )
    }

    @Test
    fun `a track walked twice is named once`() {
        assertEquals(
            "A / B \u2013 $date",
            joinedWalkName(listOf("A", "B", "A"), date)
        )
        assertEquals(
            "and a name spelled differently is the same name",
            "A / B \u2013 $date",
            joinedWalkName(listOf("A", "B", " a "), date)
        )
    }

    @Test
    fun `a walk whose tracks cannot be named is dated and nothing more`() {
        assertEquals("Track $date", joinedWalkName(emptyList(), date))
        assertEquals("Track $date", joinedWalkName(listOf("  ", ""), date))
    }
}
