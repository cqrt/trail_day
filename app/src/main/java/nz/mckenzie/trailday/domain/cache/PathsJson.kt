package nz.mckenzie.trailday.domain.cache

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import nz.mckenzie.trailday.domain.geo.GeoPoint

/**
 * A saved track's shape, as text, for the row it is saved in.
 *
 * **One helper for every publisher's saved tracks.** DOC's material and the tracks councils and trusts
 * publish are kept in separate tables with separate keys, but what a saved track's shape comes to is the
 * same thing either way, so it is written and read in one place rather than once per publisher.
 *
 * A saved copy is not the record of the walker's work, so its shape travels as **one column of text**
 * rather than as a table of points: it is written once when a track is downloaded and read whole when a
 * screen wants it, and nothing ever joins to it. Each point is a `[lat, lng]` pair, and each path is a
 * list of them, so a track with side tracks is the same shape it is everywhere else in the app.
 */
object PathsJson {

    private val json = Json

    private val serializer = ListSerializer(ListSerializer(ListSerializer(Double.serializer())))

    fun write(paths: List<List<GeoPoint>>): String =
        json.encodeToString(serializer, paths.map { path -> path.map { listOf(it.lat, it.lng) } })

    /** The paths back, or none when the text will not read - a broken saved row is not a crash. */
    fun read(text: String): List<List<GeoPoint>> = runCatching {
        json.decodeFromString(serializer, text)
            .map { path -> path.mapNotNull { pair -> pointOf(pair) } }
            .filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())

    private fun pointOf(pair: List<Double>): GeoPoint? =
        if (pair.size >= 2) GeoPoint(lat = pair[0], lng = pair[1]) else null
}
