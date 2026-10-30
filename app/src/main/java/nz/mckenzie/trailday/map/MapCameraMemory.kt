package nz.mckenzie.trailday.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where a map was last looking: a point and a zoom, which is all a camera is here. */
data class MapCamera(val lat: Double, val lng: Double, val zoom: Double)

/**
 * The last camera the map settled on, kept for the whole app rather than for one screen.
 *
 * Two screens ask the same question of it now. The map asks so that leaving the tab and coming back -
 * or changing the basemap - opens on the ground the walker was reading rather than on the work or the
 * phone. The look screen asks because its preview is a sample of the colours drawn on **ground the
 * walker knows**: a swatch on a card says what a colour is, and the same line over the bush they were
 * looking at a moment ago says what it will look like.
 *
 * **Deliberately not written to preferences.** It is a comfort within one sitting, not a setting: an
 * app that opens on the last thing looked at rather than on the work would be one the walker has to
 * find their place in every morning.
 */
object MapCameraMemory {

    private val _current = MutableStateFlow<MapCamera?>(null)

    /** The last camera, or null before any map has settled - a cold start, in other words. */
    val current: StateFlow<MapCamera?> = _current

    /** A map has settled; keep where it settled. */
    fun remember(camera: MapCamera) {
        _current.value = camera
    }
}
