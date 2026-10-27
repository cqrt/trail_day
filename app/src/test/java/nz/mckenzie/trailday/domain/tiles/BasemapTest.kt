package nz.mckenzie.trailday.domain.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What each basemap's facts have to be.
 *
 * These are not merely constants: every one of them is a licence term, a pointer at somebody
 * else's service, or the thing that keeps two sources' tiles apart on disk. A change here that
 * looks harmless - a suffix, a zoom, an attribution string - is the sort of change that is
 * noticed by a licence holder rather than by a compiler, so they are pinned.
 *
 * The LINZ entries are pinned to the app's own proxy rather than to LINZ's host, because that is
 * where they are fetched from now: the key lives in the Worker, so a URL that named LINZ directly
 * would be a URL with no key on it. See `worker-linz/README.md`.
 */
class BasemapTest {

    /** The one the tests have always named, so the aerial facts are pinned to their own values. */
    private val aerial = Basemap.LINZ_AERIAL

    private val osm = Basemap.OPENSTREETMAP

    private val topo = Basemap.TOPOGRAPHIC

    @Test
    fun `the topographic map is fetched from their host over https`() {
        assertEquals("https://tile.opentopomap.org/{z}/{x}/{y}.png", topo.tileTemplate())
        assertEquals("https://tile.opentopomap.org/14/1017/660.png", topo.tileUrl(14, 1017, 660))
    }

    @Test
    fun `the topographic map carries the credit its licence requires`() {
        // Their map is built from OpenStreetMap data and SRTM heights, and the licence asks for both
        // and for the map's own name - CC BY-SA.
        assertTrue(topo.attribution.contains("OpenStreetMap contributors"))
        assertTrue(topo.attribution.contains("SRTM"))
        assertTrue(topo.attribution.contains("OpenTopoMap"))
        assertTrue(topo.attribution.contains("CC BY-SA"))
        assertEquals("https://opentopomap.org/about", topo.attributionLink)
    }

    @Test
    fun `aerial tile template is the linz xyz path on the app's own proxy`() {
        assertEquals(
            "https://trailday-linz-proxy.le-mckenzies.workers.dev/v1/tiles/aerial/" +
                "WebMercatorQuad/{z}/{x}/{y}.webp",
            aerial.tileTemplate()
        )
    }

    @Test
    fun `aerial tile url is one tile of the same path`() {
        assertEquals(
            "https://trailday-linz-proxy.le-mckenzies.workers.dev/v1/tiles/aerial/" +
                "WebMercatorQuad/14/1017/660.webp",
            aerial.tileUrl(14, 1017, 660)
        )
    }

    @Test
    fun `topographic style url points at the hosted style json`() {
        assertTrue(Basemap.linzTopographicStyleUrl().endsWith("style/topographic.json"))
    }

    @Test
    fun `no url anywhere in the app carries a key`() {
        // The whole point of the proxy: if a key ever appears in one of these, it has been
        // smuggled back into the APK, where it would expire on every phone at once.
        Basemap.entries.forEach { basemap ->
            assertFalse(
                "${basemap.id}'s tile template must not carry a key",
                basemap.tileTemplate().contains("api=")
            )
            assertFalse(
                "${basemap.id}'s tile url must not carry a key",
                basemap.tileUrl(14, 1017, 660).contains("api=")
            )
        }
        assertFalse(Basemap.elevationTemplate().contains("api="))
        assertFalse(Basemap.elevationUrl(14, 1017, 660).contains("api="))
        assertFalse(Basemap.demHillshadeTemplate().contains("api="))
        assertFalse(Basemap.demHillshadeUrl(14, 1017, 660).contains("api="))
        assertFalse(Basemap.linzTopographicStyleUrl().contains("api="))
    }

    @Test
    fun `every linz url is fetched from the app's proxy, not from linz`() {
        val host = LinzAccess.PROXY

        assertTrue(aerial.tileTemplate().startsWith("$host/"))
        assertTrue(Basemap.elevationTemplate().startsWith("$host/"))
        assertTrue(Basemap.demHillshadeTemplate().startsWith("$host/"))
        assertTrue(Basemap.linzTopographicStyleUrl().startsWith("$host/"))
        assertFalse(
            "and the drawn maps are still fetched from their own hosts",
            osm.tileTemplate().startsWith(host)
        )
    }

