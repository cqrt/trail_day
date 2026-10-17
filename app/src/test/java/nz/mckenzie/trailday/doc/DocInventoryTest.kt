package nz.mckenzie.trailday.doc

import nz.mckenzie.trailday.domain.doc.DocSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What DOC says is in a box, before any of it is fetched.
 *
 * The counts are DOC's own and are the point: a walker deciding whether to take an area onto a phone
 * wants to know whether it is an afternoon's walk or half a national park. The size is the app's own
 * arithmetic on measured sizes, so what is pinned here is that it is arithmetic on the right things -
 * tracks weighing like tracks, and pictures counted at all.
 */
class DocInventoryTest {

    private fun inventory(
        records: Map<DocSource, Int> = emptyMap(),
        pictures: Int = 0,
        failed: Map<DocSource, String> = emptyMap()
    ) = DocInventory(records = records, pictures = pictures, failed = failed)

    @Test
    fun `the full picture is DOC's own counts, in DOC's own words and order`() {
        val said = whatDocHasIn(
            inventory(
                records = mapOf(
                    DocSource.BIKES to 93,
                    DocSource.HUTS to 38,
                    DocSource.TRACKS to 412,
                    DocSource.CAMPSITES to 6
                ),
                pictures = 62
            )
        )

        assertEquals("412 tracks, 38 huts, 6 campsites, 93 bike locations and 62 pictures", said)
    }

    @Test
    fun `one of a thing is named in the singular, and an empty service is left out`() {
        assertEquals(
            "1 track and 1 picture",
            whatDocHasIn(inventory(records = mapOf(DocSource.TRACKS to 1, DocSource.HUTS to 0), pictures = 1))
        )
    }

    @Test
    fun `two things are joined the way they are said out loud`() {
        assertEquals(
            "3 tracks and 2 huts",
            whatDocHasIn(inventory(records = mapOf(DocSource.TRACKS to 3, DocSource.HUTS to 2)))
        )
        assertEquals("1 hut", whatDocHasIn(inventory(records = mapOf(DocSource.HUTS to 1))))
    }

    @Test
    fun `a box DOC has nothing in says nothing rather than zero`() {
        assertEquals("nothing", whatDocHasIn(inventory(records = DocSource.entries.associateWith { 0 })))
    }

    @Test
    fun `the size is arithmetic on what a download actually weighs`() {
        assertEquals(
            "a track weighs like a track, a place like a place",
            DocSizes.TRACK_BYTES * 2 + DocSizes.PLACE_BYTES * 3 + DocSizes.BIKE_BYTES,
            inventory(
                records = mapOf(
                    DocSource.TRACKS to 2,
                    DocSource.HUTS to 2,
                    DocSource.CAMPSITES to 1,
                    DocSource.BIKES to 1
                )
            ).estimatedBytes
        )
        assertEquals(
            "and the pictures are most of what a hut costs",
            DocSizes.PLACE_BYTES + DocSizes.PICTURE_BYTES * 5,
            inventory(records = mapOf(DocSource.HUTS to 1), pictures = 5).estimatedBytes
        )
    }

    @Test
    fun `an answer missing a service is not the whole truth`() {
        assertTrue("all four answered", inventory(records = DocSource.entries.associateWith { 1 }).complete)
        assertFalse(
            "one did not, so no figure here is about the whole box",
            inventory(
                records = mapOf(DocSource.TRACKS to 1),
                failed = mapOf(DocSource.HUTS to "the huts are down")
            ).complete
        )
    }
}
