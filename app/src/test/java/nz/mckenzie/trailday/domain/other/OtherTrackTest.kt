package nz.mckenzie.trailday.domain.other

import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One track from the national tracks dataset, and the app's own words for the codes it publishes.
 *
 * The choices worth holding still are that **a code the app has no word for is nothing at all** rather
 * than a word invented for it, that the walking grades are DOC's own so a walker's vocabulary does not
 * change between the two layers, and that the junk a merged dataset carries is not repeated as a fact
 * about a track.
 */
class OtherTrackTest {

    @Test
    fun `the walking grades are DOC's own words`() {
        // The dataset uses DOC's vocabulary for these, so a chip on one layer and a chip on the other say
        // the same thing about the same kind of track.
        assertEquals("Path", OtherTrackAccess.walk("1"))
        assertEquals("Walking Track", OtherTrackAccess.walk("3"))
        assertEquals("Challenging Tramping Track", OtherTrackAccess.walk("5"))
    }

    @Test
    fun `a code the app has no word for is nothing at all`() {
        // 0 is "not open to walking" and 98 is "permitted": neither is a grade, and a chip reading
        // "Permitted" under a track's name says less than no chip does. 99 is the dataset's "unknown".
        assertNull(OtherTrackAccess.walk("0"))
        assertNull(OtherTrackAccess.walk("98"))
        assertNull(OtherTrackAccess.walk("99"))
        assertNull(OtherTrackAccess.walk(null))
    }

    @Test
    fun `the junk a merged dataset carries is not repeated as a fact`() {
        // Measured against the live service: 435 rows of the app's own set carry a status inherited from a
        // supplier - "Com", "Stratford", a single space, "Closed for lambing" - and none of them is a
        // status. The page says nothing rather than repeating somebody's typo.
        assertNull(OtherTrackAccess.status("Com"))
        assertNull(OtherTrackAccess.status("Stratford"))
        assertNull(OtherTrackAccess.status(" "))
        assertNull(OtherTrackAccess.status("Closed for lambing"))
        assertEquals("Open", OtherTrackAccess.status("Open"))
        assertEquals("Temporarily closed", OtherTrackAccess.status("TempClosed"))
        assertEquals("Removed from use", OtherTrackAccess.status("Decommisioned"))
    }

    @Test
    fun `the dog rules and the wheelchair answer are values under a label`() {
        // Not sentences: a page shows "Dogs: On lead only" and a filter offers "On lead only" under a Dogs
        // heading, and a table of full sentences would have both of them saying "dogs" twice.
        assertEquals("Off lead", OtherTrackAccess.dogs("OffLeash"))
        assertEquals("On lead only", OtherTrackAccess.dogs("OnLeash"))
        assertEquals("Not allowed", OtherTrackAccess.dogs("Prohibited"))
        assertNull(OtherTrackAccess.dogs("Dogs prohibited"))
        assertEquals("Suitable", OtherTrackAccess.mobility("Yes"))
        assertEquals("Not suitable", OtherTrackAccess.mobility("No"))
        assertNull(OtherTrackAccess.mobility("Unknown"))
    }

    @Test
    fun `a row merged from two suppliers names both, each once`() {
        val track = track(
            "DataSupplier" to "Top of The South Biking Tracks, Top of The South Walking Tracks"
        )

        assertEquals(
            listOf("Top of The South Biking Tracks", "Top of The South Walking Tracks"),
            track.suppliers
        )
    }

    @Test
    fun `who looks after it is left off when the dataset does not say`() {
        // "Unknown" is on 79% of the app's set, and it is a value rather than a manager: a page that
        // printed it would be telling a walker that somebody called Unknown looks after the track.
        assertNull(track("ManagedBy" to "Unknown").managedBy)
        assertNull(track("ManagedBy" to "   ").managedBy)
        assertEquals(
            "Tasman District Council",
            track("ManagedBy" to "Tasman District Council").managedBy
        )
    }

    @Test
    fun `a description arrives as words rather than as markup`() {
        val track = track("Description" to "A loop through the <b>bush</b>")

        assertEquals("A loop through the bush", track.description)
    }

    @Test
    fun `only the one status that is not a warning reads as open`() {
        assertEquals(true, track("Status" to "Open").isOpen)
        assertEquals(false, track("Status" to "TempClosed").isOpen)
        assertNull("a status the app has no word for is not open", track("Status" to "Com").isOpen)
    }

    @Test
    fun `a track with no walking grade has none, rather than a chip saying unknown`() {
        assertNull(track("Walk" to "99").walkGrade)
        assertEquals("Path", track("Walk" to "1").walkGrade)
    }

    @Test
    fun `a fact of nothing but spaces is no fact`() {
        val track = track("ConditionsOfUse" to "   ", "WebLink" to "")

        assertNull(track.conditions)
        assertNull(track.webLink)
        assertTrue(track.suppliers.isEmpty())
    }

    private fun track(vararg facts: Pair<String, String>) = OtherTrack(
        globalId = "b050c00f-2d3e-47f5-beec-ef162c38c314",
        name = "Southern Walkway",
        paths = listOf(
            listOf(GeoPoint(lat = -41.2900, lng = 174.7800), GeoPoint(lat = -41.2950, lng = 174.7850))
        ),
        facts = facts.toMap()
    )
}
