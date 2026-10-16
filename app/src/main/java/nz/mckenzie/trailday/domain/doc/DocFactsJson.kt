package nz.mckenzie.trailday.domain.doc

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * What DOC said about something, as one JSON column.
 *
 * The facts are kept **as they arrived** rather than as columns, and that is the point: the four
 * services carry different fields, DOC adds and drops fields of its own accord, and a table with a
 * column per fact would have to be migrated every time - and would throw away anything it had no column
 * for. A cached row therefore reads exactly as it did with a network, whatever DOC said that day.
 *
 * Text that will not read is **no facts rather than a crash**: a cache row written by a later build, or
 * damaged, must not be able to stop a screen drawing. See [DocPathsJson] for the same rule about
 * geometry.
 */
object DocFactsJson {

    /** The facts as JSON, field by field. */
    fun write(facts: Map<String, String>): String = json.encodeToString(serializer, facts)

    /** The facts back, or none when the text is not readable. */
    fun read(text: String): Map<String, String> =
        if (text.isBlank()) emptyMap()
        else runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyMap())

    private val serializer = MapSerializer(String.serializer(), String.serializer())

    /**
     * Plain, and with the field names kept exactly as they arrived: this is a store of what DOC said,
     * not the app's own document, and a name rewritten here would be a fact the reader cannot find.
     */
    private val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }
}
