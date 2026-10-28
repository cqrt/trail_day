package nz.mckenzie.trailday.offline

/**
 * Which offline pack the map picker is choosing an area for.
 *
 * The three packs are independent caches - imagery tiles, everything DOC publishes, and the drawn
 * Topographic map - so they get an area each, chosen the same way: tap two corners, then download. The
 * picker is one screen because the choosing is one thing; only what is downloaded afterwards differs.
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
     * LINZ's vector Topographic map: its tiles, and the heights its hillshade is drawn from.
     *
     * The other drawn map the app can keep. Vector tiles are a fraction of the size of imagery for the
     * same ground, so this is the cheap way to carry a map with no reception - and the one a walker
     * actually reads on a hillside, where the photograph says nothing about the shape of the land.
     */
    TOPOGRAPHY,

    /**
     * The ground around one track: the imagery that draws it, and what DOC publishes inside the same box.
     *
     * The walker's own unit rather than a rectangle drawn by hand - the plan's "keep the ground around this
     * track" - so the box arrives already drawn around the track's line and both caches are kept in one
     * act. See [AroundTrack].
     */
    AROUND_TRACK,

    /**
     * The tracks other people publish - councils, regional councils, trail trusts and clubs - for the map's
     * second reference layer to work with no reception.
     *
     * Its own mode rather than part of [DOC_DATA]'s, because it is a second publisher's material and a
     * second cache: a walker planning a walk on their own council's walkways has no reason to pay for
     * DOC's huts, and the two are counted and cleared apart on the offline screen.
     */
    OTHER_TRACKS
}

/**
 * Whether this pack's ground can be chosen as a **region** as well as drawn as a box.
 *
 * Only the track packs, because a region is a way of asking a service that knows about districts. The
 * imagery and the drawn map are fetched tile by tile for a rectangle, and no rectangle is a region.
 */
val OfflinePickerMode.offersRegions: Boolean get() = this == OfflinePickerMode.OTHER_TRACKS
