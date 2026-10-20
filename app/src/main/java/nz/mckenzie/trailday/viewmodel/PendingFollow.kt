package nz.mckenzie.trailday.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nz.mckenzie.trailday.domain.doc.DocItem

/**
 * A line of DOC's the walker has asked to follow from somewhere other than the recorder.
 *
 * The map's card and the places list both offer "Follow this"; the screen changes to the recorder, which takes
 * the line as its target, so the walk starts from the screen that asks about walking rather than from a list or
 * a map card. One pending line at a time: asking again replaces a line that was never started.
 */
object PendingFollow {

    private val _wanted = MutableStateFlow<DocItem?>(null)
    val wanted: StateFlow<DocItem?> = _wanted

    fun offer(item: DocItem) {
        _wanted.value = item
    }

    /** Takes the line for the recorder, leaving nothing behind for the next visit. */
    fun take(): DocItem? = _wanted.value.also { _wanted.value = null }
}
