package nz.mckenzie.trailday.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.doc.ArcGisDoc
import nz.mckenzie.trailday.doc.DocDetailReader
import nz.mckenzie.trailday.doc.DocDownloader
import nz.mckenzie.trailday.doc.DocProxyDetails
import nz.mckenzie.trailday.domain.doc.DocAlertsState
import nz.mckenzie.trailday.domain.doc.DocCache
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.doc.isPictureAddress
import nz.mckenzie.trailday.domain.doc.withCompanion
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.offline.OfflineDocStore
import java.net.HttpURLConnection
import java.net.URL

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
            _picture.value = pictureFor(current.pictureUrl)
            _alerts.value = reader.alertsFor(current)
        }
    }

    /**
     * The picture for [url]: the phone's own copy when the area was downloaded, otherwise the
     * website's thumbnail fetched for this visit.
     *
     * The download keeps a copy so the page works with no reception; a track looked at without being
     * downloaded still has its picture, because the API publishes one and a page without it is a
     * page missing the thing the walker looks at first.
     */
    private suspend fun pictureFor(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        runCatching { docStore.picture(url) }.getOrNull()?.let { return it }
        if (!isPictureAddress(url)) return null
        return withContext(Dispatchers.IO) {
            val connection = runCatching {
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", ArcGisDoc.USER_AGENT)
                }
            }.getOrNull() ?: return@withContext null
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                if (!(connection.contentType ?: "").startsWith("image/")) return@withContext null
                connection.inputStream.use { it.readBytes() }
            } catch (_: Exception) {
                null
            } finally {
                connection.disconnect()
            }
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
                        reader = DocProxyDetails(),
                        docStore = OfflineDocStore(
                            dao = database.offlineDocItemDao(),
                            downloader = DocDownloader.online()
                        )
                    )
                }
            }
        }
    }
}
