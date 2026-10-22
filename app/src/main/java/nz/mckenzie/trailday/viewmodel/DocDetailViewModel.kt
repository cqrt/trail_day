package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.doc.ArcGisDoc
import nz.mckenzie.trailday.doc.DocDetailReader
import nz.mckenzie.trailday.doc.DocDownloader
import nz.mckenzie.trailday.doc.DocWebDetails
import nz.mckenzie.trailday.domain.doc.DocAlertsState
import nz.mckenzie.trailday.domain.doc.DocCache
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.doc.withCompanion
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.offline.OfflineDocStore

/**
 * The facts behind one DOC detail page.
 *
 * The item arrives from the map or the browser already holding the line's geometry and its own
 * fields. For a track this adds the website layer's summary - introduction, difficulty, completion
 * time and page link - and then, when the item has a page link, asks DOC's public alert endpoint.
 * Nothing is scraped: every answer is a structured DOC layer or endpoint.
 */
class DocDetailViewModel(
    item: DocItem,
    val bike: DocItem?,
    settingsRepository: SettingsRepository,
    private val reader: DocDetailReader,
    private val docStore: DocCache
) : ViewModel() {

    val apiKey: StateFlow<String> = settingsRepository.linzApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val basemap: StateFlow<Basemap> = settingsRepository.basemap
        .stateIn(viewModelScope, SharingStarted.Eagerly, Basemap.DEFAULT)

    private val _item = MutableStateFlow(item)
    val item: StateFlow<DocItem> = _item

    private val _picture = MutableStateFlow<ByteArray?>(null)
    val picture: StateFlow<ByteArray?> = _picture

    private val _alerts = MutableStateFlow<DocAlertsState>(
        if (item.link == null) DocAlertsState.NotAvailable else DocAlertsState.Loading
    )
    val alerts: StateFlow<DocAlertsState> = _alerts

    init {
        viewModelScope.launch {
            // A track downloaded with its area already carries the website summary. Asking again is
            // only for a live search result, or a cache written before the summary travelled with it.
            if (item.source == DocSource.TRACKS &&
                (item.introduction == null || item.link == null)
            ) {
                reader.companionFor(item)?.let { companion ->
                    _item.value = item.withCompanion(companion)
                }
            }
            val current = _item.value
            _picture.value = current.pictureUrl?.let { url ->
                runCatching { docStore.picture(url) }.getOrNull()
            }
            _alerts.value = reader.alertsFor(current)
        }
    }

    companion object {
        fun factory(context: Context, item: DocItem, bike: DocItem?): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    val database = TrailDayDatabase.get(appContext)
                    DocDetailViewModel(
                        item = item,
                        bike = bike,
                        settingsRepository = SettingsRepository(appContext),
                        reader = DocWebDetails(),
                        docStore = OfflineDocStore(
                            dao = database.offlineDocItemDao(),
                            downloader = DocDownloader(ArcGisDoc())
                        )
                    )
                }
            }
        }
    }
}
