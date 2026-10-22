package nz.mckenzie.trailday.domain.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What each of DOC's four services is, field by field.
 *
 * **These were read off the live services rather than assumed**, one request to each service's own
 * metadata, and they are pinned here because a field DOC renames is otherwise invisible: the reader
 * would keep working and a hut's card would quietly lose what it says about the hut. DOC warns a
 * replacement dataset is coming for these layers, so this test failing is the intended way to find out.
 *
 * The facts below are exactly what each service publishes today, and each list is written out in full
 * on purpose: a service that gains or loses a field is a change worth looking at, not a change worth
 * absorbing.
 */
class DocSourceTest {

    private val host = "https://services1.arcgis.com/3JjYDyG3oajxU6HO/arcgis/rest/services"

    /** Every name-and-value slot the replacement tracks service publishes. */
    private val characteristicSlots = listOf(2, 3, 4, 5, 6, 7, 8, 9, 14, 15)
        .flatMap { slot -> listOf("CharName$slot", "CharValue$slot") }

    @Test
    fun `each service is asked at its own folder on DOC's one host`() {
        assertEquals(
            "$host/DOC_Tracks_Open_Data/FeatureServer/0/query",
            DocSource.TRACKS.serviceUrl
        )
        assertEquals("$host/DOC_Huts/FeatureServer/0/query", DocSource.HUTS.serviceUrl)
        assertEquals("$host/DOC_Campsites/FeatureServer/0/query", DocSource.CAMPSITES.serviceUrl)
        assertEquals(
            "$host/DOC_Mountain_Bike_Tracks/FeatureServer/0/query",
            DocSource.BIKES.serviceUrl
        )
    }

    @Test
    fun `the tracks are lines, and the other three are places`() {
        assertEquals(
            "the tracks are drawn on the ground",
            DocShape.LINE,
            DocSource.TRACKS.shape
        )
        // Huts, campsites and bike locations are single points at DOC. The bike layer in particular is
        // where riding is permitted rather than geometry to follow - the app would be inventing a route
        // to draw it as a line.
        assertEquals(DocShape.POINT, DocSource.HUTS.shape)
        assertEquals(DocShape.POINT, DocSource.CAMPSITES.shape)
        assertEquals(DocShape.POINT, DocSource.BIKES.shape)
        assertTrue(DocSource.TRACKS.isLine)
        assertTrue(DocSource.entries.filter { !it.isLine }.size == 3)
    }

    @Test
    fun `a track's name is in TechObjectName and everything else calls it name`() {
        assertEquals("TechObjectName", DocSource.TRACKS.nameField)
        assertEquals("name", DocSource.HUTS.nameField)
        assertEquals("name", DocSource.CAMPSITES.nameField)
        assertEquals("name", DocSource.BIKES.nameField)
    }

    @Test
    fun `a hut's facts are the ones DOC_Huts publishes`() {
        assertEquals(
            listOf(
                "place", "region", "bookable", "facilities", "hasAlerts",
                "introductionThumbnail", "staticLink", "locationString"
            ),
            DocSource.HUTS.fields
        )
        // No bunk count, and there is none to have: DOC does not publish one for huts.
        assertTrue(
            "a hut has no bunk count to read",
            DocSource.HUTS.fields.none { it.contains("bunk", ignoreCase = true) }
        )
    }

    @Test
    fun `a campsite's facts are the ones DOC_Campsites publishes`() {
        assertEquals(
            listOf(
                "place", "region", "introduction", "campsiteCategory", "numberOfPoweredSites",
                "numberOfUnpoweredSites", "bookable", "free", "facilities", "activities",
                "dogsAllowed", "landscape", "access", "hasAlerts", "introductionThumbnail",
                "staticLink", "locationString"
            ),
            DocSource.CAMPSITES.fields
        )
    }

    @Test
    fun `a bike location's facts are the ones DOC_Mountain_Bike_Tracks publishes`() {
        assertEquals(
            listOf(
                "introduction", "difficulty", "bikingTime", "hasAlerts",
                "introductionThumbnail", "mountainBikingTrackWebPage"
            ),
            DocSource.BIKES.fields
        )
    }

    @Test
    fun `a track's facts are its own two and the slots the replacement service keeps characteristics in`() {
        assertEquals(listOf("SubObjectType", "FlocID") + characteristicSlots, DocSource.TRACKS.fields)
    }

    @Test
    fun `every service is asked for the key DOC numbers it with, and for its own name`() {
        DocSource.entries.forEach { source ->
            val fields = source.outFields.split(",")
            assertEquals(
                "$source is asked for the key first",
                DocSource.OBJECT_ID_FIELD,
                fields.first()
            )
            assertTrue("$source is asked for its own name field", source.nameField in fields)
            assertEquals(
                "$source asks for each field once",
                fields.size,
                fields.toSet().size
            )
        }
    }

    @Test
    fun `a stored id reads back as its source, and one this build never wrote is nothing`() {
        assertEquals(DocSource.TRACKS, DocSource.known("tracks"))
        assertEquals(DocSource.HUTS, DocSource.known("huts"))
        assertEquals(DocSource.CAMPSITES, DocSource.known("campsites"))
        assertEquals(DocSource.BIKES, DocSource.known("bikes"))

        // Null rather than a default: a cache row naming a source this build does not know is a row to
        // drop, not one to read as a track.
        assertNull(DocSource.known(null))
        assertNull(DocSource.known(""))
        assertNull(DocSource.known("DOC_Huts"))
        assertNull(DocSource.known("tracks "))
    }

    @Test
    fun `each service says which of its own facts are worth narrowing by`() {
        // The vocabulary the browser's Filter is built from, and the reason it is per source: a hut has
        // no "how it is walked" and a track has nobody to book with, so one service's filter would offer
        // the other three a chip that can never match.
        assertEquals(
            listOf(
                DocFilter("SubObjectType", "What it is"),
                DocFilter(DocSource.TRACK_TYPE_CHARACTERISTIC, "How it is walked")
            ),
            DocSource.TRACKS.filters
        )
        assertEquals(
            listOf(DocFilter("bookable", "Bookable"), DocFilter("region", "Region")),
            DocSource.HUTS.filters
        )
        assertEquals(
            listOf(
                DocFilter("campsiteCategory", "What it is"),
                DocFilter("bookable", "Bookable"),
                DocFilter("region", "Region")
            ),
            DocSource.CAMPSITES.filters
        )
        assertEquals(listOf(DocFilter("difficulty", "Difficulty")), DocSource.BIKES.filters)
    }

    @Test
    fun `a filter is on a fact the service publishes, and its label is words`() {
        DocSource.entries.forEach { source ->
            source.filters.forEach { filter ->
                assertTrue(
                    "$source filters on ${filter.field}, which it does not publish",
                    filter.field == DocSource.TRACK_TYPE_CHARACTERISTIC || filter.field in source.fields
                )
                assertTrue(
                    "a label is read by a walker: \"${filter.label}\"",
                    filter.label.isNotBlank() && filter.label.none { it == '_' }
                )
            }
        }
    }

    @Test
    fun `each source has an id of its own, which is what a cache row is keyed by`() {
        val ids = DocSource.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertTrue("\"$id\" is stored, so it stays simple: letters only", id.all { it.isLetter() })
        }
    }
}
