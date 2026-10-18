package nz.mckenzie.trailday.domain.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether deleting a trip should delete the place it was made for.
 *
 * The complaint that started this was simple and fair: delete a trip and its line stayed behind as a
 * "place", so the walker had to tidy up after the app. What is held still here is that tidying never takes
 * more than the trip: a place the walker made for themselves survives, and so does a place more than one
 * walk was made of.
 */
class WalkMadePlaceTest {

    @Test
    fun `a place a marked walk made goes with the walk`() {
        assertTrue(
            placeMadeByWalk(
                placeName = "Card test track",
                sessionName = "Card test track",
                placeSourceRef = recordingSourceRef(42),
                walksOfPlace = 1
            )
        )
    }

    @Test
    fun `a place walked more than once stays, whichever trip is deleted`() {
        assertFalse(
            "the second walk of a place is not the end of the place",
            placeMadeByWalk(
                placeName = "Card test track",
                sessionName = "Card test track",
                placeSourceRef = recordingSourceRef(42),
                walksOfPlace = 2
            )
        )
    }

    @Test
    fun `a place a walk was made for stays, because its trip is named after it with the date`() {
        // A trip made for a place is named "<place> - 12 Oct", never exactly the place's name, so the two
        // cannot be confused even before the mark existed.
        assertFalse(
            placeMadeByWalk(
                placeName = "Routeburn Track",
                sessionName = "Routeburn Track - 12 Oct",
                placeSourceRef = null,
                walksOfPlace = 1
            )
        )
    }

    @Test
    fun `a place made by a walk before the mark existed is recognised by its name`() {
        assertTrue(
            "the walk's own name, and no other walk of it",
            placeMadeByWalk(
                placeName = "Old walk",
                sessionName = "Old walk",
                placeSourceRef = null,
                walksOfPlace = 1
            )
        )
    }

    @Test
    fun `a marked place says so whatever its name`() {
        assertTrue(madeByWalk(recordingSourceRef(7)))
        assertTrue(madeByWalk(recordingSourceRef(1_234)))
        assertFalse("DOC's material is not a walk", madeByWalk("doc-huts:123"))
        assertFalse("and neither is a place with no mark", madeByWalk(null))
    }
}
