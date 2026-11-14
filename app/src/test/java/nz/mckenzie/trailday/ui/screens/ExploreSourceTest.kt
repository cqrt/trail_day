package nz.mckenzie.trailday.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The switch at the top of Explore.
 *
 * Two publishers, DOC first, and nothing else: what the tab is showing is one of these or the other, and
 * the labels are the words the walker reads on the switch. A third source added here without a browser
 * behind it would be a segment that switched to a blank page.
 */
class ExploreSourceTest {

    @Test
    fun `there are two sources, and DOC is the one it opens on`() {
        assertEquals(listOf("DOC", "Other tracks"), ExploreSource.entries.map { it.label })
        assertEquals("a walker opening Explore sees DOC first", ExploreSource.DOC, ExploreSource.entries.first())
    }

    @Test
    fun `each source is named for its publisher, not for what it lists`() {
        // The four kinds of DOC's material are a choice inside the DOC browser, so the switch above it
        // says who published what is below it rather than repeating one kind of it.
        ExploreSource.entries.forEach { source ->
            assertTrue(
                "${source.label} is a publisher's name",
                source.label == "DOC" || source.label == "Other tracks"
            )
        }
    }
}
