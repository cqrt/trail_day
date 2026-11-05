package nz.mckenzie.trailday.domain.recording

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reading a walk's followed lines back out of the database.
 *
 * Two columns hold them: the list, and the last of them for every reader that predates it. Rows written
 * before the list existed have only the one, and reading those as a walk of one line is the truth about
 * them rather than a fallback.
 */
class FollowedRefsTest {

    @Test
    fun `a walk of one line reads as one`() {
        assertEquals(listOf("doc-tracks:1"), followedRefs("doc-tracks:1", "doc-tracks:1"))
    }

    @Test
    fun `a walk of several reads in the order it walked them`() {
        assertEquals(
            listOf("doc-tracks:1", "other:a", "doc-tracks:2"),
            followedRefs("doc-tracks:2", "doc-tracks:1\nother:a\ndoc-tracks:2")
        )
    }

    @Test
    fun `a row written before the list existed reads as its one reference`() {
        assertEquals(listOf("doc-tracks:1"), followedRefs("doc-tracks:1", null))
    }

    @Test
    fun `a walk that followed nothing reads as nothing`() {
        assertEquals(emptyList<String>(), followedRefs(null, null))
        assertEquals(emptyList<String>(), followedRefs("  ", "\n\n"))
    }

    @Test
    fun `blanks around a stored reference are not part of it`() {
        assertEquals(listOf("other:a", "other:b"), followedRefs(null, " other:a \n\n other:b\n"))
    }
}
