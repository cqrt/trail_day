package nz.mckenzie.trailday.domain.asset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a computer may take a track away, and what it says first.
 *
 * The one thing being held here is that the desk never deletes something a season's record hangs off.
 * The plan said the desk should archive instead of deleting (`active = false`); the app has no screen
 * that shows an archived asset and no way to bring one back, so that would be a quiet removal - see
 * [AssetRemovalRules]. The archive was dropped rather than waited for, so this is the whole of the rule:
 * nothing attached, delete it; anything attached, say how much and send the walker to the phone.
 */
class AssetRemovalRulesTest {

    @Test
    fun `a track with nothing recorded against it may be deleted, and the sentence says what goes`() {
        val removal = AssetRemovalRules.of("Estuary road", recordings = 0)

        assertTrue(removal.allowed)
        assertEquals(
            "Nothing is recorded against \"Estuary road\", so deleting it here takes nothing else with it.",
            removal.sentence
        )
    }

    @Test
    fun `a recording is enough to refuse, because the link is the only thing holding it`() {
        val removal = AssetRemovalRules.of("Old track", recordings = 1)

        assertFalse(removal.allowed)
        assertTrue("says what is on it: ${removal.sentence}", removal.sentence.contains("1 trip"))
    }

    @Test
    fun `several recordings are counted and said in the plural`() {
        val removal = AssetRemovalRules.of("Old track", recordings = 3)

        assertFalse(removal.allowed)
        assertTrue("says how many: ${removal.sentence}", removal.sentence.contains("3 trips"))
    }

    @Test
    fun `every refusal names the phone, because that is where the delete is done`() {
        listOf(1, 4).forEach { recordings ->
            val removal = AssetRemovalRules.of("Estuary road", recordings)

            assertFalse(removal.allowed)
            assertTrue(
                "a refusal has to say where to go: ${removal.sentence}",
                removal.sentence.contains("Delete it on the phone")
            )
        }
    }
}
