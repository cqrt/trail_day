package nz.mckenzie.trailday.offline

/**
 * How the picker's ground is chosen: **a region off a map of the country, or a box drawn by hand.**
 *
 * Two ways because they answer different questions. A region answers "where am I going" - Otago, Southland,
 * Wellington - in one tap and with a name everybody already knows. A box answers "this valley, this end of
 * the beach", which no region describes and a walker standing on the ground can see.
 *
 * The box is not replaced. A region is a coarse and very convenient unit, and a walker who wants the ground
 * around one hut would pay for a whole province to get it.
 */
enum class OfflineChoice {
    /** One of the sixteen regions: tapped on the map, named for free, counted before it is downloaded. */
    REGION,

    /** Two opposite corners, for the ground a region does not describe. */
    BOX
}
