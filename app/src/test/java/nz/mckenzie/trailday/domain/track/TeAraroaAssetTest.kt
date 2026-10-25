package nz.mckenzie.trailday.domain.track

import nz.mckenzie.trailday.domain.tiles.TeAraroa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundled trail, held to what the app says it is.
 *
 * The asset is 700 KB of GeoJSON that no compiler looks at, so this is the only thing that would notice
 * it going missing, going empty, or being replaced by a file of somewhere else - and the only thing that
 * reads it through the same door the map's own layer is fed from. The file is read off the module's own
 * assets rather than through a Context, because that is what a unit test has.
 */
class TeAraroaAssetTest {

    private val asset = File("src/main/assets/${TeAraroa.ASSET}")

    @Test
    fun `the bundled trail is the whole route, read as the app reads it`() {
        assertTrue("the trail must be in the app's own assets: ${asset.absolutePath}", asset.isFile)

        val outcome = TrackInterchange.readFile(asset.readBytes())
        val tracks = (outcome as TrackInterchange.FileOutcome.Read).tracks

        assertEquals("one track per section, as the Trust publishes it", 466, tracks.size)
        assertEquals(
            "and every point of the file",
            28027,
            tracks.sumOf { track -> track.reading.paths.sumOf { it.size } }
        )
        assertEquals(
            "each section under the name the Trust gives it",
            "42 Traverse - SH 47",
            tracks.first().name
        )
        assertTrue(
            "every section is a line rather than a single point",
            tracks.all { track -> track.reading.paths.first().size >= 2 }
        )
        assertTrue(
            "no section was joined to another, which is the shape of the whole file",
            tracks.none { track -> track.reading.segmentsDidNotMeet }
        )
        assertTrue(
            "and all of it is on the earth, in New Zealand",
            tracks.all { track ->
                track.reading.paths.all { path ->
                    path.all { point -> point.lat in -48.0..-34.0 && point.lng in 166.0..179.0 }
                }
            }
        )
    }
}
