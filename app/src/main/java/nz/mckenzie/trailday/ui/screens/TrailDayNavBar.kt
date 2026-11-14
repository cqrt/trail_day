package nz.mckenzie.trailday.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import nz.mckenzie.trailday.ui.theme.onPaper

/**
 * The five places the work starts from.
 *
 * The map, the places you keep, recording a line, the trips that prove it, and **Explore**: what other
 * people publish, DOC's material and the tracks councils and trusts publish, browsed in one place.
 * Everything else - a drawn line, one place, a spot, an area being chosen, the offline page - is
 * opened from one of these and is not a tab.
 *
 * The names match MainActivity's destinations, which is how a tap down here becomes a
 * screen change up there without either file learning the other's vocabulary.
 */
enum class Tab(val label: String, val glyph: IconGlyph) {
    MAP("Map", IconGlyph.MAP),
    ASSETS("Places", IconGlyph.ASSETS),
    RECORD("Record", IconGlyph.RECORD),
    RECORDINGS("Activities", IconGlyph.RECORDINGS),
    EXPLORE("Explore", IconGlyph.COMPASS)
}

/**
 * The bar along the bottom of every tab.
 *
 * Material 3 Expressive's navigation bar rather than the full-height one: it is shallower,
 * and it marks the current tab with a pill behind its icon. This is the one expressive
 * component the stable material3 artifact exposes to applications - see the note in
 * gradle/libs.versions.toml for why the expressive theme and motion are not here yet.
 *
 * Each screen draws its own, inside its own `Scaffold`, so the bar is part of the screen
 * rather than pinned over it: the maps get to fill the space they are given.
 *
 * **The bar's paper is 80% solid**, the same paper the map's round controls wear: where a screen puts
 * the bar over the map it lets a fifth of the ground through, and where the bar sits under the page it
 * reads as solid because the page is behind it. The labels and the glyphs stay at full strength - see
 * [nz.mckenzie.trailday.ui.theme.TrailDayPaper] - so only the paper thins.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrailDayNavBar(current: Tab, onSelect: (Tab) -> Unit) {
    val paper = MaterialTheme.colorScheme.surfaceContainer
    ShortNavigationBar(
        containerColor = paper.onPaper(),
        // Named rather than left to the default's `contentColorFor(containerColor)`: that reads a
        // thinned colour it has no role for and the labels would fall back to what was around them.
        contentColor = MaterialTheme.colorScheme.contentColorFor(paper)
    ) {
        Tab.entries.forEach { tab ->
            ShortNavigationBarItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                icon = { AppIcon(tab.glyph) },
                label = { Text(tab.label) }
            )
        }
    }
}
