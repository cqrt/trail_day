package nz.mckenzie.trailday.domain.reference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule the recorder's chooser narrows both published lists by.
 *
 * The two things worth pinning: a name typed into the search finds a line **wherever it is**, and the
 * nearby list when nothing is typed is the nearby list rather than everything. Both were the whole
 * complaint behind the tracks the map already draws being absent from the chooser.
 */
class FollowChoicesTest {

    private data class Line(val name: String, val metres: Double?)

    private fun choices(query: String, vararg lines: Line): List<String> =
        lines.toList()
            .chooserChoices(query, { it.metres }, Line::name)
            .map { it.item.name }

    @Test
    fun nothingTypedOffersOnlyWhatIsNearby() {
        assertEquals(
            listOf("Kereru Track"),
            choices("", Line("Kereru Track", 1_200.0), Line("Somewhere Else Track", 60_000.0))
        )
    }

    @Test
    fun nothingTypedOffersNothingWhenThePhoneHasNoFix() {
        assertTrue(choices("", Line("Kereru Track", null)).isEmpty())
    }

    @Test
    fun aNameFindsALineWhereverItIs() {
        assertEquals(listOf("Somewhere Else Track"), choices("somewhere", Line("Somewhere Else Track", 60_000.0)))
    }

    @Test
    fun nearbyComesFirstAndWhatCannotBeMeasuredComesLast() {
        val metres = listOf(
            Line("Far Track", 800.0),
            Line("Unmeasurable Track", null),
            Line("Near Track", 40.0)
        )
            .chooserChoices("track", { it.metres }, Line::name)
            .map { it.item.name }

        assertEquals(listOf("Near Track", "Far Track", "Unmeasurable Track"), metres)
    }

    @Test
    fun onlyFiveLinesAreOffered() {
        val many = (1..9).map { Line("Track $it", it * 100.0) }.toTypedArray()
        assertEquals(CHOOSER_ROWS, choices("", *many).size)
    }

    @Test
    fun aLineJustOutsideTheReachIsNotOffered() {
        assertEquals(
            emptyList<String>(),
            choices("", Line("Edge Track", CHOOSER_NEARBY_M + 1.0))
        )
        assertEquals(
            listOf("Edge Track"),
            choices("", Line("Edge Track", CHOOSER_NEARBY_M))
        )
    }

    @Test
    fun theSearchIsCaseBlindAndIgnoresSurroundingSpace() {
        assertEquals(listOf("Kereru Track"), choices("  kereru ", Line("Kereru Track", 60_000.0)))
    }
}
