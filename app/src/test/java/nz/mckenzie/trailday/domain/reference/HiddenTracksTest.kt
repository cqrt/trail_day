package nz.mckenzie.trailday.domain.reference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a phone remembers about the tracks it has been told are wrong.
 *
 * **The reference is the whole entry**, because everything else is a copy of something a publisher owns:
 * the same line arrives again with the next download of the area and with the next copy of the dataset,
 * and the only thing that survives both is the publisher's own number. The name is kept beside it for
 * the one place a walker reads the list, and it is never what a line is matched by.
 */
class HiddenTracksTest {

    private val wrongTrack = HiddenTrack("doc-tracks:35237557", "Godley Head Loop")

    @Test
    fun `nothing hidden hides nothing`() {
        assertFalse(HiddenTracks.NONE.hides("doc-tracks:35237557"))
        // A thing with no reference cannot have been hidden by name, and must not match by accident.
        assertFalse(HiddenTracks.NONE.hides(null))
    }

    @Test
    fun `a hidden track matches its own reference and no other`() {
        val hidden = HiddenTracks.NONE.hiding(wrongTrack)
        assertTrue(hidden.hides("doc-tracks:35237557"))
        // The number is per service, so the same number from another service is another thing.
        assertFalse(hidden.hides("doc-huts:35237557"))
        assertFalse(hidden.hides("other:35237557"))
    }

    @Test
    fun `hiding the same track twice is one entry`() {
        val hidden = HiddenTracks.NONE.hiding(wrongTrack).hiding(wrongTrack)
        assertEquals(1, hidden.entries.size)
    }

    @Test
    fun `two tracks with one name are two entries`() {
        val a = HiddenTrack("other:aaa", "Mill Track")
        val b = HiddenTrack("other:bbb", "Mill Track")
        val hidden = HiddenTracks.NONE.hiding(a).hiding(b)
        assertEquals(2, hidden.entries.size)
        assertTrue(hidden.hides("other:aaa"))
        assertTrue(hidden.hides("other:bbb"))
    }

    @Test
    fun `showing one back leaves the rest hidden`() {
        val hidden = HiddenTracks.NONE.hiding(wrongTrack).hiding(HiddenTrack("other:aaa", "Mill Track"))
        val after = hidden.showing("doc-tracks:35237557")
        assertFalse(after.hides("doc-tracks:35237557"))
        assertTrue(after.hides("other:aaa"))
    }

    @Test
    fun `showing one that is not hidden changes nothing`() {
        val hidden = HiddenTracks.NONE.hiding(wrongTrack)
        assertEquals(hidden, hidden.showing("other:not-there"))
    }

    @Test
    fun `what is written reads back as it went in`() {
        val hidden = HiddenTracks.NONE.hiding(wrongTrack)
        val written = HiddenTracks.write(hidden)
        assertEquals(1, written.size)
        assertEquals(hidden, HiddenTracks.read(written))
    }

    @Test
    fun `a name with a separator in it still reads as itself`() {
        // The separator is a unit separator, which a name will not hold - but a name that somehow did
        // must lose only its own reference, not split into a second entry.
        val awkward = HiddenTrack("other:aaa", "Mill\u001FTrack")
        assertEquals(awkward, HiddenTracks.read(HiddenTracks.write(HiddenTracks.NONE.hiding(awkward))).entries.single())
    }

    @Test
    fun `nothing hidden is written as nothing at all`() {
        assertTrue(HiddenTracks.write(HiddenTracks.NONE).isEmpty())
        assertTrue(HiddenTracks.read(null).isEmpty)
        assertTrue(HiddenTracks.read(emptySet()).isEmpty)
    }

    @Test
    fun `a reference on its own is still hidden`() {
        // Written by a build that kept no name: the line stays hidden rather than reappearing because the
        // entry could not be read.
        val hidden = HiddenTracks.read(setOf("doc-tracks:1"))
        assertTrue(hidden.hides("doc-tracks:1"))
        assertEquals("doc-tracks:1", hidden.entries.single().name)
    }

    @Test
    fun `a blank entry is dropped rather than kept as an empty reference`() {
        assertTrue(HiddenTracks.read(setOf("", "  ")).isEmpty)
    }
}