    @Test
    fun `a key of the walker's own sends every linz url to linz itself, with the key on it`() {
        // The other half of the same seam: the Developer options section's key. See [LinzAccess] for why
        // there are two hosts at all, and the Worker's README for why only one of them is the default.
        LinzAccess.use("c01abcDEF123")

        try {
            assertEquals(
                "${LinzAccess.LINZ_ORIGIN}/v1/tiles/aerial/WebMercatorQuad/{z}/{x}/{y}.webp" +
                    "?api=c01abcDEF123",
                aerial.tileTemplate()
            )
            assertEquals(
                "${LinzAccess.LINZ_ORIGIN}/v1/tiles/aerial/WebMercatorQuad/14/1017/660.webp" +
                    "?api=c01abcDEF123",
                aerial.tileUrl(14, 1017, 660)
            )
            // A path that carries a query of its own keeps it, and the key joins it rather than
            // replacing it: `pipeline=terrain-rgb` is what makes the elevation source an elevation one.
            assertEquals(
                "${LinzAccess.LINZ_ORIGIN}/v1/tiles/elevation/WebMercatorQuad/14/1017/660.png" +
                    "?pipeline=terrain-rgb&api=c01abcDEF123",
                Basemap.elevationUrl(14, 1017, 660)
            )
            // And a LINZ asset a style document names is moved with everything else.
            assertEquals(
                "${LinzAccess.LINZ_ORIGIN}/v1/sprites/topographic@2x.png?api=c01abcDEF123",
                LinzAccess.rewrite("${LinzAccess.LINZ_ORIGIN}/v1/sprites/topographic@2x.png")
            )
            assertEquals(
                "a drawn map's URL is nobody else's to rewrite",
                "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                LinzAccess.rewrite(osm.tileTemplate())
            )
        } finally {
            LinzAccess.use("")
        }
    }

    @Test
    fun `the drawn map is fetched from their host over https`() {
        assertEquals("https://tile.openstreetmap.org/{z}/{x}/{y}.png", osm.tileTemplate())
        assertEquals("https://tile.openstreetmap.org/14/1017/660.png", osm.tileUrl(14, 1017, 660))
    }

    @Test
    fun `the drawn map carries the credit and the link their guidelines require`() {
        assertEquals("\u00a9 OpenStreetMap contributors", osm.attribution)
        assertEquals("https://www.openstreetmap.org/copyright", osm.attributionLink)
    }

    @Test
    fun `the credit is single quoted so it drops into a style document unescaped`() {
        Basemap.entries.forEach { basemap ->
            val html = basemap.attributionHtml()

            assertTrue("${basemap.id} should link", html.startsWith("<a href='"))
            assertFalse("${basemap.id} should need no json escaping", html.contains("\""))
            assertTrue(html.contains(basemap.attribution))
            assertTrue(html.contains(basemap.attributionLink))
        }
    }

    @Test
    fun `only imagery may be fetched ahead of time`() {
        assertTrue(
            "offline areas download aerial imagery, so it has to be prefetchable",
            aerial.prefetchable
        )
        assertFalse(
            "OpenStreetMap's tile policy prohibits bulk downloading and offline packs",
            osm.prefetchable
        )
        assertFalse(
            "and the topographic map's is the same shape: looked at, never fetched ahead",
            topo.prefetchable
        )
        assertFalse(
            "and the LiDAR hillshade is downloaded as a pack of its own rather than as a basemap",
            Basemap.LINZ_TOPO_LIDAR_HILLSHADE.prefetchable
        )
    }

