package nz.mckenzie.trailday.offline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.MainActivity
import nz.mckenzie.trailday.R

/**
 * A download in flight, said in the shade rather than only on the screen that started it.
 *
 * **What this is for.** A download for offline use is the one thing in the app that a walker starts and
 * then wants to put the phone away - it takes minutes, it needs no attention, and watching a progress bar
 * is not what anybody wants to do with an afternoon. So it is reported where a walker already looks for
 * things that are still happening: the notification shade, in the same words the screen uses, because
 * [downloadProgressWords] is read by both.
 *
 * **It reads rather than runs.** The download itself is the screens' own work, in the view model that
 * started it; this service's whole job is the notification, and it follows [DownloadState] until that
 * says there is nothing left to report. What it buys besides the notification is the app's priority: a
 * foreground service is a process the system does not quietly reclaim, so a download that is being
 * reported is also a download more likely to finish.
 *
 * **Foreground from the first moment.** `startForegroundService` promises the system a notification
 * within a few seconds, and stopping without ever having gone foreground is answered by killing the app -
 * the same lesson the editor's service learned the hard way.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        followTheDownload()
        // Not sticky: a service that resurrected itself would put up a notification about a download
        // that stopped when the process did.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Follows [DownloadState] until it says nothing is being downloaded.
     *
     * The first emission can be null - a service started a moment before the download it is for has said
     * anything - so a null only stops this once it has had something to show. A notification that
     * appeared and vanished in the same instant would be worse than none.
     */
    private fun followTheDownload() {
        scope.launch {
            var shown = false
            DownloadState.current.collect { progress ->
                if (progress == null) {
                    if (shown) stopSelf()
                } else {
                    shown = true
                    notify(progress)
                }
            }
        }
    }

    private fun goForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(DownloadState.current.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
    }

    private fun notify(progress: DownloadProgress) {
        notificationManager().notify(NOTIFICATION_ID, buildNotification(progress))
    }

    /**
     * The notification: **which pack, and the same sentence the screen is showing.**
     *
     * A progress bar as well as the words, because the words say how fast and how long and the bar says
     * "nearly there" at a glance - which is the one thing a walker checks without reading.
     */
    private fun buildNotification(progress: DownloadProgress?): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setOngoing(true)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)

        if (progress == null) {
            return builder
                .setContentTitle(getString(R.string.download_notification_title, ""))
                .setContentText(getString(R.string.web_editor_notification_starting))
                .build()
        }

        return builder
            .setContentTitle(getString(R.string.download_notification_title, progress.pack))
            .setContentText(downloadProgressWords(progress))
            .setProgress(100, progress.percent, progress.total <= 0)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.download_channel_description)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "trailday-downloads"
        const val NOTIFICATION_ID = 4203

        /**
         * Puts the notification up, if it is not up already.
         *
         * Safe to ask for twice, which matters because both screens that can start a download ask as soon
         * as they see one running - and the download outlives the screen that began it.
         */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java)
            )
        }
    }
}
