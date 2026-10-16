package nz.mckenzie.trailday.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import nz.mckenzie.trailday.data.ReminderStateStore
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase

/**
 * The background reminder check.
 *
 * WorkManager rather than an alarm: this is deferrable work, and WorkManager already
 * knows how to survive a reboot and to wait until the phone is out of Doze, which an
 * `AlarmManager` alarm would need a boot receiver and a reschedule to match.
 */
class DueReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val appContext = applicationContext
        val check = DueReminderCheck(
            assetRepository = AssetRepository(TrailDayDatabase.get(appContext)),
            store = ReminderStateStore(appContext),
            notifier = ReminderNotifier(appContext)
        )
        check.run()
        Result.success()
    } catch (failure: Throwable) {
        // Retrying is right for a database that is briefly busy; it is pointless if the
        // failure is permanent, but WorkManager caps the retries soon enough.
        Result.retry()
    }
}
