package nz.mckenzie.trailday.domain.look

import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which row a kind of thing is set by.
 *
 * This is the meeting point between what the app knows a thing *is* and what the walker is offered to
 * change about it, so it is the one place a hut could quietly start being set by the campsites' row.
 * Every kind has to land somewhere, and a line has to land on a line's row - a width control and a
 * marker size are not interchangeable.
 */
class LookTargetTest {

    @Test
    fun `every kind of thing is set by one row, and the row carries the right control`() {
        AssetKind.entries.forEach { kind ->
            val target = LookTarget.of(kind)

            val expected = if (kind.shape == AssetShape.POINT) LookControl.PLACE else LookControl.LINE
            assertEquals("${kind} is on a row that draws it with the wrong control", expected, target.control)
        }
    }

    @Test
    fun `the five kinds are set by five different rows`() {
        val rows = AssetKind.entries.map { LookTarget.of(it) }

        assertEquals("two kinds answering to one row would change together", rows.size, rows.toSet().size)
    }

    @Test
    fun `the other publishers have a row no kind answers to`() {
        // Other tracks is a publisher rather than a kind: nothing the app stores is one, and giving it
        // a kind would make it possible to create one.
        assertTrue("other tracks is a line's row", LookTarget.OTHER_TRACKS.control == LookControl.LINE)
        assertTrue(
            "no kind lands on other tracks",
            AssetKind.entries.none { LookTarget.of(it) == LookTarget.OTHER_TRACKS }
        )
    }

    @Test
    fun `the trail has a row no kind answers to either, and it is a line's`() {
        // The trail is a route rather than a kind or a publisher: it has a row because its colour and
        // weight are the walker's, and a line's row because a route is drawn with a width.
        assertTrue("the trail is a line's row", LookTarget.TE_ARAROA.control == LookControl.LINE)
        assertTrue(
            "no kind lands on the trail",
            AssetKind.entries.none { LookTarget.of(it) == LookTarget.TE_ARAROA }
        )
    }

    @Test
    fun `the lines and the places between them are every row`() {
        assertEquals(LookTarget.entries.size, LookTarget.LINES.size + LookTarget.PLACES.size)
        assertTrue("a row cannot be both", LookTarget.LINES.none { it in LookTarget.PLACES })
        assertEquals("four lines", 4, LookTarget.LINES.size)
        assertEquals("three places", 3, LookTarget.PLACES.size)
    }

    @Test
    fun `each row names itself once, in a value fit for a preferences file`() {
        val ids = LookTarget.entries.map { it.id }

        assertEquals("two rows under one id would overwrite each other", ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertTrue(
                "\"$id\" is written to a preferences file, so it stays to letters and dashes",
                id.isNotBlank() && id.all { it.isLetter() || it == '-' }
            )
        }
        LookTarget.entries.forEach { target ->
            assertTrue("${target.id} needs a name", target.displayName.isNotBlank())
        }
    }

    @Test
    fun `a stored id reads back as the row it names, and anything else as nothing`() {
        LookTarget.entries.forEach { target ->
            assertEquals(target, LookTarget.fromStorage(target.id))
        }
        assertNull(LookTarget.fromStorage("hovercrafts"))
        assertNull(LookTarget.fromStorage(""))
    }
}
