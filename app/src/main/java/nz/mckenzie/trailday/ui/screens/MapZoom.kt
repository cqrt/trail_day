package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * How far one tap of a zoom key moves the camera.
 *
 * A whole level, which is what the map's own double-tap gives: a line too small to read is legible after a
 * tap or two rather than after ten, and a tap is still a nudge when the walker is already close.
 */
private const val ZOOM_STEP = 1.0

/** The key: a small translucent circle, so the map is read through it rather than hidden behind it. */
private val KEY_SIZE = 28.dp
private val KEY_GLYPH = 18.dp

/**
 * The room a key takes up, which is deliberately **not** the size of the circle drawn in it.
 *
 * What a walker sees and what a thumb has to find are two different sizes, and the design wants the first
 * small: the keys sit over a map held in a gloved hand, so the circle stays the size it was drawn at and the
 * area that answers a tap is as close to the 48 dp a touch target wants as that look allows. It is also what
 * sets the air between a key and the level, because the column lays out the targets rather than the circles.
 */
private val KEY_TOUCH = 40.dp

/**
 * What a caller pads a zoom control by, so its **circles** land on the app's usual 12 dp.
 *
 * Measured to the circle rather than to the target, because the target is invisible and the circle is what a
 * walker sees: padded by 12 dp as everything else is, the circles would sit 18 dp in and the control would
 * look as though it had drifted away from the edge of the screen.
 */
internal val KEY_EDGE_PADDING = 12.dp - (KEY_TOUCH - KEY_SIZE) / 2

/**
 * The grey the keys and the level are drawn in: near-black at 55%, so what is under them can still be read
 * through, and the white drawn on top of them can be read at all.
 */
private val KEY_GREY = Color(45, 45, 45, 140)

/** The level between the keys: white on the same grey, in a stadium the width of a key. */
private val LEVEL_WIDTH = 28.dp
private val LEVEL_HEIGHT = 20.dp
private val LEVEL_FONT = 12.sp

/**
 * The zoom a map's keys have asked for, and the level it is at, in the shape the map takes them.
 *
 * **A step and a count** rather than a level, because the keys are relative: the map is handed "a level
 * closer" and a count that says it is a new request rather than the same one arriving twice - which is what
 * stops a tab change or a basemap change zooming again for a tap that is already done.
 *
 * **And the level the map reports**, which is not the same thing as the step: the map is the only thing that
 * knows where its camera ended up, a fit lands on a fraction of a level, and a walker who pinches the map
 * itself has moved it without touching a key. One thing rather than three pieces of state in every screen,
 * because the four screens with these keys want exactly this.
 */
internal class MapZoomState {

    /** How far the camera is asked to move: a level closer when positive, a level further when negative. */
    var step by mutableDoubleStateOf(0.0)
        private set

    /** How many times a key has been tapped, which is what makes each one a request of its own. */
    var count by mutableIntStateOf(0)
        private set

    /**
     * The level the map is at, as the map last reported it, or null before it has said.
     *
     * Null rather than a guess: the level is the map's own answer, and a number invented here would be a
     * number the map then contradicts.
     */
    var level by mutableStateOf<Int?>(null)
        private set

    fun zoomIn() {
        step = ZOOM_STEP
        count += 1
    }

    fun zoomOut() {
        step = -ZOOM_STEP
        count += 1
    }

    /**
     * The map has settled at [zoom].
     *
     * Rounded to a whole level, because that is the number the keys move by and the number a walker thinks
     * in: a fit that lands on 13.7 is the level a walker calls 14, and 13.7 in a 28 dp stadium is three
     * characters of noise.
     */
    fun reported(zoom: Double) {
        level = zoom.roundToInt()
    }
}

/** The zoom one map is driven with. */
@Composable
internal fun rememberMapZoomState(): MapZoomState = remember { MapZoomState() }

/**
 * The keys a map is zoomed with, and the level it is at between them.
 *
 * Two small translucent circles with white glyphs - **drawn on the app's own 24-unit grid**, so a plus and a
 * minus sit on the centre of their circle rather than on a text baseline - and the level in white on the same
 * grey between them. Every map in the app that has them has them the same way: a preview, which cannot be
 * pinched, and the home map, where they are a second way to do what a pinch does.
 *
 * The whole column is keys and a label, so it is not itself clickable: a tap lands on one key or on neither.
 */
@Composable
internal fun MapZoomButtons(zoom: MapZoomState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        // **Centred, and this is not cosmetic.** The keys are laid out at the size a thumb can find (40 dp)
        // and the level at the size it is drawn (28 dp), so a column that started its children would set the
        // number 6 dp to the left of the two circles it sits between - which is exactly the kind of almost-
        // lined-up that reads as a mistake.
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ZoomKey(glyph = IconGlyph.PLUS, description = "Zoom in", onClick = zoom::zoomIn)
        ZoomLevel(zoom.level)
        ZoomKey(glyph = IconGlyph.MINUS, description = "Zoom out", onClick = zoom::zoomOut)
    }
}

/**
 * One key: the circle a walker sees, centred in the room a thumb has.
 *
 * The glyph is an [AppIcon] rather than a character, which is the whole of why it is centred: the app draws
 * its own shapes on a fixed grid, so a plus is a plus at any size, on the middle of the circle, with no
 * baseline and no font to be at the mercy of.
 */
@Composable
private fun ZoomKey(glyph: IconGlyph, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(KEY_TOUCH)
            // Clipped to a circle so the ripple a tap leaves is the shape of the thing that was tapped
            // rather than a square around it.
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(KEY_SIZE)
                .background(KEY_GREY, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                glyph = glyph,
                dimension = KEY_GLYPH,
                tint = Color.White,
                contentDescription = description
            )
        }
    }
}

/**
 * The level the map is at, between the two keys.
 *
 * The stadium is drawn whether or not there is a number in it, because it is what holds the keys apart: a
 * label that arrived and left would slide the keys up and down the screen every time the map was rebuilt.
 * Nothing is written until the map has said where it is, which is a moment after it opens.
 *
 * **The platform's font padding is off**, which is the one thing that makes a centred number look centred:
 * the room Android reserves above a line is for accents a level never has, and all it does here is push the
 * digits a pixel and a half down the middle of a stadium that is only twenty deep.
 */
@Composable
private fun ZoomLevel(level: Int?) {
    Box(
        modifier = Modifier
            .width(LEVEL_WIDTH)
            .height(LEVEL_HEIGHT)
            .background(KEY_GREY, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = level?.toString().orEmpty(),
            color = Color.White,
            style = TextStyle(
                fontSize = LEVEL_FONT,
                fontWeight = FontWeight.SemiBold,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
    }
}
