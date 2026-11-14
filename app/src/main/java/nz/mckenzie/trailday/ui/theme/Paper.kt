package nz.mckenzie.trailday.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * How much of what is behind a bar or a map control shows through it.
 *
 * **Twenty per cent see-through, eighty per cent solid.** The paper is what thins, not the ink: a bar
 * keeps its labels and a round control keeps its glyph at full strength, because the surface colour is
 * the only thing given an alpha. Made one number rather than a `.copy(alpha = 0.8f)` in four files, so
 * the tab bar and the map's controls cannot end up a shade apart.
 *
 * The bar sits over the map on the Map tab, where a fifth of the ground showing through is what says
 * the bar is above the map rather than beside it. On the other tabs the bar is drawn under the page,
 * so the same fifth reads as solid there.
 */
object TrailDayPaper {

    /** The alpha a surface is drawn at: 80% solid, 20% see-through. */
    const val ALPHA = 0.8f
}

/**
 * [this] colour as the paper a surface is drawn on the map with: the same colour, its alpha at
 * [TrailDayPaper.ALPHA].
 *
 * A function rather than four copies of `.copy(alpha = ...)`, because copying a colour and forgetting
 * the alpha somewhere is exactly how one of the four surfaces comes to read heavier than the rest.
 */
internal fun Color.onPaper(): Color = copy(alpha = TrailDayPaper.ALPHA)
