package nz.mckenzie.trailday.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * The location permission: **whether Trail Day has the precise location a track needs, and what to say
 * when it has not.**
 *
 * Android asks one question and offers two answers. **Approximate counts as a refusal here**: a fix from
 * a cell tower is a kilometre or two wide, which is no use for a dot on a map and none at all for a
 * track. From inside the app the two answers look identical - no fine location - which is why somebody
 * who tapped Approximate used to read "location permission is needed", ask again, and get nothing.
 *
 * The words are built here rather than on each screen so the map and the recorder cannot explain the
 * same setting two ways.
 */
object LocationPermission {

    /** True when the app may know where the phone is, precisely. */
    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * True when the only location on offer is the rough kind.
     *
     * Android answers this by granting the coarse permission and refusing the fine one, so both are
     * asked: coarse alone is somebody who chose Approximate, and neither is somebody who said no.
     */
    fun approximateOnly(context: Context): Boolean =
        !granted(context) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** What to say, given that only approximate location is allowed. */
    fun refusal(approximateOnly: Boolean): String = if (approximateOnly) APPROXIMATE else DENIED

    /** What to say, worked out from the phone's own answer. */
    fun refusal(context: Context): String = refusal(approximateOnly(context))

    /** Somebody chose Approximate, and the app cannot tell them so without saying where to change it. */
    const val APPROXIMATE = "Trail Day has approximate location, which is too rough to show where you are " +
        "or to record a track. Change it to Precise in the phone's settings: Apps, Trail Day, " +
        "Permissions, Location."

    /** The permission was refused, or turned off for the app altogether. */
    const val DENIED = "Location is off for Trail Day, so there's no dot on the map and nothing can be " +
        "recorded. Turn it on in the phone's settings: Apps, Trail Day, Permissions."
}
