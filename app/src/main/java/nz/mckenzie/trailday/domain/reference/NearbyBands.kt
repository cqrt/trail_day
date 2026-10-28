package nz.mckenzie.trailday.domain.reference

/**
 * The radii a "near me" search is read in, in kilometres: **the radii both browsers offer**, so the rings
 * asked are the ones a walker has already been shown.
 *
 * A search asks for these smallest first and adds each ring to what the last one brought. That is what makes
 * a bigger radius only ever add results: a single page of a bigger circle is spent on whichever rows the
 * service happens to list first, and the near ones fall out of it - see
 * [nz.mckenzie.trailday.other.OtherTracksSearcher] and [nz.mckenzie.trailday.doc.DocNearestSearcher].
 */
val NEARBY_BANDS: List<Double> = listOf(5.0, 10.0, 25.0, 50.0, 100.0, 150.0, 200.0)

/**
 * How many rows a ring may return beyond the budget, to cover the inner rings it repeats.
 *
 * An outer ring contains every row of the smaller rings inside it, so its answer repeats them in the
 * service's own order - and a request sized to the remaining budget would be spent on rows already held.
 * Measured at Invercargill, that read 252 of the 301 rows inside 100 km and showed 59 tracks where there
 * are 83.
 */
const val NEARBY_DUPLICATE_ALLOWANCE = 300

/** The rings to ask for [radiusKm], smallest first, ending at the chosen radius. */
fun nearbyBandsFor(radiusKm: Double): List<Double> =
    (NEARBY_BANDS.filter { it < radiusKm } + radiusKm).sorted()
