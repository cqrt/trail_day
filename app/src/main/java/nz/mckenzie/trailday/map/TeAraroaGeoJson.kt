package nz.mckenzie.trailday.map

import android.content.Context
import nz.mckenzie.trailday.domain.tiles.TeAraroa

/**
 * The Te Araroa trail's own GeoJSON, read out of the app's assets.
 *
 * Beside [AssetGeoJson] and [DocGeoJson] because it is the same thing - the GeoJSON a map source is
 * handed - and the difference is only where it comes from: the walker's own rows, DOC's cache, and the
 * app's own bundle.
 *
 * **Read once, and only when it is asked for.** The trail is 700 KB of assets, and a walker who has
 * switched the layer off should never pay for reading it; the reader holds what it read, so switching
 * the layer on and off again costs nothing after the first time. A missing or unreadable asset is the
 * empty collection rather than a crash, because a map with no trail on it is a map, not a fault.
 */
object TeAraroaGeoJson {

    /** Reads the bundled route, holding it from the first read on. */
    fun reader(context: Context): () -> String {
        val assets = context.applicationContext.assets
        var held: String? = null
        return {
            held ?: runCatching { assets.open(TeAraroa.ASSET).use { it.readBytes().decodeToString() } }
                .getOrDefault(EMPTY)
                .also { held = it }
        }
    }

    /** Nothing to draw, in the shape a map source expects. */
    val EMPTY: String = AssetGeoJson.build(emptyList())
}
