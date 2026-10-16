package nz.mckenzie.trailday.domain.doc

/**
 * DOC's material kept on the phone, so a walk in the hills needs nothing from DOC.
 *
 * The download is one act and the reading of it another, which is why this is an interface: the phone
 * downloads a box of DOC's material once, on purpose, and what a screen shows afterwards is read from
 * here rather than over the network.
 *
 * **Keyed by source as well as by DOC's number**, for the reason the reference is namespaced: DOC
 * numbers its features per service, so a hut and a track really can both be 123, and a store keyed on
 * the number alone would keep one of them and lose the other without saying so.
 */
interface DocCache {

    /** Everything held, newest download first is not a promise: this is a set, not a history. */
    suspend fun cached(source: DocSource): List<DocItem>

    /** How many things are held, all sources together: the number the Offline screen shows. */
    suspend fun count(): Int

    /** Keeps [items], replacing what was held of the same sources and the same numbers. */
    suspend fun store(items: List<DocItem>)

    /** Takes every source's material away. */
    suspend fun clear()
}

/** A cache with nothing in it: a test's, or a desk that has no phone behind it. */
object EmptyDocCache : DocCache {
    override suspend fun cached(source: DocSource): List<DocItem> = emptyList()
    override suspend fun count(): Int = 0
    override suspend fun store(items: List<DocItem>) = Unit
    override suspend fun clear() = Unit
}

/**
 * What was held that matches [nameContains], by name, case-blind.
 *
 * Here rather than in the store because it is a filter over what was read, not a database query: the
 * material is already on the phone, and asking the database to search it would be a second way to
 * answer a question the app already answers in one place.
 */
fun List<DocItem>.matching(nameContains: String): List<DocItem> {
    val needle = nameContains.trim()
    if (needle.isEmpty()) return this
    return filter { it.name.contains(needle, ignoreCase = true) }
}
