package nz.mckenzie.trailday.offline

/**
 * Which offline pack the map picker is choosing an area for.
 *
 * The two packs are independent caches - imagery tiles, and everything DOC publishes - so they get an
 * area each, chosen the same way: tap two corners, then download. The picker is one screen because the
 * choosing is one thing; only what is downloaded afterwards differs.
 */
enum class OfflinePickerMode {
    /** Basemap imagery tiles, for the map to work with no reception. */
    IMAGERY,

    /**
     * What DOC publishes - its tracks, huts, campsites and bike locations - for the browser and the
     * map's reference layer to work with no reception.
     */
    DOC_DATA,

    /**
     * The ground around one track: the imagery that draws it, and what DOC publishes inside the same box.
     *
     * The walker's own unit rather than a rectangle drawn by hand - the plan's "keep the ground around this
     * track" - so the box arrives already drawn around the track's line and both caches are kept in one
     * act. See [AroundTrack].
     */
    AROUND_TRACK
}
