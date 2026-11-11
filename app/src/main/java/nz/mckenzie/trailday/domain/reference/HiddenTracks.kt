package nz.mckenzie.trailday.domain.reference

/**
 * A published track the walker has said is wrong: what to key it by, and what to call it.
 *
 * The name is here because somebody has to read the list of what has been hidden, and a list of
 * `doc-tracks:1234` is a list nobody can read. It is **a label and never a key** - two tracks can
 * honestly carry the same name, and hiding one must not hide the other.
 */
data class HiddenTrack(
    /** The publisher's own reference: `doc-tracks:1234`, `other:XYZ`. See [nz.mckenzie.trailday.domain.doc.DocItem.sourceRef]. */
    val sourceRef: String,
    /** What the walker saw it called, so the list in Settings reads as places rather than references. */
    val name: String
)

/**
 * The tracks somebody else published that the walker has hidden from the map.
 *
 * **DOC's material and the national dataset are read, never kept.** A line from either is a cache row
 * belonging to a region, so it has no page of its own with a Delete on it and nothing can be edited -
 * which leaves a walker looking at a line through a slip, or an alignment that never existed, with two
 * blunt answers: switch the whole layer off, or delete the region and lose every other track in it.
 *
 * Hiding one line is the answer the app was missing. What is kept is **the publisher's own reference**,
 * not a copy of the line, so:
 *
 * - another download of the region does not bring it back, because the reference is what is compared;
 * - a release that reads a newer copy of the dataset does not bring it back either;
 * - the walker's own recorded track can be given the same name as the wrong one and become the only
 *   line on that ground, which is the whole point of recording it.
 *
 * A hidden track is this phone's opinion, so it is not in a backup and not on the desk's page: the
 * same rule the look and the layer switches already follow.
 */
data class HiddenTracks(val entries: List<HiddenTrack> = emptyList()) {

    private val refs: Set<String> = entries.mapTo(HashSet()) { it.sourceRef }

    /**
     * Whether this published thing has been hidden.
     *
     * Null never matches, and that is the safe way round: a thing with no reference is a thing the app
     * cannot name, and hiding it would be hiding it by accident.
     */
    fun hides(sourceRef: String?): Boolean = sourceRef != null && sourceRef in refs

    val isEmpty: Boolean get() = entries.isEmpty()

    /** This set with one more thing hidden, or this set when it is already in it. */
    fun hiding(track: HiddenTrack): HiddenTracks =
        if (hides(track.sourceRef)) this else HiddenTracks(entries + track)

    /** This set without [sourceRef]. A reference that is not in it is not an error. */
    fun showing(sourceRef: String): HiddenTracks =
        if (!hides(sourceRef)) this else HiddenTracks(entries.filterNot { it.sourceRef == sourceRef })

    companion object {
        /** Nothing hidden, which is what a phone that has never hidden anything reads. */
        val NONE = HiddenTracks()

        /**
         * The field separator: a unit separator, which is in neither a reference nor a track's name.
         *
         * Stored as one string per entry, `reference` then `name`, because the preference is a string
         * set and a name is what the list needs. A CSV or a JSON document in a preference would be a
         * second parser for two fields.
         */
        private const val FIELD = '\u001F'

        /**
         * What the preference holds, as a set.
         *
         * Anything unreadable is dropped rather than guessed at: a set is this phone's own note to
         * itself, and one bad entry must not empty the rest of it.
         */
        fun read(stored: Collection<String>?): HiddenTracks {
            if (stored.isNullOrEmpty()) return NONE
            val entries = stored.mapNotNull { line ->
                val at = line.indexOf(FIELD)
                // A reference on its own is an entry written by a build that had no name for it: keep
                // it hidden, and call it what the reference says.
                when {
                    at < 0 -> line.takeIf { it.isNotBlank() }?.let { HiddenTrack(it, it) }
                    else -> {
                        val ref = line.substring(0, at)
                        val name = line.substring(at + 1)
                        ref.takeIf { it.isNotBlank() }?.let {
                            HiddenTrack(it, name.ifBlank { it })
                        }
                    }
                }
            }
            return if (entries.isEmpty()) NONE else HiddenTracks(entries)
        }

        /** What [hidden] is written as. Nothing hidden writes nothing, so an untouched phone is empty. */
        fun write(hidden: HiddenTracks): Set<String> =
            hidden.entries.mapTo(LinkedHashSet()) { "${it.sourceRef}$FIELD${it.name}" }
    }
}
