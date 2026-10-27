package nz.mckenzie.trailday.domain.cache

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * What a publisher said about something, as one column of text.
 *
 * **One helper for every publisher's saved tracks.** DOC's four services and the national tracks dataset
 * carry different fields and are kept in separate tables, but a saved row's details are stored the same
 * way either way, so they are written and read in one place rather than once per publisher.
 *
 * The details are kept **as they arrived** rather than as columns of their own, and that is the point: a
 * publisher adds and drops fields of its own accord, and a table with a column per detail would have to be
 * rebuilt every time one changed - and would throw away anything it had no column for. A saved row
 * therefore reads exactly as it did with a network, whatever was said that day.
 *
 * Text that will not read is **no details rather than a crash**: a row written by a later build, or
 * damaged, must not be able to stop a screen drawing. See [PathsJson] for the same rule about shape.
 */
object FactsJson {

    /** The details as text, field by field. */
    fun write(facts: Map<String, String>): String = json.encodeToString(serializer, facts)

    /** The details back, or none when the text is not readable. */
    fun read(text: String): Map<String, String> =
        if (text.isBlank()) emptyMap()
        else runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyMap())

    private val serializer = MapSerializer(String.serializer(), String.serializer())

    /**
     * Plain, and with the field names kept exactly as they arrived: this is a store of what a publisher
     * said, not the app's own document, and a name rewritten here would be a detail the reader cannot find.
     */
    private val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }
}
