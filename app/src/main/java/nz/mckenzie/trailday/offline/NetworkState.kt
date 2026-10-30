package nz.mckenzie.trailday.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Whether the phone can reach the internet at all.
 *
 * The map has five different things to say when it stops drawing, and **the first question is
 * whether there is a connection**, because it decides which of the other four is worth asking.
 * A phone with no reception cannot be told anything about the Worker or the key: the honest
 * sentence is "you're offline", and the useful action is the map that is already on the phone.
 * Asking the Worker first cost ten seconds of timeouts to arrive at a sentence that was not
 * about the connection.
 *
 * **Validated, not merely connected.** A phone joined to a Wi-Fi network that goes nowhere - a
 * captive portal, a campsite router with a dead uplink - has a network and cannot reach anything,
 * and [NetworkCapabilities.NET_CAPABILITY_VALIDATED] is the system's own answer to whether that
 * network actually works.
 *
 * The answer is asked for at one moment rather than followed: the notice is about a run of tiles
 * that has just failed, and tiles arriving again is what takes it away - see [TileTrouble]. A
 * stream here would be a second thing deciding when the notice lives.
 */
object NetworkState {

    @Volatile
    private var manager: ConnectivityManager? = null

    /** A test's own answer, in place of the phone's. */
    @Volatile
    private var answerForTest: Boolean? = null

    /** Called once, from [nz.mckenzie.trailday.TrailDayApplication]. */
    fun start(context: Context) {
        manager = runCatching {
            context.applicationContext.getSystemService(ConnectivityManager::class.java)
        }.getOrNull()
    }

    /**
     * Whether there is a network that reaches the internet.
     *
     * **Unknown reads as online.** If the manager is missing, or the answer cannot be read, the
     * notice falls back to the service's own explanation rather than telling a walker standing in
     * town that they are offline.
     */
    fun isOnline(): Boolean {
        answerForTest?.let { return it }
        val current = manager ?: return true
        return runCatching {
            val network = current.activeNetwork ?: return@runCatching false
            val capabilities = current.getNetworkCapabilities(network) ?: return@runCatching false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(true)
    }

    /** A test's own answer. Null puts the phone's back. */
    internal fun useForTest(answer: Boolean?) {
        answerForTest = answer
    }
}