    @Test
    fun `each basemap names its own tile files, content type and zoom`() {
        assertEquals(".webp", aerial.tileSuffix)
        assertEquals("image/webp", aerial.contentType)
        assertEquals(22, aerial.maxZoom)

        assertEquals(".png", osm.tileSuffix)
        assertEquals("image/png", osm.contentType)
        assertEquals(19, osm.maxZoom)

        assertEquals(".png", topo.tileSuffix)
        assertEquals("image/png", topo.contentType)
        assertEquals(17, topo.maxZoom)

        // The tile server builds paths out of the id, so two sources cannot collide unless their
        // ids do.
        assertEquals(Basemap.entries.size, Basemap.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `every basemap says in the picker what it is, without mentioning a key`() {
        // The walker used to be told which maps needed a key they had to go and get. None does now,
        // so a summary that still said so would be telling them to fetch something they cannot use.
        Basemap.entries.forEach { basemap ->
            assertTrue("${basemap.id} should say something", basemap.summary.isNotBlank())
            assertFalse(
                "${basemap.id}'s summary must not mention a key",
                basemap.summary.contains("key", ignoreCase = true)
            )
        }
    }

    @Test
    fun `the LiDAR hillshade basemap loads LINZ's own vector style through the proxy`() {
        val hillshade = Basemap.LINZ_TOPO_LIDAR_HILLSHADE

        assertEquals(
            "https://trailday-linz-proxy.le-mckenzies.workers.dev/v1/tiles/topographic-v2/" +
                "WebMercatorQuad/style/topographic-v2.json",
            hillshade.hostedStyleUrl()
        )
        assertTrue(hillshade.usesHostedStyle)
        assertFalse(aerial.usesHostedStyle)
    }

    @Test
    fun `the LiDAR hillshade elevation is LINZ terrain-rgb, through the proxy`() {
        assertEquals(
            "https://trailday-linz-proxy.le-mckenzies.workers.dev/v1/tiles/elevation/" +
                "WebMercatorQuad/14/1017/660.png?pipeline=terrain-rgb",
            Basemap.elevationUrl(14, 1017, 660)
        )
        assertEquals(
            "https://trailday-linz-proxy.le-mckenzies.workers.dev/v1/tiles/elevation/" +
                "WebMercatorQuad/{z}/{x}/{y}.png?pipeline=terrain-rgb",
            Basemap.elevationTemplate()
        )
    }

    @Test
    fun `each basemap has a real preview tile of its own`() {
        assertTrue(aerial.previewTileUrl(12, 2015, 1280).contains("/aerial/"))
        assertTrue(osm.previewTileUrl(12, 2015, 1280).contains("tile.openstreetmap.org"))
        assertTrue(topo.previewTileUrl(12, 2015, 1280).contains("tile.opentopomap.org"))
        assertTrue(
            Basemap.LINZ_TOPO_LIDAR_HILLSHADE.previewTileUrl(12, 2015, 1280)
                .contains("/topo-raster/")
        )
    }

    @Test
    fun `the DEM hillshade is LINZ's own pre-rendered layer, through the proxy`() {
        assertEquals(
            "https://trailday-linz-proxy.le-mckenzies.workers.dev/v1/tiles/hillshade/" +
                "WebMercatorQuad/14/1017/660.webp",
            Basemap.demHillshadeUrl(14, 1017, 660)
        )
        assertEquals(
            "https://trailday-linz-proxy.le-mckenzies.workers.dev/v1/tiles/hillshade/" +
                "WebMercatorQuad/{z}/{x}/{y}.webp",
            Basemap.demHillshadeTemplate()
        )
    }

    @Test
    fun `the LiDAR hillshade basemap names the vector Topographic tiles`() {
        val hillshade = Basemap.LINZ_TOPO_LIDAR_HILLSHADE

        assertEquals(
            "https://trailday-linz-proxy.le-mckenzies.workers.dev/v1/tiles/topographic-v2/" +
                "WebMercatorQuad/14/1017/660.pbf",
            hillshade.tileUrl(14, 1017, 660)
        )
        assertEquals(".pbf", hillshade.tileSuffix)
        assertEquals("application/x-protobuf", hillshade.contentType)
        assertEquals(15, hillshade.maxZoom)
    }

    @Test
    fun `a stored value reads back as the basemap it names`() {
        assertEquals(aerial, Basemap.fromStorage("linz-aerial"))
        assertEquals(osm, Basemap.fromStorage("osm"))
    }

    @Test
    fun `an unknown or missing value reads as the default rather than failing`() {
        assertEquals(aerial, Basemap.fromStorage(null))
        assertEquals(aerial, Basemap.fromStorage(""))
        assertEquals(aerial, Basemap.fromStorage("terrain"))
        assertEquals(Basemap.DEFAULT, Basemap.fromStorage("something a later build wrote"))
    }
}
