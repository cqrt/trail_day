package nz.mckenzie.trailday.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import nz.mckenzie.trailday.domain.doc.DocItem

/**
 * A track the walker has asked to see on the map from somewhere else.
 *
 * A place in the library and a track in DOC's browser are both read as lists, and what the walker
 * wants next is the ground under them. The map takes the request when it opens, frames the track and
 * puts its card up, so the list's tap leads to the map rather than to a page about the line.
 *
 * One request at a time: asking again replaces one that was never seen.
 */
sealed interface MapFocus {

    /** One of the walker's own places, by the id the list knows it by. */
    data class Place(val assetId: Long) : MapFocus

    /**
     * One of DOC's things, as the browser read it - and the bike point that belongs to it, when the
     * browser found one. The bike point travels with the track so a live result can say it is
     * bike-friendly before anything has been downloaded.
     */
    data class Doc(val item: DocItem, val bike: DocItem? = null) : MapFocus
}

object PendingMapFocus {

    private val _wanted = MutableStateFlow<MapFocus?>(null)

    fun offer(focus: MapFocus) {
        _wanted.value = focus
    }

    /** Takes the request for the map, leaving nothing behind for the next visit. */
    fun take(): MapFocus? = _wanted.value.also { _wanted.value = null }
}
