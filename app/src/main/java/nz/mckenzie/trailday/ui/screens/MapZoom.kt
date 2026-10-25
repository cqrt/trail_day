package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * How far one tap of a preview map's zoom buttons moves the camera.
 *
 * A whole level, which is what the map's own double-tap gives: a line too small to read is legible after a
 * tap or two rather than after ten, and a tap is still a nudge when the walker is already close.
 */
private const val ZOOM_STEP = 1.0

/**
 * The zoom a preview map's buttons have asked for, in the shape the map takes it: **a step**, and a count
 * that says it is a new request rather than the same one arriving twice.
 *
 * One thing rather than two pieces of state in every screen, because the three screens with a preview map
 * want exactly this and nothing else: a button in, a button out, and a request the map can tell apart from
 * the last one - which is what stops a tab change or a basemap change zooming again for a tap that is
 * already done.
 */
internal class MapZoomState {

    /** How far the camera is asked to move: a level closer when positive, a level further when negative. */
    var step by mutableDoubleStateOf(0.0)
        private set

    /** How many times a button has been tapped, which is what makes each one a request of its own. */
    var count by mutableIntStateOf(0)
        private set

    fun zoomIn() {
        step = ZOOM_STEP
        count += 1
    }

    fun zoomOut() {
        step = -ZOOM_STEP
        count += 1
    }
}

/** The zoom one screen's preview map is driven with. */
@Composable
internal fun rememberMapZoomState(): MapZoomState = remember { MapZoomState() }

/**
 * The two buttons a preview map is looked closer with.
 *
 * A preview cannot be dragged or pinched, so without these it could only be looked at from wherever it
 * framed itself - which for a long track is a line too small to read, and for a track that is one straight
 * leg is a line that runs off the screen. They are a step in and a step out, one tap at a time, in the part
 * of the map the back button and the credit strip leave free.
 *
 * The buttons are the app's own floating controls over a map, so a preview's zoom looks like every other
 * control laid over every other map.
 */
@Composable
internal fun MapZoomButtons(zoom: MapZoomState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RoundIconButton(glyph = IconGlyph.PLUS, description = "Zoom in", onClick = zoom::zoomIn)
        RoundIconButton(glyph = IconGlyph.MINUS, description = "Zoom out", onClick = zoom::zoomOut)
    }
}
