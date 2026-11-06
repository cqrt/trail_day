package nz.mckenzie.trailday.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The level the recorder's map was last left at.
 *
 * **One number, and not a place.** Every other map opens where the walker was reading, but the
 * recorder's map has a job: it frames the phone, or the work, every time it is made again. What it
 * cannot work out for itself is how close to be - a box says where, and a box around one person says
 * nothing about scale, so the fit lands on whatever the deepest useful level happens to be, and a
 * walker who had zoomed in on a creek came back to the whole valley.
 *
 * **Kept here rather than in the recorder's view model**, because following a place from its page
 * builds a new view model with a key of its own: a level kept there is forgotten at the one moment the
 * walker has just said where they are walking.
 *
 * **Deliberately not written to preferences**, for the same reason [MapCameraMemory] is not: a comfort
 * within one sitting, not a setting. An app that opens mid-creek rather than on the walker would be
 * one they have to find their place in every morning.
 */
object RecordZoomMemory {

    private val _level = MutableStateFlow<Double?>(null)

    /** The last level, or null before the recorder's map has settled - a cold start, in other words. */
    val level: StateFlow<Double?> = _level

    /** The map has settled; keep how close it was. */
    fun remember(level: Double) {
        _level.value = level
    }
}
