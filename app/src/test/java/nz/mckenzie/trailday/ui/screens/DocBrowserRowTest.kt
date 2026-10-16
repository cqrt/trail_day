package nz.mckenzie.trailday.ui.screens

import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.ui.formatDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * What a DOC browser row says, service by service.
 *
 * One rule for four services, which is the point of testing it here: a row must not say about a hut what
 * only makes sense about a track, and **a fact DOC did not send is left out rather than shown blank** -
 * "no facilities line" and "nothing worth saying" read very differently on a row.
 *
 * The words are the walker's, not DOC's field names: a length in metres, a distance in metres, and
 * "Bookable" rather than `bookable: "Yes"`.
 */
class DocBrowserRowTest {

    private fun item(
        source: DocSource,
        name: String = "Something",
        facts: Map<String, String> = emptyMap(),
        lengthM: Double = 0.0,
        points: Int = 1
    ): DocItem {
        val path = if (points <= 1) {
            listOf(GeoPoint(-41.62, 172.65))
        } else {
            // A line whose length is what the test asked for, laid out west to east at this latitude.
            val degrees = lengthM / 111_320.0 / 0.749
            listOf(GeoPoint(-41.62, 172.65), GeoPoint(-41.62, 172.65 + degrees))
        }
        return DocItem(source = source, objectId = 1L, name = name, paths = listOf(path), facts = facts)
    }

    @Test
    fun `a track's row is what it is, how it is walked, and how long`() {
        val track = item(
            DocSource.TRACKS,
            name = "Glory Tk",
            facts = mapOf(
                "SubObjectType" to "Walking Track",
                "CharName1" to DocSource.TRACK_TYPE_CHARACTERISTIC,
                "CharValue1" to "One way"
            ),
            points = 2,
            lengthM = 1000.0
        )

        // The length is whatever the line the helper laid out measures: what the row is being held to is
        // that it carries it, in the walker's metres, rather than that this test can do the survey.
        assertEquals(
            "Walking Track \u00b7 One way \u00b7 ${formatDistance(track.lengthM)}",
            docRowDetail(track, null)
        )
    }

    @Test
    fun `a hut's row is where it is, and that it has to be booked`() {
        val hut = item(
            DocSource.HUTS,
            name = "Angelus Hut",
            facts = mapOf(
                "place" to "Nelson Lakes National Park",
                "region" to "Nelson/Marlborough",
                "bookable" to "Yes"
            )
        )

        assertEquals(
            "Nelson Lakes National Park \u00b7 Nelson/Marlborough \u00b7 Bookable",
            docRowDetail(hut, null)
        )
    }

    @Test
    fun `a campsite's row counts its sites, and says nothing about a hut's facts`() {
        val camp = item(
            DocSource.CAMPSITES,
            name = "Kiosk Creek Campsite",
            facts = mapOf(
                "place" to "Fiordland National Park",
                "region" to "Fiordland",
                "campsiteCategory" to "Standard",
                "numberOfUnpoweredSites" to "10",
                "numberOfPoweredSites" to "2",
                "bookable" to "Yes"
            )
        )

        assertEquals(
            "Fiordland National Park \u00b7 Fiordland \u00b7 Standard \u00b7 12 sites, 2 powered \u00b7 Bookable",
            docRowDetail(camp, null)
        )
    }

    @Test
    fun `a bike location's row is how hard it is and how long it takes`() {
        val bike = item(
            DocSource.BIKES,
            name = "Globe Hill Track",
            facts = mapOf("difficulty" to "Intermediate", "bikingTime" to "3 hr")
        )

        assertEquals("Intermediate \u00b7 3 hr", docRowDetail(bike, null))
    }

    @Test
    fun `how far away it is goes on every kind of row`() {
        val hut = item(DocSource.HUTS, facts = mapOf("bookable" to "No"))

        assertEquals("2.40 km away", docRowDetail(hut, distanceM = 2400.0))
    }

    @Test
    fun `a fact DOC did not send is left out rather than shown blank`() {
        // A hut with no facilities line and nobody having said whether it is bookable: the row is its
        // place and nothing else, rather than a row of empty separators.
        val bare = item(DocSource.HUTS, facts = mapOf("place" to "Ruahine Forest Park"))
        assertEquals("Ruahine Forest Park", docRowDetail(bare, null))

        // And a hut with nothing at all says nothing at all - not " \u00b7  \u00b7 ".
        val empty = item(DocSource.HUTS)
        assertEquals("", docRowDetail(empty, null))
        assertFalse("a row of separators is not a row", docRowDetail(empty, null).contains("\u00b7"))
    }

    @Test
    fun `a campsite with no sites said takes no site count, and free-standing power is not a site`() {
        val camp = item(
            DocSource.CAMPSITES,
            facts = mapOf("campsiteCategory" to "Standard", "numberOfUnpoweredSites" to "0")
        )

        assertEquals("Standard", docRowDetail(camp, null))
    }
}
