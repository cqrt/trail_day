package nz.mckenzie.trailday.offline

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the fetcher makes of the answers a tile service actually gives.
 *
 * The one that matters here is **204 No Content**, which LINZ answers for a tile it publishes nothing for -
 * most of the vector Topographic map's deeper levels - and which used to be read as a failure. A download of
 * the drawn map then asked for every tile three times and sat at "0 of 981" for minutes.
 */
class LinzTileFetcherTest {

    private lateinit var server: HttpServer
    private var asks = 0

    /** A server that answers [failing] with 503 and every ask after that with [then]. */
    private fun serve(failing: Int, then: HttpResponse): String {
        server = HttpServer(
            host = "127.0.0.1",
            routes = listOf(
                HttpRoute(claims = { true }) {
                    asks++
                    if (asks <= failing) HttpResponse.text(503, "busy") else then
                }
            )
        )
        val port = server.start()
        return "http://127.0.0.1:$port/tiles/linz-topographic-v2/0/0/0.pbf"
    }

    @After
    fun tearDown() {
        if (::server.isInitialized) server.stop()
    }

    @Test
    fun `a tile the source has nothing for is not a failure, and is asked for once`() = runBlocking {
        val url = serve(failing = 0, then = HttpResponse.text(204, ""))
        val fetcher = LinzTileFetcher(urlFor = { _, _, _ -> url }, accept = "application/x-protobuf")

        val result = fetcher.fetch(0, 0, 0)

        assertEquals("204 means nothing here, which is an answer", TileFetcher.Result.NotFound, result)
        assertEquals("and it is not worth asking again", 1, asks)
    }

    @Test
    fun `a tile that arrives is a tile`() = runBlocking {
        val url = serve(failing = 0, then = HttpResponse.bytes(200, "application/x-protobuf", byteArrayOf(1, 2, 3)))
        val fetcher = LinzTileFetcher(urlFor = { _, _, _ -> url }, accept = "application/x-protobuf")

        val result = fetcher.fetch(0, 0, 0)

        assertTrue("a tile with the bytes that were sent", result is TileFetcher.Result.Tile)
        assertEquals(byteArrayOf(1, 2, 3).toList(), (result as TileFetcher.Result.Tile).bytes.toList())
        assertEquals(1, asks)
    }

    @Test
    fun `a server that is struggling is asked again, and the third ask is the one that lands`() = runBlocking {
        val url = serve(failing = 2, then = HttpResponse.bytes(200, "application/x-protobuf", byteArrayOf(7)))
        val fetcher = LinzTileFetcher(urlFor = { _, _, _ -> url }, accept = "application/x-protobuf")

        val result = fetcher.fetch(0, 0, 0)

        assertEquals(3, asks)
        assertTrue("the third ask lands a tile", result is TileFetcher.Result.Tile)
        assertEquals(byteArrayOf(7).toList(), (result as TileFetcher.Result.Tile).bytes.toList())
    }
}
