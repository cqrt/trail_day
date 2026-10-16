package nz.mckenzie.trailday.domain.doc

import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint

/**
 * The exact reference DOC gives one of its things, namespaced by the service it came from.
 *
 * **The namespacing is not decoration.** DOC numbers its features per service, so a hut and a track
 * really can both be `123` - and on a bare number the app would treat the second as the first: the
 * already-imported check would refuse a hut because a track with that number was on the phone, and a
 * cached hut would overwrite a cached track. So the reference names the source as well, and it is
 * written the same way wherever it is compared, stored or carried in a backup.
 */
fun docSourceRef(source: DocSource, objectId: Long): String = "doc-${source.id}:$objectId"

/**
 * One thing DOC publishes: a track, a hut, a campsite or a bike location.
 *
 * Four services, one shape of record, because every surface that reads DOC wants the same three things
 * - what it is called, where it is, and what DOC says about it - and wants them the same way whichever
 * service they came from. What differs is kept rather than flattened: [facts] is the row exactly as
 * DOC sent it, and the properties below are the app's own words for the facts it has words for.
 *
 * **The facts are kept as they arrived.** The app reads a name, a bookable flag, a facilities line and
 * so on, and a build that learns to read one more - or DOC that adds one - needs no new code to
 * *carry* it. That is also why they are stored as they came: a cached hut keeps what DOC said about it,
 * so a hut with no reception reads exactly as it did with one.
 */
data class DocItem(
    /** Which service it came from. */
    val source: DocSource,
    /** DOC's own number for it, unique within its service and not across services. */
    val objectId: Long,
    /** What DOC calls it. Never blank: a row with no name is named after its number. */
    val name: String,
    /**
     * Where it is: **a line's paths, or a place's single coordinate**.
     *
     * One field for both shapes rather than two nullable ones, because the app's own geometry already
     * speaks in paths - a place is a path of one point, and every map, list and card reads a place that
     * way. The source's own shape says which it is; see [source].
     */
    val paths: List<List<GeoPoint>>,
    /** What DOC says about it, by DOC's own field name. Never contains the id, which is [objectId]. */
    val facts: Map<String, String> = emptyMap()
) {

    /** How long the line is, or zero for a place. */
    val lengthM: Double get() = if (source.isLine) AssetGeometry(paths).lengthM else 0.0

    /** How many vertices it has, which for a place is always one. */
    val pointCount: Int get() = paths.sumOf { it.size }

    /** The coordinate, for a place. **Null for a line**, which has no one coordinate. */
    val point: GeoPoint? get() = if (source.isLine) null else paths.firstOrNull()?.firstOrNull()

    /** The reference the walker's own places are marked with once one of these is kept. */
    val sourceRef: String get() = docSourceRef(source, objectId)

    /** The place DOC puts it in - its own field where it has one, the app's map where it does not. */
    val place: String? get() = fact("place") ?: DocPlaces.placeFor(flocId)

    /** The region DOC puts it in. Huts and campsites have one; tracks and bike locations do not. */
    val region: String? get() = fact("region")

    /**
     * DOC's own kind for it: "Walking Track", "Standard" for a campsite.
     *
     * Not the app's kind - which source it came from is - and kept because it is DOC's answer about what
     * something is.
     */
    val kind: String? get() = fact("SubObjectType") ?: fact("campsiteCategory")

    /**
     * How a track is walked - one way, a loop, a return - from the free slots the tracks service keeps.
     *
     * The value is found by looking for its name in whichever slot DOC put it in, rather than by
     * reading slot one: the service makes no promise about the order, and a track whose type is in
     * slot seven is not a track without one.
     */
    val trackType: String?
        get() {
            val slots = DocSource.CHARACTERISTIC_SLOTS
            for (slot in 1..slots) {
                if (facts["CharName$slot"] == DocSource.TRACK_TYPE_CHARACTERISTIC) {
                    return fact("CharValue$slot")
                }
            }
            return null
        }

    /** The site code DOC uses for a track's place. Tracks only, and only to work out the place. */
    val flocId: String? get() = fact("FlocID")

    /**
     * Whether a hut or a campsite must be booked, in DOC's own word.
     *
     * DOC's text, not a boolean the app invented: "No" and an empty field are different answers, and a
     * flag that turned both into `false` would lose the difference.
     */
    val bookable: String? get() = fact("bookable")

    /** Whether a campsite is free, in DOC's own word. Null when DOC did not say. */
    val free: String? get() = fact("free")

    /** What is there: water, toilets, a fire pit. DOC's words, with the links taken out. */
    val facilities: String? get() = DocText.plain(fact("facilities"))

    /** What DOC says about the place in prose, for a campsite or a bike location. */
    val introduction: String? get() = DocText.plain(fact("introduction"))

    /** Whether dogs are allowed here: DOC's words, which differ from site to site. */
    val dogsAllowed: String? get() = DocText.plain(fact("dogsAllowed"))

    /** The sites a campsite has, and how many of them have power. */
    val unpoweredSites: Int? get() = fact("numberOfUnpoweredSites")?.toIntOrNull()
    val poweredSites: Int? get() = fact("numberOfPoweredSites")?.toIntOrNull()

    /** How hard a bike location is: "Easiest" through to "Extreme", in DOC's own words. */
    val difficulty: String? get() = fact("difficulty")

    /** How long DOC says the ride takes, as it wrote it: "3 hr". */
    val bikingTime: String? get() = fact("bikingTime")

    /**
     * What DOC says about alerts, which is **not** whether there are any.
     *
     * Every row of every service carries the same sentence - "Refer to DOC website for current alerts" -
     * so it is a standing instruction rather than a fact about this place. Nothing in the app may read
     * this as "no alerts": see [link] for the page it points the walker at.
     */
    val alertsNote: String? get() = DocText.plain(fact("hasAlerts"))

    /** The picture DOC published for it, if any: fetched and kept when the area is cached. */
    val pictureUrl: String? get() = fact("introductionThumbnail")

    /**
     * DOC's own page for it, which is where anything current - alerts above all - actually lives.
     *
     * Two fields because two services publish it under different names, and the walker's question is
     * the same one either way.
     */
    val link: String? get() = fact("staticLink") ?: fact("mountainBikingTrackWebPage")

    /** One fact, trimmed, or null when DOC sent nothing or nothing but spaces. */
    private fun fact(field: String): String? = facts[field]?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * DOC's own words, with the markup taken out.
 *
 * Several fields arrive with a link inside the sentence - a campsite's dog rules point at a DOC office,
 * its facilities point at a fire ban - and the app draws plain text, so what is left of
 * `Dogs with a permit only. Contact closest <a href="#contacts">DOC office</a>` has to be the sentence.
 *
 * A tag is dropped whole, including one that was cut off part-way: DOC's own fields are truncated
 * mid-tag on the live service, and half a tag is not something to show anyone.
 */
object DocText {

    /** [html] as plain words, or null when there is nothing but markup or spaces in it. */
    fun plain(html: String?): String? {
        if (html.isNullOrBlank()) return null
        val words = StringBuilder()
        var insideTag = false
        for (character in html) {
            when {
                character == '<' -> insideTag = true
                character == '>' -> insideTag = false
                !insideTag -> words.append(character)
            }
        }
        return words.toString().replace(WHITESPACE, " ").trim().takeIf { it.isNotEmpty() }
    }

    private val WHITESPACE = Regex("\\s+")
}
