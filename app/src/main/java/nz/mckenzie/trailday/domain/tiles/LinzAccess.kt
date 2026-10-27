package nz.mckenzie.trailday.domain.tiles

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Where LINZ is reached from, and whether the walker's own key goes with it.
 *
 * The app ships no key of its own. Every LINZ URL it builds is a LINZ *path* on its own proxy's host,
 * and the key lives in `worker-linz/` as a Worker secret - so there is nothing to paste into Settings
 * and nothing that expires on a phone. See `worker-linz/README.md` for why that is worth a Worker.
 *
 * **This is the one door out of it, and it is hidden on purpose.** A walker with their own LINZ key, or
 * the developer checking the app against LINZ itself rather than against the proxy, can put a key in
 * the **Developer options** section; every URL below is then built for LINZ's own host with `?api=` on
 * it instead. Nothing else about the app changes - the same fetchers, the same stores, the same style -
 * which is the whole point of the choice living in one place.
 *
 * The choice is a *process-wide* one, like the tile server's own address in
 * [nz.mckenzie.trailday.offline.TileServerHolder], and for the same reason: a URL gets built in a dozen
 * places that have no business knowing about a preference. [ownKeyInUse] is what those places watch to
 * learn that a URL they were handed has gone stale.
 */
object LinzAccess {

    /** LINZ's own host. Key mode talks to this, and only key mode does. */
    const val LINZ_ORIGIN = "https://basemaps.linz.govt.nz"

    /**
     * The app's own proxy, which holds the key so the app does not have to.
     *
     * The one place this address is written down. See `worker-linz/README.md`.
     */
    const val PROXY = "https://trailday-linz-proxy.le-mckenzies.workers.dev"

    @Volatile
    private var ownKey: String = ""

    private val _ownKeyInUse = MutableStateFlow(false)

    /**
     * Whether the walker's own key is in force.
     *
     * What a map watches to know the style it has loaded was built from the other host: the two modes
     * are two different sets of URLs for the same tile, so a change is a style reload.
     */
    val ownKeyInUse: StateFlow<Boolean> = _ownKeyInUse.asStateFlow()

    /** The host every LINZ URL is built from. */
    val host: String get() = if (ownKey.isBlank()) PROXY else LINZ_ORIGIN

    /**
     * [path] at whichever host is in use, with the walker's key on it when there is one.
     *
     * A path may carry a query of its own - `pipeline=terrain-rgb` is what makes the elevation source an
     * elevation source - so the key joins it rather than replacing it.
     *
     * The key travels in the query because that is LINZ's own API shape, not a choice made here. It is
     * exactly why the key belongs on a Worker for everyone who has not asked for this: a URL turns up in
     * logs, and a key in the URL turns up with it.
     */
    fun url(path: String): String =
        if (ownKey.isBlank()) {
            "$PROXY$path"
        } else {
            "$LINZ_ORIGIN$path" + (if (path.contains('?')) '&' else '?') + "api=$ownKey"
        }

    /**
     * LINZ's own asset URL - a hosted style's sprite, or its letters - at whichever host is in use.
     *
     * A style document names those at LINZ's host itself, so they have to be moved with everything else.
     * Anything that is not a LINZ URL is handed back untouched rather than mangled into one.
     */
    fun rewrite(linzUrl: String): String {
        val path = linzUrl.removePrefix(LINZ_ORIGIN)
        return if (path == linzUrl) linzUrl else url(path)
    }

    /** The key as it goes into a URL: what LINZ issues, and nothing that would break the query. */
    fun sanitiseKey(value: String): String =
        value.trim().filter { it.isLetterOrDigit() || it == '-' || it == '_' }

    /**
     * Follows the stored key for the life of the process, so a change takes effect at once.
     *
     * Called once, from the application, the way the tile server is started once.
     */
    fun follow(keys: Flow<String>, scope: CoroutineScope) {
        scope.launch {
            keys.collect { stored ->
                ownKey = sanitiseKey(stored)
                _ownKeyInUse.value = ownKey.isNotBlank()
            }
        }
    }

    /** For a test: the mode without a preference store behind it. */
    internal fun use(key: String) {
        ownKey = sanitiseKey(key)
        _ownKeyInUse.value = ownKey.isNotBlank()
    }
}
