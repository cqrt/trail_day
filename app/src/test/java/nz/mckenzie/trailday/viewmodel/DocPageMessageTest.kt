package nz.mckenzie.trailday.viewmodel

import nz.mckenzie.trailday.domain.doc.DocQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line the browser says when DOC sent a full page.
 *
 * Worth pinning because **the two figures are not the same one**: the page the service sent is not the list
 * the walker can count, since the service is asked about a box and the search is a circle. A message quoting
 * the page reads as a miscount to somebody looking at fewer rows, which is what it was doing.
 */
class DocPageMessageTest {

    @Test
    fun `a short page is DOC saying that is all there is`() {
        assertNull(docPageMessage(sent = 241, shown = 241))
        assertNull("and nothing at all earns nothing", docPageMessage(sent = 0, shown = 0))
    }

    @Test
    fun `a full page that was shown whole says how many`() {
        assertEquals(
            "Showing the first ${DocQuery.DEFAULT_LIMIT} matches. Narrow the search to see the rest.",
            docPageMessage(sent = DocQuery.DEFAULT_LIMIT, shown = DocQuery.DEFAULT_LIMIT)
        )
    }

    @Test
    fun `a full page trimmed to the circle says both figures`() {
        val said = docPageMessage(sent = DocQuery.DEFAULT_LIMIT, shown = 241)!!

        assertTrue("what the walker can count", said.contains("241"))
        assertTrue("and the page it came out of", said.contains("${DocQuery.DEFAULT_LIMIT}"))
        assertTrue("with the way out", said.contains("Narrow the search"))
    }
}
