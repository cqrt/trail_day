package nz.mckenzie.trailday.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The truth table behind the two DOC track switches.
 *
 * Tracks is the walkable base layer, Bike trails is the badge overlay and the bike-only fallback.
 * These four cases are the whole point: a dual-use track must survive Bike trails being off, and a
 * bike-only view still needs a line.
 */
class DocTrackVisibilityTest {

    @Test
    fun `tracks on, bike trails off shows every track and no badges`() {
        assertTrue(DocTrackVisibility.baseTracksShown(tracksShown = true))
        assertTrue(
            DocTrackVisibility.lineShown(
                tracksShown = true,
                bikeTrailsShown = false,
                bikeFriendly = true
            )
        )
        assertTrue(
            DocTrackVisibility.lineShown(
                tracksShown = true,
                bikeTrailsShown = false,
                bikeFriendly = false
            )
        )
        assertFalse(DocTrackVisibility.badgesShown(bikeTrailsShown = false))
        assertFalse(
            "the bike line layer stays quiet when the base line owns the drawing",
            DocTrackVisibility.bikeTracksShown(tracksShown = true, bikeTrailsShown = false)
        )
    }

    @Test
    fun `tracks on, bike trails on shows every track with badges, still drawn once`() {
        assertTrue(DocTrackVisibility.baseTracksShown(tracksShown = true))
        assertTrue(
            DocTrackVisibility.lineShown(
                tracksShown = true,
                bikeTrailsShown = true,
                bikeFriendly = true
            )
        )
        assertTrue(DocTrackVisibility.badgesShown(bikeTrailsShown = true))
        assertFalse(
            DocTrackVisibility.bikeTracksShown(tracksShown = true, bikeTrailsShown = true)
        )
    }

    @Test
    fun `tracks off, bike trails on shows only rideable tracks and badges`() {
        assertFalse(DocTrackVisibility.baseTracksShown(tracksShown = false))
        assertTrue(
            DocTrackVisibility.lineShown(
                tracksShown = false,
                bikeTrailsShown = true,
                bikeFriendly = true
            )
        )
        assertFalse(
            "a walking-only track is not part of the bike-only view",
            DocTrackVisibility.lineShown(
                tracksShown = false,
                bikeTrailsShown = true,
                bikeFriendly = false
            )
        )
        assertTrue(DocTrackVisibility.bikeTracksShown(tracksShown = false, bikeTrailsShown = true))
        assertTrue(DocTrackVisibility.badgesShown(bikeTrailsShown = true))
    }

    @Test
    fun `both off shows neither tracks nor badges`() {
        assertFalse(DocTrackVisibility.baseTracksShown(tracksShown = false))
        assertFalse(DocTrackVisibility.bikeTracksShown(tracksShown = false, bikeTrailsShown = false))
        assertFalse(DocTrackVisibility.badgesShown(bikeTrailsShown = false))
        assertFalse(
            DocTrackVisibility.lineShown(
                tracksShown = false,
                bikeTrailsShown = false,
                bikeFriendly = true
            )
        )
    }
}
