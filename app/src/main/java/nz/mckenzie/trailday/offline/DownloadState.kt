package nz.mckenzie.trailday.offline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The download in flight, if there is one, for the screens and the notification to read.
 *
 * A process-wide holder for the same reason `TrackingState` is one: **the thing doing the work is not the
 * thing showing it.** A download is started from the picker, watched from the offline screen, and reported
 * on from a notification, and threading one flow through all three would be plumbing that adds nothing -
 * there is one download at a time by design, and this is it.
 *
 * Written by whatever is doing the fetching, cleared when it stops, and read by anything that wants to say
 * how it is going. The sentence itself is [downloadProgressWords], so no reader has to work out its own.
 */
object DownloadState {

    private val _current = MutableStateFlow<DownloadProgress?>(null)

    /** What is being downloaded now, or null when nothing is. */
    val current: StateFlow<DownloadProgress?> = _current

    /** A download is under way: say how it is going. */
    fun report(progress: DownloadProgress) {
        _current.value = progress
    }

    /** It finished, or it stopped: there is nothing left to watch. */
    fun clear() {
        _current.value = null
    }
}
