package nz.mckenzie.trailday.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nz.mckenzie.trailday.domain.reference.Followable

/**
 * A line the walker has asked to follow from somewhere other than the recorder.
 *
 * The map, the places list and a track's own page all offer "Follow this"; the screen changes to the
 * recorder, which takes the line as its target, so the walk starts from the screen that asks about walking
 * rather than from a list or a card. One pending line at a time: asking again replaces a line that was
 * never started.
 *
 * **A [Followable], not one publisher's own record**: DOC's tracks and the tracks councils and trusts
 * publish reach the recorder the same way, and the recorder needs only a reference, a name and a line.
 */
object PendingFollow {

    private val _wanted = MutableStateFlow<Followable?>(null)
    val wanted: StateFlow<Followable?> = _wanted

    fun offer(item: Followable) {
        _wanted.value = item
    }

    /** Takes the line for the recorder, leaving nothing behind for the next visit. */
    fun take(): Followable? = _wanted.value.also { _wanted.value = null }
}
