package nz.mckenzie.trailday.domain.reference

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.domain.other.OtherTracksCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Whose line a saved trip says it followed.
 *
 * The reference is the only thing a trip keeps of the line, and it names a publisher only by its prefix -
 * so what the walker reads is decided here, once, rather than in each screen that shows a trip.
 */
class FollowedLineTest {

    @Test
    fun `DOC's material is DOC's`() {
        assertEquals("Following a DOC track", followedLineWords("doc-tracks:35237557"))
    }

    @Test
    fun `a track from the dataset names the publisher holding it`() {
        assertEquals(
            "Following a track from Environment Southland Regional Council",
            followedLineWords("other:6c6bfda6-d011-4afa-b453-405029c47bfb", "Environment Southland Regional Council")
        )
    }

    @Test
    fun `a track whose row has gone says somebody else's rather than a name nobody checked`() {
        assertEquals("Following another publisher's track", followedLineWords("other:6c6bfda6"))
    }

    @Test
    fun `a trip that followed no published line has nothing to say here`() {
        assertNull(followedLineWords(null))
        assertNull(followedLineWords(null, "Environment Southland Regional Council"))
    }

    @Test
    fun `the dataset's own key is read back off the reference`() {
        assertEquals("6c6bfda6", otherTrackGlobalId("other:6c6bfda6"))
        assertNull("DOC's material is not the dataset's", otherTrackGlobalId("doc-tracks:35237557"))
        assertNull(otherTrackGlobalId(null))
    }

    @Test
    fun `the publisher comes from the held row, and nothing comes from a row that is not held`() = runBlocking {
        val cache = HeldTracks(
            listOf(
                OtherTrack(
                    globalId = "6c6bfda6",
                    name = "Kew Bush Walkway",
                    paths = emptyList(),
                    facts = mapOf("DataSupplier" to "Environment Southland Regional Council")
                )
            )
        )

        assertEquals(
            "Environment Southland Regional Council",
            followedPublisher("other:6c6bfda6", cache)
        )
        assertNull("a row the phone no longer holds names nobody", followedPublisher("other:gone", cache))
        assertNull("and DOC's own material is asked of DOC, not of this cache", followedPublisher("doc-tracks:35237557", cache))
    }

    /** A cache holding rows by key, standing in for a download. */
    private class HeldTracks(private val tracks: List<OtherTrack>) : OtherTracksCache {
        override suspend fun cached(): List<OtherTrack> = tracks
        override suspend fun count(): Int = tracks.size
        override fun observeCount() = flowOf(tracks.size)
        override suspend fun find(globalId: String): OtherTrack? = tracks.firstOrNull { it.globalId == globalId }
    }
}
