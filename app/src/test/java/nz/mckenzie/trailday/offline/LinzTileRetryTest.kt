package nz.mckenzie.trailday.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which tile failures are worth asking about again.
 *
 * The rule exists because a walker saw the "this map is not drawing" notice while their key worked and the
 * phone was online: a screenful of tiles failing once is not the same statement as a source that is not
 * answering, and a second request is what tells them apart.
 */
class LinzTileRetryTest {

    @Test
    fun `a tile that arrived is not a failure, so there is nothing to retry`() {
        assertFalse(worthRetrying(TileFetcher.Result.Tile(byteArrayOf(1, 2, 3))))
        assertFalse("the source answered, it just has no imagery there", worthRetrying(TileFetcher.Result.NotFound))
    }

    @Test
    fun `a connection, a timeout or an empty body is worth asking about again`() {
        listOf(
            "Connection reset",
            "timeout",
            "empty response",
            "HTTP 500",
            "HTTP 503",
            "rate limited by LINZ (HTTP 429)"
        ).forEach { message ->
            assertTrue("\"$message\" may pass on a second try", worthRetrying(TileFetcher.Result.Failed(message)))
        }
    }

    @Test
    fun `a key LINZ has refused reads the same three times, so it is not retried`() {
        assertFalse(
            worthRetrying(
                TileFetcher.Result.Failed("LINZ rejected the key (HTTP 400): it may have expired")
            )
        )
    }
}
