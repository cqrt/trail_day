package nz.mckenzie.trailday.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app's three light settings have to mean.
 *
 * The mapping is stored, so the ids are the contract: a preference written by this build has to read
 * back as the same answer tomorrow, and a value from a build that knows a choice this one does not
 * has to leave the app drawing rather than stop it. The default is pinned for a different reason -
 * following the phone is what every install did before there was a setting, and an install that has
 * never chosen must look exactly as it did.
 */
class ThemeChoiceTest {

    @Test
    fun `a stored id reads back as the choice it names`() {
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.fromStorage("system"))
        assertEquals(ThemeChoice.LIGHT, ThemeChoice.fromStorage("light"))
        assertEquals(ThemeChoice.DARK, ThemeChoice.fromStorage("dark"))
    }

    @Test
    fun `an unknown or missing value reads as the default rather than failing`() {
        assertEquals(ThemeChoice.DEFAULT, ThemeChoice.fromStorage(null))
        assertEquals(ThemeChoice.DEFAULT, ThemeChoice.fromStorage(""))
        assertEquals(ThemeChoice.DEFAULT, ThemeChoice.fromStorage("sepia"))
        assertEquals(ThemeChoice.DEFAULT, ThemeChoice.fromStorage("something a later build wrote"))
    }

    @Test
    fun `an untouched install follows the phone, as it always did`() {
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.DEFAULT)

        // The enum is what an install with nothing stored gets, so the default has to be the first
        // thing `fromStorage` answers when it has nothing to read.
        assertEquals(ThemeChoice.DEFAULT, ThemeChoice.fromStorage(null))
    }

    @Test
    fun `each choice names itself once, in a value fit for a preferences file`() {
        val ids = ThemeChoice.entries.map { it.id }

        assertEquals("two choices stored under one id would overwrite each other", ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertTrue(
                "\"$id\" is written to a preferences file, so it stays simple: letters only",
                id.isNotBlank() && id.all { it.isLetter() }
            )
        }
    }

    @Test
    fun `only following the phone is decided by the phone`() {
        assertTrue(ThemeChoice.SYSTEM.isDark(phoneInDark = true))
        assertFalse(ThemeChoice.SYSTEM.isDark(phoneInDark = false))

        // Said outright means said outright, in both directions: a phone set to dark does not
        // override "Light", which is the whole reason the choice is offered.
        assertFalse(ThemeChoice.LIGHT.isDark(phoneInDark = true))
        assertFalse(ThemeChoice.LIGHT.isDark(phoneInDark = false))
        assertTrue(ThemeChoice.DARK.isDark(phoneInDark = true))
        assertTrue(ThemeChoice.DARK.isDark(phoneInDark = false))
    }

    @Test
    fun `every choice has words for the picker`() {
        ThemeChoice.entries.forEach { choice ->
            assertTrue("${choice.id} needs a name", choice.displayName.isNotBlank())
            assertTrue("${choice.id} needs a line explaining it", choice.summary.isNotBlank())
        }
    }
}
