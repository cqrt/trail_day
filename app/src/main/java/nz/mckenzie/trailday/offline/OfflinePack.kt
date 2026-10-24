package nz.mckenzie.trailday.offline

/**
 * A pack of things a walker keeps for offline use: **what it is called in a stored row, and what it is
 * called on a screen.**
 *
 * The two are separate on purpose, and for the same reason [nz.mckenzie.trailday.domain.doc.DocSource]
 * keeps an id apart from its display name: an area's row has to say which pack it belongs to for as long
 * as the row lives, so that id is never renamed - and the words a walker reads are chosen where they are
 * read, so calling "Topographic map" something better later cannot orphan a season of downloads.
 */
enum class OfflinePack(
    /** Stable id: it names the pack in a stored area row. Never renamed. */
    val id: String,
    /** What the walker reads: the offline screen's card title, and the notification's own line. */
    val title: String
) {
    /** LINZ's aerial imagery: what offline areas were always downloaded into. */
    IMAGERY("imagery", "LINZ imagery"),

    /**
     * LINZ's vector Topographic map, with the heights its hillshade is drawn from.
     *
     * The pack a walker keeps in small, named pieces: it is the drawn map they read on a hillside, and its
     * tiles are a fraction of imagery for the same ground - which is what makes several small areas of it
     * worth having rather than one large one.
     */
    TOPOGRAPHY("topography", "Topographic map"),

    /** Everything DOC publishes for an area. Held as one cache, with no areas of its own. */
    DOC("doc", "DOC assets");

    companion object {
        /**
         * The pack a stored id names, or imagery.
         *
         * Anything unrecognised reads as imagery rather than throwing: a row written by a later build must
         * not stop the offline screen drawing, and imagery is the pack every row that predates this
         * column came from.
         */
        fun fromStorage(value: String?): OfflinePack =
            entries.firstOrNull { it.id == value } ?: IMAGERY
    }
}
