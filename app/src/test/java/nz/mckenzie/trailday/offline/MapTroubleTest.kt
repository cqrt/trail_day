package nz.mckenzie.trailday.offline

import nz.mckenzie.trailday.data.db.OfflineAreaEntity
import nz.mckenzie.trailday.domain.tiles.Basemap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the map says when it stops drawing, and what it offers.
 *
 * The words used to be one sentence whatever had happened, and the offer was OpenStreetMap - which
 * is the one thing that cannot draw with no reception. This holds the two judgements that replaced
 * it: **which of the five things happened**, and **what the walker can do about that one**. Also the
 * third judgement the notice rests on, which box of downloaded tiles covers the ground on screen.
 *
 * The composable draws what it is given, so everything worth getting wrong is here.
 */
class MapTroubleTest {

    @Test
    fun `being offline says so, and offers the map that is already on the phone`() {
        val words = troubleWords(
            MapTrouble.Offline(downloaded = Basemap.LINZ_AERIAL),
            Basemap.OPENSTREETMAP
        )

        assertEquals("You're offline", words.title)
        assertEquals(MapTroubleAction.SwitchTo(Basemap.LINZ_AERIAL), words.action)
        assertEquals("Use aerial imagery", actionLabel(words.action))
        assertTrue("should say what covers the spot: ${words.body}", words.body.contains("Aerial imagery"))
    }

    @Test
    fun `being offline with nothing downloaded sends the walker to what is`() {
        val words = troubleWords(MapTrouble.Offline(downloaded = null), Basemap.LINZ_AERIAL)

        assertEquals("You're offline", words.title)
        assertEquals(MapTroubleAction.OpenOfflineTab, words.action)
        assertEquals("What's downloaded", actionLabel(words.action))
        // **Never a basemap switch here.** Every one of the four maps fetches tiles it does not have,
        // so offering one to a phone with no reception is offering the same blank screen again.
        assertFalse(words.action is MapTroubleAction.SwitchTo)
    }

    @Test
    fun `the Worker's own sentence is repeated, with the drawn map under it`() {
        val words = troubleWords(
            troubleFrom(ProxyHealth.Answered("LINZ rejected the key (HTTP 403): it may have expired")),
            Basemap.LINZ_AERIAL
        )

        assertTrue(words.body.startsWith("LINZ rejected the key (HTTP 403)"))
        assertEquals(MapTroubleAction.SwitchTo(Basemap.OPENSTREETMAP), words.action)
        assertEquals("Use OpenStreetMap", actionLabel(words.action))
    }

    @Test
    fun `a Worker that did not answer is said to be at our end`() {
        val trouble = troubleFrom(ProxyHealth.Unreachable)
        val words = troubleWords(trouble, Basemap.LINZ_AERIAL)

        assertEquals(MapTrouble.ServiceSilent, trouble)
        assertTrue("should take the blame off the phone: ${words.body}", words.body.contains("at our end"))
        assertEquals(MapTroubleAction.SwitchTo(Basemap.OPENSTREETMAP), words.action)
    }

    @Test
    fun `a refused key points at settings, because that is where the key is`() {
        val words = troubleWords(
            troubleFrom(KeyCheck.Failed("LINZ rejected the key (HTTP 401): it may have expired")),
            Basemap.LINZ_AERIAL
        )

        assertEquals("Your LINZ key isn't working", words.title)
        assertEquals(MapTroubleAction.OpenSettings, words.action)
        assertEquals("Open Settings", actionLabel(words.action))
    }

    @Test
    fun `a key that was never asked about is not blamed`() {
        // A validated connection can still fail DNS or TLS, and that says nothing about the key: a
        // walker sent to Settings over it would be changing a key that is probably fine.
        assertEquals(
            MapTrouble.ServiceSilent,
            troubleFrom(KeyCheck.Unreachable("Unable to resolve host"))
        )
    }

    @Test
    fun `a key LINZ accepted is not a statement about the key`() {
        assertEquals(MapTrouble.ServiceSilent, troubleFrom(KeyCheck.Works))
    }

    @Test
    fun `a drawn map's silence offers the imagery, which comes another way`() {
        val words = troubleWords(MapTrouble.DrawnMapSilent, Basemap.OPENSTREETMAP)

        assertEquals("This map isn't drawing", words.title)
        assertEquals(MapTroubleAction.SwitchTo(Basemap.LINZ_AERIAL), words.action)
        assertEquals("Use aerial imagery", actionLabel(words.action))
    }

    @Test
    fun `a finished download covers the ground inside its box`() {
        val area = area(minZoom = 12, maxZoom = 16)

        assertTrue(area.covers(lat = -41.3, lng = 174.8, zoom = 14.0))
        // Finer than the deepest level downloaded: the renderer overzooms the deep tiles it has.
        assertTrue(area.covers(lat = -41.3, lng = 174.8, zoom = 18.0))
    }

    @Test
    fun `a download does not cover what it did not download`() {
        val area = area(minZoom = 12, maxZoom = 16)

        // Shallower than the box: those tiles were never fetched, and nothing can scale up to them.
        assertFalse(area.covers(lat = -41.3, lng = 174.8, zoom = 10.0))
        assertFalse("outside the box", area.covers(lat = -40.0, lng = 174.8, zoom = 14.0))
        assertFalse("outside the box", area.covers(lat = -41.3, lng = 172.0, zoom = 14.0))
    }

    @Test
    fun `an unfinished download is a promise, not tiles on the phone`() {
        val half = area(minZoom = 12, maxZoom = 16).copy(downloadedTiles = 40, completedAtEpochMs = null)

        assertFalse(half.covers(lat = -41.3, lng = 174.8, zoom = 14.0))
    }

    /** One finished area of imagery: a box around Wellington, at zooms 12 to 16 unless said otherwise. */
    private fun area(minZoom: Int, maxZoom: Int) = OfflineAreaEntity(
        name = "Wellington",
        pack = OfflinePack.IMAGERY.id,
        minLat = -41.4,
        minLng = 174.7,
        maxLat = -41.2,
        maxLng = 174.9,
        minZoom = minZoom,
        maxZoom = maxZoom,
        plannedTiles = 100,
        downloadedTiles = 100,
        bytes = 1_000,
        createdAtEpochMs = 0,
        completedAtEpochMs = 1
    )
}
