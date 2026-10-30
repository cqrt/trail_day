package nz.mckenzie.trailday.domain.look

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.domain.asset.AssetKind

/**
 * The look in force, for the whole process: **what a hut is drawn in, asked once rather than carried.**
 *
 * A kind's colour is wanted by a dozen places that have no business knowing about a preference - the
 * list's tile, a place's page, the desk's rows, the map's marker pictures - and threading a look
 * through every one of them would be threading it through the whole app. So the look is followed in
 * one place, from the application at launch, the way the LINZ key is ([nz.mckenzie.trailday.domain
 * .tiles.LinzAccess]): a change lands there and everything that draws reads it.
 *
 * **This is the third object of its kind**, after [nz.mckenzie.trailday.domain.tiles.LinzAccess] and
 * [nz.mckenzie.trailday.tracking.DevicePosition], and the bar for a fourth is high. What earns this
 * one its place is that a colour is a global fact about the map rather than state any one screen owns.
 *
 * A screen that must *redraw* when it changes - the map, whose lines carry their colour in their own
 * document - collects [current]. Everything else just asks.
 */
object Look {

    private val _current = MutableStateFlow(MapLook.DEFAULT)

    /** The look in force. The default until the stored one arrives, which is a frame at most. */
    val current: StateFlow<MapLook> = _current.asStateFlow()

    /**
     * Follows the stored look for the life of the process, so a change takes effect at once.
     *
     * Called once, from the application, the way the LINZ key is.
     */
    fun follow(looks: Flow<MapLook>, scope: CoroutineScope) {
        scope.launch {
            looks.collect { stored -> _current.value = stored }
        }
    }

    /** The look in force, for code that draws once and is not watching. */
    val value: MapLook get() = _current.value

    /** The colour a [kind] of line or place is drawn in. */
    fun colourOf(kind: AssetKind): LookColour = value.colourOf(kind)

    /** The colour a row is drawn in - the other tracks' publisher has no kind of its own. */
    fun colourOf(target: LookTarget): LookColour = value.colourOf(target)

    /** A kind's colour at a light tone, for a glyph on a dark card. */
    fun colourOnDark(kind: AssetKind): String = value.colourOnDark(kind)

    /** A row's colour at a light tone. */
    fun colourOnDark(target: LookTarget): String = value.colourOnDark(target)

    /**
     * The colour a place is drawn in: **the recorded green when a walk has been made of it**, its
     * kind's own colour otherwise.
     *
     * A line that has been walked says so, whatever the walker has chosen for its kind. The green is
     * not on the shelf ([LookColour]) for exactly this reason.
     */
    fun colourOfPlace(kind: AssetKind, recorded: Boolean): String =
        if (recorded) RECORDED else colourOf(kind).hex

    /**
     * The recorded green, fixed on purpose: the same value as
     * [nz.mckenzie.trailday.map.AssetColors.RECORDED], and a test holds the two together so neither can
     * be changed without the other.
     */
    private const val RECORDED = "#2E7D32"
}
