package nz.mckenzie.trailday.ui.theme

import androidx.compose.ui.unit.dp

/**
 * How high the things that float over a map sit above it.
 *
 * **The map is imagery**, and imagery is as often pale snow as dark water: a card or a pill with no
 * shadow melts into whatever is behind it, and the walker has to find the card before reading it. One
 * height for all of them - the round controls, the follow and pause pills, and the card at the foot of
 * the map - so they read as one layer above the ground rather than as stickers at different depths.
 *
 * Six points is a shadow you can see under a white card on a photograph without it becoming a border.
 */
object TrailDayElevation {

    /** A card or a pill floating over the map, or over a photograph inside a card. */
    val floating = 6.dp
}
