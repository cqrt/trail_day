package nz.mckenzie.trailday

import nz.mckenzie.trailday.ui.screens.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The five tabs, and where each one goes.
 *
 * A tab's name is the destination it opens - that is the whole contract between the bar and the
 * activity - so a sixth tab added without a screen behind it would crash on its first tap. This holds
 * the pair together, and holds the order the bar draws: the map first, because that is where a walk
 * starts, and **Explore last**, where Offline used to be.
 *
 * The way back is held too, because a page reached from two doors is the one thing that can send a
 * walker somewhere they have never been: **Offline returns to whichever door it was opened through**.
 */
class TabNavigationTest {

    @Test
    fun `the bar holds five tabs, ending on Explore`() {
        assertEquals(
            listOf("Map", "Places", "Record", "Activities", "Explore"),
            Tab.entries.map { it.label }
        )
    }

    @Test
    fun `every tab has a screen of its own to open`() {
        Tab.entries.forEach { tab ->
            assertEquals("a tap on ${tab.label} opens its own screen", tab.name, destinationOf(tab).name)
        }
    }

    @Test
    fun `Offline is a page rather than a tab`() {
        assertNotEquals(
            "the imagery has a room of its own now, not a tab",
            "OFFLINE",
            Tab.entries.last().name
        )
        assertEquals("Explore took its place", "EXPLORE", Tab.entries.last().name)
    }

    @Test
    fun `the Offline page goes back to the door it was opened through`() {
        val fromMap = backFrom(
            from = Destination.OFFLINE,
            docDetailOrigin = Destination.EXPLORE,
            otherTrackDetailOrigin = Destination.EXPLORE,
            settingsOrigin = Destination.ASSETS,
            offlineOrigin = Destination.MAP
        )
        assertEquals(Destination.MAP, fromMap)

        val fromSettings = backFrom(
            from = Destination.OFFLINE,
            docDetailOrigin = Destination.EXPLORE,
            otherTrackDetailOrigin = Destination.EXPLORE,
            settingsOrigin = Destination.ASSETS,
            offlineOrigin = Destination.SETTINGS
        )
        assertEquals("Settings' own back arrow then takes the walker to the tab that asked", Destination.SETTINGS, fromSettings)
    }

    @Test
    fun `choosing an area goes back to the Offline page, not to the map`() {
        listOf(
            Destination.OFFLINE_PICKER,
            Destination.OFFLINE_DOC_PICKER,
            Destination.OFFLINE_OTHER_TRACKS_PICKER,
            Destination.OFFLINE_TOPO_PICKER
        ).forEach { picker ->
            assertEquals(
                "$picker is a page under Offline",
                Destination.OFFLINE,
                backFrom(
                    from = picker,
                    docDetailOrigin = Destination.EXPLORE,
                    otherTrackDetailOrigin = Destination.EXPLORE,
                    settingsOrigin = Destination.MAP,
                    offlineOrigin = Destination.MAP
                )
            )
        }
    }

    @Test
    fun `a publisher's thing goes back to Explore, where it was read`() {
        assertEquals(
            Destination.EXPLORE,
            backFrom(
                from = Destination.DOC_DETAIL,
                docDetailOrigin = Destination.EXPLORE,
                otherTrackDetailOrigin = Destination.EXPLORE,
                settingsOrigin = Destination.MAP,
                offlineOrigin = Destination.MAP
            )
        )
        assertEquals(
            Destination.EXPLORE,
            backFrom(
                from = Destination.OTHER_TRACK_DETAIL,
                docDetailOrigin = Destination.MAP,
                otherTrackDetailOrigin = Destination.EXPLORE,
                settingsOrigin = Destination.MAP,
                offlineOrigin = Destination.MAP
            )
        )
    }
}
