package nz.mckenzie.trailday.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The app's shapes: **the mock screens' radii**, in the one place Material reads them from.
 *
 * `medium` is the important one: a card in this app takes that role, so every card on every screen is the
 * mock screens' eighteen points from here on, rather than each screen choosing. The names are Material's
 * because that is what the components ask for; the values are the mock screens'.
 *
 * The smaller two are for the pieces inside a card - the tile behind a kind's glyph (see `KindTile`) and the
 * chip - and the larger two for sheets and dialogs.
 */
internal val TrailDayShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** The radius of the tile a kind's glyph sits in, and of the figure tiles: the mock screens' twelve. */
val TileCorner = RoundedCornerShape(14.dp)

/** How big that tile is: a gloved finger's target, and the mock screens' own measure. */
val TileSize = 44.dp
