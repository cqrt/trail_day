package nz.mckenzie.trailday.ui

import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the map's card says about something DOC publishes, once it has been tapped.
 *
 * The row's summary is a glance and is tested beside this; the card opens the same material out, so what
 * is held still here is that it opens it out **without inventing anything**: DOC's word for whether a hut
 * must be booked is shown even when it is "No", a fact DOC did not send is left out, and DOC's prose is
 * trimmed rather than allowed to push the map off the screen.
 */
class DocCardFactsTest {

    private fun item(
        source: DocSource,
        name: String = "Something",
        facts: Map<String, String> = emptyMap(),
        points: List<GeoPoint> = listOf(GeoPoint(-41.62, 172.65))
    ) = DocItem(source = source, objectId = 1L, name = name, paths = listOf(points), facts = facts)

    /** A line running east from the given point, of roughly [lengthM] metres. */
    private fun lineOf(lengthM: Double): List<GeoPoint> {
        val degrees = lengthM / 111_320.0 / 0.749
        return listOf(GeoPoint(-41.62, 172.65), GeoPoint(-41.62, 172.65 + degrees))
    }

    @Test
    fun `a track's card says how it is walked and how long it is`() {
        val track = item(
            DocSource.TRACKS,
            name = "Routeburn Track",
            facts = mapOf(
                "SubObjectType" to "Great Walk",
                "CharName1" to DocSource.TRACK_TYPE_CHARACTERISTIC,
                "CharValue1" to "One way"
            ),
            points = lineOf(2_000.0)
        )

        assertEquals(
            listOf("Great Walk \u00b7 One way \u00b7 1.99 km"),
            docCardFacts(track)
        )
    }

    @Test
    fun `a hut's card says where it is, whether it must be booked, and what is there`() {
        val hut = item(
            DocSource.HUTS,
            name = "Angelus Hut",
            facts = mapOf(
                "place" to "Nelson Lakes National Park",
                "region" to "Nelson/Tasman",
                "bookable" to "No",
                "facilities" to "Water, toilets, heating",
                "dogsAllowed" to "No dogs",
                "introduction" to "<p>Beside a lake, and cold at night.</p>"
            )
        )

        assertEquals(
            listOf(
                "Nelson Lakes National Park \u00b7 Nelson/Tasman",
                // DOC's own answer, even the "No": a card that dropped it would make a hut look bookable.
                "Bookable: No",
                "Water, toilets, heating",
                "Dogs: No dogs"
            ),
            docCardFacts(hut)
        )
        assertEquals(
            "and DOC's prose, with its markup taken out",
            "Beside a lake, and cold at night.",
            docCardProse(hut)
        )
    }

    @Test
    fun `a campsite's card says how many sites it has and whether they are free`() {
        val camp = item(
            DocSource.CAMPSITES,
            name = "Avalanche Creek Shelter Campsite",
            facts = mapOf(
                "place" to "Arthur's Pass National Park",
                "campsiteCategory" to "Standard",
                "numberOfUnpoweredSites" to "12",
                "numberOfPoweredSites" to "2",
                "free" to "Yes"
            )
        )

        assertEquals(
            listOf(
                "Standard \u00b7 14 sites, 2 powered",
                "Arthur's Pass National Park",
                "Free: Yes"
            ),
            docCardFacts(camp)
        )
    }

    @Test
    fun `a fact DOC did not send is left out rather than shown blank`() {
        val bare = item(DocSource.BIKES, name = "A bike location")

        assertTrue("nothing to say is nothing to say", docCardFacts(bare).isEmpty())
        assertNull("and no prose to show", docCardProse(bare))
    }

    @Test
    fun `prose longer than a card is trimmed and says so`() {
        val long = item(
            DocSource.CAMPSITES,
            facts = mapOf("introduction" to "a".repeat(1_200))
        )

        val prose = docCardProse(long)!!
        assertEquals("trimmed to the card's length, with an ellipsis", 601, prose.length)
        assertTrue("and it says it goes on", prose.endsWith("\u2026"))
    }
}
