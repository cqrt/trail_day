package nz.mckenzie.trailday.domain.doc

import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.reference.Followable
import nz.mckenzie.trailday.domain.text.PlainText

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
    override val name: String,
    /**
     * Where it is: **a line's paths, or a place's single coordinate**.
     *
     * One field for both shapes rather than two nullable ones, because the app's own geometry already
     * speaks in paths - a place is a path of one point, and every map, list and card reads a place that
     * way. The source's own shape says which it is; see [source].
     */
    override val paths: List<List<GeoPoint>>,
    /** What DOC says about it, by DOC's own field name. Never contains the id, which is [objectId]. */
    val facts: Map<String, String> = emptyMap()
) : Followable {

    /** How long the line is, or zero for a place. */
    val lengthM: Double get() = if (source.isLine) AssetGeometry(paths).lengthM else 0.0

    /** How many vertices it has, which for a place is always one. */
    val pointCount: Int get() = paths.sumOf { it.size }

    /** The coordinate, for a place. **Null for a line**, which has no one coordinate. */
    val point: GeoPoint? get() = if (source.isLine) null else paths.firstOrNull()?.firstOrNull()

    /** The reference the walker's own places are marked with once one of these is kept. */
    override val sourceRef: String get() = docSourceRef(source, objectId)

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
    val trackType: String? get() = characteristic(DocSource.TRACK_TYPE_CHARACTERISTIC)

    /** The walking time DOC publishes in its own range, such as "1-4 hours". */
    val walkingDuration: String? get() =
        fact("walkingDuration") ?: characteristic("WEB_WALKING_DURATION")

    /** The category DOC's website puts the track in, such as "Walking track". */
    val category: String? get() = fact("walkCategory") ?: characteristic("WEB_CATEGORY_TRACK")

    /** What DOC permits on the track, when it filled the slot in. */
    val permittedActivities: String? get() =
        PlainText.plain(fact("permittedActivities")) ?: characteristic("WEB_PERMITTED_ACTIVITIES")

    /** DOC's own words about wheelchairs and buggies on the track, when it has any. */
    val wheelchairsAndBuggies: String? get() = PlainText.plain(fact("wheelchairsAndBuggies"))

    /** How long DOC says the track takes by mountain bike, when it says. */
    val mtbDuration: String? get() = fact("mtbDuration")

    /** How hard DOC says the track is by mountain bike, when it says. */
    val mtbCategory: String? get() = fact("mtbTrackCategory")

    /** How long DOC says the track takes by kayak, when it says. */
    val kayakingDuration: String? get() = fact("kayakingDuration")

    /** The track's own status, such as "OPEN". */
    val status: String? get() = fact("status") ?: characteristic("USER_STATUS")

    /**
     * The one value under [name] in DOC's free name-and-value slots, or null when the row did not use
     * that slot. Tracks are the only service that publishes these.
     */
    fun characteristic(name: String): String? {
        for (slot in 1..DocSource.CHARACTERISTIC_SLOTS) {
            if (facts["CharName$slot"] == name) return fact("CharValue$slot")
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

    /**
     * Whether a hut or campsite has to be booked, read out of DOC's own word.
     *
     * Null when DOC said nothing, because "no" and "nobody said" are different answers and no row should
     * collapse them into one. DOC writes "Yes" or "No" as text; anything else is not a yes.
     */
    val isBookable: Boolean? get() = bookable?.let { it.equals("yes", ignoreCase = true) }

    /** What is there: water, toilets, a fire pit. DOC's words, with the links taken out. */
    val facilities: String? get() = PlainText.plain(fact("facilities"))

    /** What DOC says about the place in prose, for a campsite or a bike location. */
    val introduction: String? get() = PlainText.plain(fact("introduction"))

    /** What DOC says can be done here, for a campsite. */
    val activities: String? get() = PlainText.plain(fact("activities"))

    /** How to get to the place, for a campsite. */
    val access: String? get() = PlainText.plain(fact("access"))

    /** The landscape a campsite sits in. */
    val landscape: String? get() = PlainText.plain(fact("landscape"))

    /** Whether dogs are allowed here: DOC's words, which differ from site to site. */
    val dogsAllowed: String? get() =
        PlainText.plain(fact("dogsAllowed") ?: characteristic("WEB_DOGS_ALLOWED"))

    /** The sites a campsite has, and how many of them have power. */
    val unpoweredSites: Int? get() = fact("numberOfUnpoweredSites")?.toIntOrNull()
    val poweredSites: Int? get() = fact("numberOfPoweredSites")?.toIntOrNull()

    /** How hard a bike location is: "Easiest" through to "Extreme", in DOC's own words. */
    val difficulty: String? get() = fact("difficulty")

    /** How long DOC says the ride takes, as it wrote it: "3 hr". */
    val bikingTime: String? get() = fact("bikingTime")

    /** How long DOC's website says the track takes to complete, when it has said. */
    val completionTime: String? get() = fact("completionTime")

    /**
     * What DOC says about alerts, which is **not** whether there are any.
     *
     * Every row of every service carries the same sentence - "Refer to DOC website for current alerts" -
     * so it is a standing instruction rather than a fact about this place. Nothing in the app may read
     * this as "no alerts": see [link] for the page it points the walker at.
     */
    val alertsNote: String? get() = PlainText.plain(fact("hasAlerts"))

    /** The picture DOC published for it, if any: fetched and kept when the area is cached. */
    val pictureUrl: String? get() = fact("introductionThumbnail")

    /**
     * DOC's own page for it, which is where anything current - alerts above all - actually lives.
     *
     * Two fields because two services publish it under different names, and the walker's question is
     * the same one either way.
     */
    val link: String? get() =
        fact("staticLink") ?: fact("walkingAndTrampingWebPage") ?: fact("mountainBikingTrackWebPage")

    /** One fact, trimmed, or null when DOC sent nothing or nothing but spaces. */
    private fun fact(field: String): String? = facts[field]?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * What this row's answer is to a filter's question, or null when DOC said nothing.
     *
     * **One rule for every filter.** The browser's chips are built through this and its rows are matched
     * through this, so a chip can never offer a value that its own rows would not match - which is the
     * whole reason the filters are the source's own facts rather than a fixed list. The track type is
     * the one fact that is not a field of its own: it lives in one of the service's name-and-value
     * slots, and [trackType] is what finds it there.
     */
    fun filterValue(field: String): String? =
        if (field == DocSource.TRACK_TYPE_CHARACTERISTIC) trackType else fact(field)
}
