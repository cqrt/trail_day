package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.domain.look.LineWidthChoice
import nz.mckenzie.trailday.domain.look.LookColour
import nz.mckenzie.trailday.domain.look.LookTarget
import nz.mckenzie.trailday.domain.look.MapLook
import nz.mckenzie.trailday.domain.look.MarkerSizeChoice
import nz.mckenzie.trailday.domain.tiles.Basemap

/**
 * The look screen: **seven rows, and every tap is stored at once.**
 *
 * There is no Save. A colour is a thing the walker is looking at - on the preview above the rows and
 * on the map behind them - and a screen that asked them to confirm it would be a screen where the
 * picture is a proposal rather than the app.
 *
 * Every setter writes the **whole** look through the repository rather than the one field, because the
 * preference is stored as the differences from the default (see [MapLook.toStorage]) and a partial
 * write would need a read-modify-write of the same value it is trying to describe.
 *
 * The mutex is what keeps two taps in a row from losing one: each write reads the stored look, changes
 * its row, and writes it back, so the second tap has to see the first one's answer.
 */
class TracksAndPlacesViewModel(private val settings: SettingsRepository) : ViewModel() {

    /**
     * The look in force, followed from the phone rather than held here.
     *
     * Followed, because the map tab, the desk and the icons are already drawing this same value: a copy
     * in this screen could disagree with them for as long as the screen is open, and the screen is the
     * one place that must never be behind. [MapLook.DEFAULT] until the preference arrives, which is the
     * look an untouched install has anyway.
     */
    val look: StateFlow<MapLook> = settings.mapLook
        .stateIn(viewModelScope, SharingStarted.Eagerly, MapLook.DEFAULT)

    /** The basemap under the preview: the walker's own, since the sample is drawn over their map. */
    val basemap: StateFlow<Basemap> = settings.basemap
        .stateIn(viewModelScope, SharingStarted.Eagerly, Basemap.DEFAULT)

    /** One write at a time, so a second tap changes the look the first one stored. */
    private val writing = Mutex()

    fun setColour(target: LookTarget, colour: LookColour) =
        change { it.withColour(target, colour) }

    fun setWidth(target: LookTarget, width: LineWidthChoice) =
        change { it.withWidth(target, width) }

    fun setSize(target: LookTarget, size: MarkerSizeChoice) =
        change { it.withSize(target, size) }

    /** Every row back to how the app draws it, which is also the empty preference. */
    fun reset() = change { MapLook.DEFAULT }

    private fun change(transform: (MapLook) -> MapLook) {
        viewModelScope.launch {
            writing.withLock {
                settings.setMapLook(transform(settings.mapLook.first()))
            }
        }
    }

    companion object {

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer { TracksAndPlacesViewModel(SettingsRepository(appContext)) }
            }
        }
    }
}
