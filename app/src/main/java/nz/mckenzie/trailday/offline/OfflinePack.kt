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
    TOPOGRAPHY("topography", "LINZ Topographic"),

    /** Everything DOC publishes for an area. Held as one cache, with no areas of its own. */
    DOC("doc", "DOC assets"),

    /**
     * The tracks other people publish: the national tracks dataset, in **named boxes of its own.**
     *
     * Its own pack rather than a second half of [DOC]'s, because it is a second publisher and a second
     * cache: a walker who clears DOC's material keeps their council's walkways, and the other way round.
     * Keyed by the service's own identifier, so two overlapping areas end with one copy of a track rather
     * than two - but each download is also recorded under the name the walker gave it, because a cache of
     * tracks cannot tell them which hillsides they kept.
     */
    OTHER_TRACKS("other-tracks", "Other tracks");

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
