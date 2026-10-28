package nz.mckenzie.trailday.offline

import android.content.Context
import nz.mckenzie.trailday.domain.region.Regions

/**
 * The bundled region outlines: **Stats NZ's regional boundaries, simplified to what a phone needs.**
 *
 * 16 regions, 1,995 points, 39 KB - simplified to about two kilometres, with islets under about five
 * kilometres across dropped, because nothing here is measured: the shapes are drawn and tapped, and what is
 * downloaded is decided by the districts' names. The file is read once, when the picker opens.
 *
 * Stats NZ publishes these under the Creative Commons Attribution 4.0 licence, so the attribution strip on
 * the picker says whose they are, in the same way LINZ's imagery is credited on every map.
 */
object RegionAsset {

    /** Where the outlines live in `assets/`. */
    const val FILE = "regions.json"

    /** The regions, or none when the file is missing - which a download of the app could not be. */
    fun load(context: Context): Regions = runCatching {
        context.assets.open(FILE).bufferedReader().use { read -> Regions.parse(read.readText()) }
    }.getOrDefault(Regions.EMPTY)
}
