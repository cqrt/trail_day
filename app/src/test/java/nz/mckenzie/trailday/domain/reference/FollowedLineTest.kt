package nz.mckenzie.trailday.domain.reference

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import nz.mckenzie.trailday.domain.look.LookTarget
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
 *
 * **A walk follows one line at a time and is remembered against every one it walked**, so the wording is
 * asked about a list: one DOC track reads as it always has, and a walk that went from a council's row to
 * DOC's names both rather than the last.
 */
class FollowedLineTest {

    private fun doc(id: Long = 35237557L) = FollowedRef("doc-tracks:$id")

    private fun other(id: String, publisher: String? = null) = FollowedRef("other:$id", publisher)

    @Test
    fun `DOC's material is DOC's`() {
        assertEquals("Following a DOC track", followedLineWords(listOf(doc())))
    }

    @Test
    fun `two of DOC's are DOC's tracks`() {
        assertEquals("Following DOC tracks", followedLineWords(listOf(doc(1), doc(2))))
    }

    @Test
    fun `a track from the dataset names the publisher holding it`() {
        assertEquals(
            "Following a track from Environment Southland Regional Council",
            followedLineWords(listOf(other("6c6bfda6", "Environment Southland Regional Council")))
        )
    }

    @Test
    fun `a track whose row has gone says somebody else's rather than a name nobody checked`() {
        assertEquals(
            "Following another publisher's track",
            followedLineWords(listOf(other("6c6bfda6")))
        )
    }

    @Test
    fun `a walk that changed publisher names both, each once`() {
        assertEquals(
            "Following tracks from Environment Canterbury and DOC",
            followedLineWords(
                listOf(other("a", "Environment Canterbury"), doc(), other("b", "Environment Canterbury"))
            )
        )
    }

    @Test
    fun `two tracks of one council are that council's tracks`() {
        assertEquals(
            "Following tracks from Environment Canterbury",
            followedLineWords(listOf(other("a", "Environment Canterbury"), other("b", "Environment Canterbury")))
        )
    }

    @Test
    fun `three publishers are listed in the order they were walked`() {
        assertEquals(
            "Following tracks from DOC, Environment Canterbury and another publisher",
            followedLineWords(listOf(doc(), other("a", "Environment Canterbury"), other("b")))
        )
    }

    @Test
    fun `a trip that followed no published line has nothing to say here`() {
        assertNull(followedLineWords(emptyList()))
    }

    @Test
    fun `the row a followed line is painted by is read from the same prefix`() {
        assertEquals(
            "DOC's tracks are drawn among the walker's own",
            LookTarget.TRACKS,
            followedLookTarget("doc-tracks:35237557")
        )
        assertEquals(
            "and somebody else's wears the row kept for them",
            LookTarget.OTHER_TRACKS,
            followedLookTarget("other:6c6bfda6-d011-4afa-b453-405029c47bfb")
        )
    }

    @Test
    fun `the dataset's own key is read back off the reference`() {
        assertEquals("6c6bfda6", otherTrackGlobalId("other:6c6bfda6"))
        assertNull("DOC's material is not the dataset's", otherTrackGlobalId("doc-tracks:35237557"))
        assertNull(otherTrackGlobalId(null))
    }

    @Test
    fun `the publishers come from the held rows, in one read of the cache`() = runBlocking {
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
            listOf("Environment Southland Regional Council", null, null),
            followedPublishers(
                listOf("other:6c6bfda6", "other:gone", "doc-tracks:35237557"),
                cache
            )
        )
        assertEquals(
            "a cache with nothing of the dataset's in it is not read at all",
            listOf(null),
            followedPublishers(listOf("doc-tracks:35237557"), ExplodingCache)
        )
    }

    /** A cache holding rows by key, standing in for a download. */
    private class HeldTracks(private val tracks: List<OtherTrack>) : OtherTracksCache {
        override suspend fun cached(): List<OtherTrack> = tracks
        override suspend fun count(): Int = tracks.size
        override fun observeCount() = flowOf(tracks.size)
        override suspend fun find(globalId: String): OtherTrack? = tracks.firstOrNull { it.globalId == globalId }
    }

    /** A cache that must not be read: DOC's references are not the dataset's to answer. */
    private object ExplodingCache : OtherTracksCache {
        override suspend fun cached(): List<OtherTrack> = error("a cache of another publisher's rows was read")
        override suspend fun count(): Int = error("a cache of another publisher's rows was read")
        override fun observeCount() = flowOf(0)
    }
}
