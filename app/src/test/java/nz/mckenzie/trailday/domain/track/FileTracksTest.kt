package nz.mckenzie.trailday.domain.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A file's tracks, read once and named, and the one question a file can raise.
 *
 * Reading used to be welded to writing places, which was fine while a file could only become places. It
 * can now become activities too, so the reading stops at the reading and the destination is the
 * caller's - and this holds the two apart: what a file *is*, and what to ask before writing it.
 */
class FileTracksTest {

    private fun gpx(vararg tracks: Pair<String?, String>) = buildString {
        append("""<?xml version="1.0"?><gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">""")
        tracks.forEach { (name, points) ->
            append("<trk>")
            name?.let { append("<name>$it</name>") }
            append("<trkseg>")
            points.split(" ")
                .filter { it.isNotBlank() }
                .forEach { pair ->
                    val (lat, lon) = pair.split(",")
                    append("""<trkpt lat="$lat" lon="$lon"><time>2026-11-03T09:00:00Z</time></trkpt>""")
                }
            append("</trkseg></trk>")
        }
        append("</gpx>")
    }.toByteArray()

    @Test
    fun `a file's own names are the names its tracks keep`() {
        val tracks = FileTracks.read(
            fileName = "Ride.gpx",
            bytes = gpx(
                "Morning Walk" to "-41.5,173.8 -41.6,173.9",
                "Lunch" to "-41.7,173.9 -41.8,174.0"
            )
        )

        assertEquals(
            "each track is called what the file calls it",
            listOf("Morning Walk", "Lunch"),
            tracks.map { it.name }
        )
    }

    @Test
    fun `a file whose tracks are unnamed names them after itself, and numbers them`() {
        val tracks = FileTracks.read(
            fileName = "Ride",
            bytes = gpx(
                null to "-41.5,173.8 -41.6,173.9",
                null to "-41.7,173.9 -41.8,174.0"
            )
        )

        assertEquals(
            "one file, two unnamed tracks, so they can still be told apart",
            listOf("Ride 1", "Ride 2"),
            tracks.map { it.name }
        )
    }

    @Test
    fun `a file with no track in it is refused in the reading's own words`() {
        try {
            FileTracks.read(fileName = "notes.gpx", bytes = "not a track at all".toByteArray())
            fail("a file that is not a track has to be refused")
        } catch (refused: IllegalArgumentException) {
            assertTrue(
                "the reading's own sentence, not a new one: ${refused.message}",
                !refused.message.isNullOrBlank()
            )
        }
    }

    @Test
    fun `the name a file is kept under drops its extension`() {
        assertEquals("Morning_Walk_2026-11-03", FileTracks.baseNameOf("Morning_Walk_2026-11-03.gpx"))
        assertEquals("no extension to drop", "Ride", FileTracks.baseNameOf("Ride"))
        assertEquals(
            "a file called only by its extension still needs a name",
            FileTracks.FALLBACK_NAME,
            FileTracks.baseNameOf(".gpx")
        )
    }

    @Test
    fun `only a file of several tracks sent to Activities is asked about`() {
        assertTrue(
            "four tracks could be a week of walks or four places",
            ImportDecision.asks(tracks = 4, destination = ImportDestination.ACTIVITIES)
        )
        assertFalse(
            "one track has one answer, which is an activity",
            ImportDecision.asks(tracks = 1, destination = ImportDestination.ACTIVITIES)
        )
        assertFalse(
            "every track in a file has always become a place, without a question",
            ImportDecision.asks(tracks = 4, destination = ImportDestination.PLACES)
        )
        assertFalse(
            "a file that has been answered is written, not asked about again",
            ImportDecision.asks(
                tracks = 4,
                destination = ImportDestination.ACTIVITIES,
                answered = ImportDestination.PLACES
            )
        )
    }
}
