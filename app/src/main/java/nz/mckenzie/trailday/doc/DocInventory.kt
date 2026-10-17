package nz.mckenzie.trailday.doc

import nz.mckenzie.trailday.domain.doc.DocSource

/**
 * What DOC says is in a box, before any of it is fetched.
 *
 * **Counts, not records**: `returnCountOnly` answers with a number rather than the material, so a walker
 * can be told what an area would take on before deciding to take it - the one thing a phone in the hills
 * cannot do afterwards is change its mind.
 *
 * The counts are DOC's own. The size is an estimate, and says so wherever it is shown: see [estimatedBytes].
 */
data class DocInventory(
    /** How many records are in the box, by service. A service with none is simply absent. */
    val records: Map<DocSource, Int>,
    /** How many pictures DOC addresses for those records. */
    val pictures: Int,
    /** The questions that could not be asked, with the phone's sentence about each. */
    val failed: Map<DocSource, String>
) {

    val totalRecords: Int get() = records.values.sum()

    /**
     * True when every question was answered, so every figure here is the whole truth about the box.
     *
     * A partial answer is **not** offered as an estimate: a service that could not be asked is a service
     * whose records and size are missing, and a walker told "500 KB" about an area with three services
     * unheard from has been misled rather than under-informed.
     */
    val complete: Boolean get() = failed.isEmpty()

    /**
     * What this would take on the phone, estimated.
     *
     * Only DOC knows how many vertices a track has before it is fetched, and a track's vertices are most
     * of what a download weighs - so this is arithmetic on sizes a real download produced ([DocSizes])
     * rather than a measurement. It is here to tell a small area from a large one, and it says "roughly"
     * wherever a walker reads it.
     */
    val estimatedBytes: Long get() = DocSizes.bytes(records) + DocSizes.pictureBytes(pictures)
}

/**
 * What each kind of thing weighed, measured on a real area.
 *
 * From a download of the Marlborough Sounds: 31 tracks at 10,977 bytes each (the biggest 56,330, because a
 * track is mostly its vertices), 7 campsites at 1,084 and 3 bike locations at 512, and the pictures DOC
 * publishes at about 8,000 each.
 *
 * **An estimate by nature.** A box of long Great Walk tracks weighs several times what this says, which is
 * why every screen that shows the figure says "roughly" beside it. What it is good for is the difference
 * between an afternoon's walk and half a national park, before the phone leaves reception.
 */
object DocSizes {

    /** A track, which is mostly the vertices DOC sends for it. */
    const val TRACK_BYTES = 11_000L

    /** A hut or a campsite: a page of DOC's own words and one coordinate. */
    const val PLACE_BYTES = 1_100L

    /** A bike location: a name, a difficulty and a time. */
    const val BIKE_BYTES = 500L

    /** One of DOC's pictures, which are thumbnails rather than photographs. */
    const val PICTURE_BYTES = 8_000L

    fun bytes(source: DocSource, count: Int): Long = count * when (source) {
        DocSource.TRACKS -> TRACK_BYTES
        DocSource.HUTS, DocSource.CAMPSITES -> PLACE_BYTES
        DocSource.BIKES -> BIKE_BYTES
    }

    fun bytes(records: Map<DocSource, Int>): Long =
        records.entries.sumOf { (source, count) -> bytes(source, count) }

    fun pictureBytes(pictures: Int): Long = pictures * PICTURE_BYTES
}

/**
 * What DOC says is in a box, in the walker's own terms: "3 tracks, 2 huts, 1 campsite and 3 pictures", or
 * "nothing" when there is nothing in it.
 *
 * DOC's own order, and DOC's own names, for the same reason every other sentence about its material is
 * built this way: two surfaces describing one service differently is how a walker comes to believe they
 * are two services.
 */
fun whatDocHasIn(inventory: DocInventory): String {
    val records = DocSource.entries.mapNotNull { source ->
        val count = inventory.records[source] ?: return@mapNotNull null
        if (count == 0) null else "$count ${if (count == 1) source.singular.lowercase() else source.displayName.lowercase()}"
    }
    val pictures = when {
        inventory.pictures == 0 -> null
        inventory.pictures == 1 -> "1 picture"
        else -> "${inventory.pictures} pictures"
    }
    val all = records + listOfNotNull(pictures)
    if (all.isEmpty()) return "nothing"
    // "and" rather than a comma before the last one, because that is how it is said out loud.
    return if (all.size == 1) all.first() else all.dropLast(1).joinToString(", ") + " and " + all.last()
}
