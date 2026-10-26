package nz.mckenzie.trailday.offline

import kotlinx.coroutines.runBlocking
import nz.mckenzie.trailday.domain.tiles.Basemap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * How LINZ's responses are interpreted.
 *
 * Driven against the real local tile server so the HTTP plumbing is exercised,
 * but without depending on the live service being reachable or having imagery
 * for a particular tile.
 */
class LinzTileFetcherTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var store: OfflineTileStore
    private lateinit var server: LocalTileServer
    private var port: Int = 0

    /**
     * Aerial imagery in one store, the drawn map in another: the same two sources the app runs,
     * so the tests exercise the same paths the map asks for.
     */
    private fun startServer(upstream: TileFetcher? = null) {
        store = OfflineTileStore(temp.root.resolve("imagery"), Basemap.LINZ_AERIAL.tileSuffix)
        server = LocalTileServer(
            listOf(
                TileSource(
                    id = Basemap.LINZ_AERIAL.id,
                    store = store,
                    suffix = Basemap.LINZ_AERIAL.tileSuffix,
                    contentType = Basemap.LINZ_AERIAL.contentType,
                    upstream = { upstream }
                )
            )
        )
        port = server.start()
    }

    @After
    fun tearDown() {
        // Some tests in this class need no server at all.
        if (::server.isInitialized) server.stop()
        // Every fetch through this fetcher is counted, so a test that left a run of failures behind
        // would be handing it to the next one.
        TileTrouble.reset()
    }

    /** A fetcher that talks to the local server instead of LINZ. */
    private fun fetcher() = LinzTileFetcher(
        urlFor = { zoom, x, y ->
            "http://127.0.0.1:$port/tiles/${Basemap.LINZ_AERIAL.id}/$zoom/$x/$y" +
                Basemap.LINZ_AERIAL.tileSuffix
        }
    )

    @Test
    fun `a served tile comes back as a tile`() = runBlocking {
        startServer()
        store.write(14, 1017, 660, byteArrayOf(4, 5, 6))

        val result = fetcher().fetch(14, 1017, 660)

        assertTrue(result is TileFetcher.Result.Tile)
        assertEquals(listOf<Byte>(4, 5, 6), (result as TileFetcher.Result.Tile).bytes.toList())
    }

    @Test
    fun `a 404 means the service has no imagery, not a failure`() = runBlocking {
        startServer(upstream = TileFetcher { _, _, _ -> TileFetcher.Result.NotFound })

        assertEquals(TileFetcher.Result.NotFound, fetcher().fetch(14, 1017, 660))
    }

    @Test
    fun `a bad gateway is reported as a failure with the code`() = runBlocking {
        startServer(upstream = TileFetcher { _, _, _ -> TileFetcher.Result.Failed("upstream down") })

        val result = fetcher().fetch(14, 1017, 660)

        assertTrue("expected a failure, got $result", result is TileFetcher.Result.Failed)
        assertTrue((result as TileFetcher.Result.Failed).message.contains("502"))
    }

    @Test
    fun `an unreachable host is a failure rather than an exception`() = runBlocking {
        // Port 1 on loopback: nothing is listening, so the connection is refused.
        val offline = LinzTileFetcher(
            urlFor = { _, _, _ -> "http://127.0.0.1:1/tiles/14/1017/660.webp" },
            connectTimeoutMs = 1_000,
            readTimeoutMs = 1_000
        )

        val result = offline.fetch(14, 1017, 660)

        assertTrue("expected a failure, got $result", result is TileFetcher.Result.Failed)
    }

    @Test
    fun `a tile that arrives clears the run of failures`() = runBlocking {
        startServer()
        store.write(14, 1017, 660, byteArrayOf(4, 5, 6))

        repeat(TileTrouble.RUN) { TileTrouble.failed() }
        assertEquals(TileTrouble.RUN, TileTrouble.consecutiveFailures.value)

        fetcher().fetch(14, 1017, 660)

        assertEquals(
            "the map is not in trouble once a tile has come back",
            0,
            TileTrouble.consecutiveFailures.value
        )
    }

    @Test
    fun `a 404 is the source answering, not a failure`() = runBlocking {
        // A walker panning over the sea gets these by the dozen. Counting them would put the notice on
        // screen over water with nothing wrong at all.
        startServer(upstream = TileFetcher { _, _, _ -> TileFetcher.Result.NotFound })

        repeat(TileTrouble.RUN) { TileTrouble.failed() }

        fetcher().fetch(14, 1017, 660)

        assertEquals(0, TileTrouble.consecutiveFailures.value)
    }

    @Test
    fun `a failure counts towards the run the notice waits for`() = runBlocking {
        startServer(upstream = TileFetcher { _, _, _ -> TileFetcher.Result.Failed("upstream down") })

        repeat(TileTrouble.RUN) { fetcher().fetch(14, 1017, 660) }

        assertEquals(TileTrouble.RUN, TileTrouble.consecutiveFailures.value)
        assertTrue(isTrouble(TileTrouble.consecutiveFailures.value, answered = false))
    }

    @Test
    fun `the default url is an unkeyed linz aerial tile on the app's proxy`() {
        val url = Basemap.LINZ_AERIAL.tileUrl(14, 1017, 660)

        assertEquals(
            "https://trailday-linz-proxy.le-mckenzies.workers.dev/v1/tiles/aerial/" +
                "WebMercatorQuad/14/1017/660.webp",
            url
        )
        assertFalse("a key must never reach the phone's own URLs", url.contains("api="))
    }
}
