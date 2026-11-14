package nz.mckenzie.trailday.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the **+** on Places offers.
 *
 * A page that is the walker's own places has one way in, and the sheet behind it has exactly three
 * choices: a spot, a file, a journey. What is deliberately not among them is somebody else's material -
 * DOC's and the other publisher's are read in Explore now - so this holds the three, their order, and the
 * absence of a fourth door out of the library.
 */
class PlacesAddSheetTest {

    @Test
    fun `the sheet offers a spot, a file and a journey, in that order`() {
        assertEquals(
            listOf("Add a spot", "Import a file", "Create a journey"),
            AddChoice.entries.map { it.label }
        )
    }

    @Test
    fun `each choice says what it brings`() {
        // The file row names the formats, because that is the question a walker holding a phone full of
        // downloads is actually asking.
        val import = AddChoice.entries.single { it.label == "Import a file" }
        assertTrue(import.detail.contains("GPX"))
        assertTrue(import.detail.contains("GeoJSON"))
        AddChoice.entries.forEach { choice ->
            assertFalse("${choice.label} says something", choice.detail.isBlank())
        }
    }

    @Test
    fun `no choice is a door to somebody else's material`() {
        val labels = AddChoice.entries.map { it.label.lowercase() }
        assertFalse("DOC is read in Explore", labels.any { it.contains("doc") })
        assertFalse("other tracks are read in Explore", labels.any { it.contains("other") })
    }
}
