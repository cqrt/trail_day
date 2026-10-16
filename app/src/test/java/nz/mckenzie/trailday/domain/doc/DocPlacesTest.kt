package nz.mckenzie.trailday.domain.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The place a track's `FlocID` names.
 *
 * The map is the app's own - DOC publishes no key for its functional-location prefixes - so what is
 * held still here is that it reads case-insensitively, and that an unknown prefix gives no place rather
 * than a wrong one.
 */
class DocPlacesTest {

    @Test
    fun `a known prefix names a place, whatever its case`() {
        assertEquals("Catlins Coast", DocPlaces.placeFor("CATLNSCO-TK11"))
        assertEquals("Bluff Hill", DocPlaces.placeFor("bluffhil-TK02"))
    }

    @Test
    fun `an unknown prefix or none is no place rather than a wrong one`() {
        assertNull(DocPlaces.placeFor("SOMEWHERE-TK01"))
        assertNull(DocPlaces.placeFor("NOCH"))
        assertNull(DocPlaces.placeFor(null))
        assertNull(DocPlaces.placeFor(""))
    }
}
