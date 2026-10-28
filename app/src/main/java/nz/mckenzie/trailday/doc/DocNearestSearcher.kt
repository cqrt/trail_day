package nz.mckenzie.trailday.doc

import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocQuery
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.reference.NEARBY_DUPLICATE_ALLOWANCE
import nz.mckenzie.trailday.domain.reference.nearbyBandsFor

/**
 * What DOC publishes, read **nearest first** - in rings outward from the walker.
 *
 * The same fault the tracks browser had, and the same fix. A near-me search used to ask DOC's services for
 * one page of a box, and DOC orders that page by its own number rather than by distance: a bigger radius
 * spent the page on rows further away and the near ones fell out of it, so huts and tracks **disappeared as
 * the circle grew**. Reading outward means the page is spent on the nearest rows first, and because every
 * run asks the same inner rings first, a larger radius can only add results.
 *
 * Here rather than in the browser's view model so the rule can be read and tested without a phone - the
 * service is a parameter, and a fake one is how the monotonicity is held still.
 */
class DocNearestSearcher(private val reader: DocReader) {

    suspend fun nearest(
        query: DocQuery,
        at: GeoPoint,
        radiusKm: Double,
        /** How many rows one search reads, which is also what the browser's message counts against. */
        limit: Int = DocQuery.DEFAULT_LIMIT
    ): DocReadResult {
        val collected = LinkedHashMap<String, DocItem>()
        var failure: String? = null
        for (band in nearbyBandsFor(radiusKm)) {
            if (collected.size >= limit) break
            // Ask for the repeats as well: see [NEARBY_DUPLICATE_ALLOWANCE]. The first ring has nothing to
            // repeat, so it asks for the page and nothing more.
            val ask = if (collected.isEmpty()) limit else limit - collected.size + NEARBY_DUPLICATE_ALLOWANCE
            val read = reader.search(query.copy(near = at, radiusKm = band, limit = ask))
            when (read) {
                is DocReadResult.Found ->
                    // A hut inside two rings arrives twice, and a track DOC publishes twice arrives on every
                    // ring: one row per thing, keyed the way the cache keys it.
                    read.items.forEach { item -> collected["${item.source.id}:${item.objectId}"] = item }

                is DocReadResult.Failed -> {
                    // A ring that fails leaves the rings already read: a shorter list is what the walker
                    // has, and losing it because the last ring timed out would be worse.
                    failure = read.message
                    break
                }
            }
        }
        val lost = failure
        if (collected.isEmpty() && lost != null) return DocReadResult.Failed(lost)
        return DocReadResult.Found(collected.values.toList())
    }
}
