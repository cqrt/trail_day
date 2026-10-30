package nz.mckenzie.trailday

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.backup.BackupScheduler
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.domain.look.Look
import nz.mckenzie.trailday.domain.tiles.LinzAccess
import nz.mckenzie.trailday.map.BasemapPreviewCache
import nz.mckenzie.trailday.offline.NetworkState
import nz.mckenzie.trailday.offline.TileServerHolder
import nz.mckenzie.trailday.tracking.DevicePosition
import nz.mckenzie.trailday.update.UpdateCheckScheduler
import org.maplibre.android.MapLibre

class TrailDayApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Must run before any MapView is instantiated. MapLibre needs no API key
        // of its own - the LINZ Basemaps key is supplied per style.
        MapLibre.getInstance(this)

        // MapLibre gates its HTTP requests on the device's connectivity, so with
        // no reception it would not request anything at all - including from the
        // app's own loopback tile server, which is exactly what holds the
        // downloaded imagery. Telling it "connected" makes it ask; the server
        // answers from disk, and simply 404s for tiles it never stored.
        MapLibre.setConnected(true)

        // One tile server for the whole app: the map reads tiles from it whether
        // or not there is a network, and anything browsed online is kept for
        // offline use.
        TileServerHolder.start(this)

        // One position stream for the whole app: every map shows where the phone is, and
        // collection starts and stops with whichever map is on screen.
        DevicePosition.start(this)

        // Whether the phone can reach anything, for the map's notice: with no connection the map
        // says so and offers the map already on the phone, rather than waiting on a service that
        // cannot be reached to say nothing useful. See [NetworkState].
        NetworkState.start(this)

        // The switches below have to actually stop the job they name, or the setting is a lie.
        val settings = SettingsRepository(this)

        // The walker's own LINZ key, if they have put one in the developer section: followed for the
        // life of the process, so a key entered or cleared takes effect on the next tile rather than at
        // the next launch. See [LinzAccess].
        LinzAccess.follow(
            settings.developerLinzKey,
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )

        // The look the walker has chosen for the map, followed the same way: every colour and width in
        // the app is asked of one place, so a look has to arrive there at launch rather than be carried
        // through every screen. See [Look].
        Look.follow(
            settings.mapLook,
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )

        // The basemap picker's example tiles, fetched once at launch: one small tile each, so the
        // pictures are there the moment Settings is opened rather than arriving while it is read.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            BasemapPreviewCache.loadAll()
        }

        // The same for the update check: the switch that turns it off has to actually
        // stop the job that does it, or the setting is a lie.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.updateChecksEnabled.collect { enabled ->
                UpdateCheckScheduler.sync(this@TrailDayApplication, enabled)
            }
        }

        // And for the off-site copy, where the setting can arrive from a restore as well as
        // from the switch - a phone restored from a backup should start backing itself up.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.backUpAutomatically.collect { enabled ->
                BackupScheduler.sync(this@TrailDayApplication, enabled)
            }
        }
    }
}
