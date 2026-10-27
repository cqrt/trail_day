package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.domain.tiles.withMinimumSpan
import nz.mckenzie.trailday.map.OtherTracksGeoJson

/**
 * The facts behind one page about a track somebody else published.
 *
 * **Everything the page shows is already on the phone**, which is the whole difference between this page
 * and DOC's. A track's own fields arrived with the download that kept it, so there is nothing to fetch
 * and nothing to enrich: DOC's page asks its proxy for the website's summary and DOC's alert endpoint for
 * current notices, and this one has neither to ask. That is why this view model holds a track and a
 * basemap and nothing else.
 */
class OtherTrackViewModel(
    val track: OtherTrack,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val basemap: StateFlow<Basemap> = settingsRepository.basemap
        .stateIn(viewModelScope, SharingStarted.Eagerly, Basemap.DEFAULT)

    /**
     * The track's own line, as the map's second reference layer draws it.
     *
     * **Through the layer rather than beside it**, so that the page and the map agree about what somebody
     * else's track looks like: the page's own map switches that layer on and hands it this one line, so
     * the teal and the white casing are the layer's own and not a second choice made here.
     */
    val geoJson: String = OtherTracksGeoJson.build(listOf(track))

    /**
     * The box the map opens on: the line's own extent.
     *
     * [withMinimumSpan] because a track that is a few metres long - a link between two streets - would
     * otherwise open the map at a level where the walker sees one roof and no idea where they are.
     */
    val bounds: LatLngBounds? = track.paths.flatten()
        .takeIf { it.isNotEmpty() }
        ?.let { points ->
            LatLngBounds(
                minLat = points.minOf { it.lat },
                minLng = points.minOf { it.lng },
                maxLat = points.maxOf { it.lat },
                maxLng = points.maxOf { it.lng }
            ).withMinimumSpan()
        }

    companion object {
        fun factory(context: Context, track: OtherTrack): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    OtherTrackViewModel(
                        track = track,
                        settingsRepository = SettingsRepository(appContext)
                    )
                }
            }
        }
    }
}
