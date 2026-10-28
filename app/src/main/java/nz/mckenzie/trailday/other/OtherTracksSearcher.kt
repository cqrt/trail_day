package nz.mckenzie.trailday.other

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.domain.other.OtherTracksQuery
import nz.mckenzie.trailday.domain.tiles.boxAround
import nz.mckenzie.trailday.domain.reference.NEARBY_DUPLICATE_ALLOWANCE
import nz.mckenzie.trailday.domain.reference.nearbyBandsFor

/**
 * Somebody else's tracks, read **nearest first** - in rings outward from the walker.
 *
 * One request for the whole circle is what the browser used to do, and it was wrong. The service answers a
 * page ordered by **its own counter, not by distance**, so a bigger radius spent that page on rows further
 * away and the near ones fell out of it: at Invercargill a 100 km search showed **83** tracks and a 200 km
 * search showed **75**, with tracks vanishing as the circle grew. Reproduced against the live service before
 * this existed - the same 300-row page returned 83 tracks at 100 km and 79 at 200 km.
 *
 * Rings rather than a bigger page, because a bigger page is ordered by the counter too: 200 km around
 * Wellington is 3,848 rows and no page holds them. Reading outward means the page is spent on the nearest
 * rows first, and because every run asks the same inner rings first, **a larger radius can only add tracks
 * to the list, never take them away**.
 *
 * Here rather than in the screen's view model so the rule can be read and tested without a phone: the
 * service is a parameter, and a fake one is how the monotonicity above is held still.
 */
class OtherTracksSearcher(private val reader: OtherTracksReader = ArcGisOtherTracks()) {

    suspend fun nearest(
        name: String,
        at: GeoPoint,
        radiusKm: Double,
        /** How many rows one search reads, which is also what the browser's message counts against. */
        limit: Int = OtherTracksQuery.DEFAULT_LIMIT
    ): OtherTracksReadResult {
        val collected = LinkedHashMap<String, OtherTrack>()
        var failure: String? = null
        // The radii the picker itself offers, so a search is asked in rings a walker has already been
        // shown, plus the chosen radius when it is not one of them.
        for (band in nearbyBandsFor(radiusKm)) {
            if (collected.size >= limit) break
            // **Ask for more than the budget still allows**, except on the first ring. An outer ring
            // contains every row of the inner ones, so its answer repeats them in the service's own order
            // and a request sized to the remaining budget would be spent on rows already held: measured at
            // Invercargill, that read 252 of the 301 rows inside 100 km and showed 59 tracks where there
            // are 83. The allowance covers the repeats; the first ring has nothing to repeat, so it asks
            // for the page and nothing more.
            val ask = if (collected.isEmpty()) limit else limit - collected.size + NEARBY_DUPLICATE_ALLOWANCE
            val read = reader.read(
                OtherTracksQuery(nameContains = name, bounds = boxAround(at, band), limit = ask)
            )
            when (read) {
                is OtherTracksReadResult.Found ->
                    read.tracks.forEach { track -> collected[track.globalId] = track }

                is OtherTracksReadResult.Failed -> {
                    // A ring that fails leaves the rings already read: a shorter list is what the walker
                    // has, and losing it because the last ring timed out would be worse.
                    failure = read.message
                    break
                }
            }
        }
        val lost = failure
        if (collected.isEmpty() && lost != null) return OtherTracksReadResult.Failed(lost)
        return OtherTracksReadResult.Found(collected.values.toList())
    }
}
