package nz.mckenzie.trailday.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * The notification permission: **whether the app may put anything in the shade, and how to ask.**
 *
 * Android 13 turned this into a question, and it is a question about the whole app - a walk being recorded,
 * a download's progress, the editor's address. Without it every one of those is silently dropped, which is
 * the worst way for a notification to fail: the walker sees nothing and concludes there is nothing to see.
 *
 * **Asked where a notification is about to matter**, which is also where it can be explained - the moment a
 * walk is started, or a download is. Never on the way into the app, where it would be a prompt for nothing.
 *
 * It used to be asked only alongside the location permission, and only when that was missing - so a walker
 * who had already allowed location from the map, which is the natural way to allow it, was never asked, and
 * their recordings were never announced.
 */
object NotificationPermission {

    /** True when the app may post: always below Android 13, where this is not a question. */
    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

/** What the app may post now, and how to ask for it. */
internal data class NotificationPermissionState(
    val allowed: Boolean,
    /**
     * Asks, if there is anything to ask. Safe to call when it is already held: the system answers at once
     * rather than showing a prompt.
     */
    val ask: () -> Unit
)

/**
 * The one screen that can give the permission back, once the app has been told no.
 *
 * **A refused permission cannot be asked for again**: the system answers "no" without showing anything, so
 * a walker who wants their recording announced has to be sent to the app's own notification settings.
 */
fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/** The notification permission as a screen sees it, with a way to ask for it. */
@Composable
internal fun rememberNotificationPermission(): NotificationPermissionState {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotificationPermission.allowed(context)) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> allowed = granted }

    return NotificationPermissionState(
        allowed = allowed,
        ask = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    )
}
