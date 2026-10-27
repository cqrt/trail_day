package nz.mckenzie.trailday.domain.other

import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.reference.Followable
import nz.mckenzie.trailday.domain.text.PlainText

/**
 * The prefix a track imported from this dataset is marked with, in the walker's own places.
 *
 * **Never renamed**: it is written into `assets.sourceRef` and travels in a backup, so a later build
 * reading a different prefix would not recognise a track the walker had already taken on. It is also not
 * DOC's: DOC's own imports are `doc-…`, and the app drops those in one action that must not touch these.
 */
const val OTHER_TRACK_REF_PREFIX = "other:"

/**
 * The exact reference a track imported from this dataset is known by.
 *
 * Namespaced by the publisher for the reason DOC's is: an id is only unique within whoever issued it, and
 * on a bare UUID the already-imported check would be comparing a council's track with a hut's.
 */
fun otherTrackSourceRef(globalId: String): String = "$OTHER_TRACK_REF_PREFIX$globalId"

/**
 * One track from Herenga ā Nuku's National Tracks Dataset (Pilot): what the app keeps of a row.
 *
 * **This is not DOC's material and must not be kept as if it were.** The dataset is a merge of
 * sixteen-odd suppliers - district and regional councils, DOC, Te Araroa, Waka Kotahi, trail trusts and
 * a couple of aggregators - rebuilt monthly, and the app reads only the part of it that is nobody
 * else's: [OtherTracksUrl.where] is where DOC's and Te Araroa's rows are left behind, because the app
 * already carries both from their own publishers.
 *
 * **A row's only identifier is its `GlobalID`.** The dataset publishes an `ID` field too, and it is
 * null on every row that has been looked at; `OBJECTID` is the service's own counter, which is the one
 * thing a monthly rebuild is free to renumber. So the UUID is what the cache keys a row by. Whether it
 * really does survive a rebuild is **unverified** - it needs two months of data to check - which is the
 * second reason [facts] travels whole: a row that comes back under a new id is a row the app cannot
 * recognise, and the app's own "same name, same place, same point" rule is what would catch it.
 *
 * **The fields are the app's own words for the dataset's codes.** They arrive as numbers and as
 * shorthand (`Walk` is 1 to 6, `DogAccess` is `OnLeash`), and a page that showed a walker "3" or
 * "OnLeash" would be showing them the service's own bookkeeping. What the app has no word for is left
 * out rather than guessed at - see [OtherTrackAccess].
 */
data class OtherTrack(
    /** The service's UUID for the row. */
    val globalId: String,
    /** What the dataset calls it, or what [OtherTracksJson] names it when the dataset named it nothing. */
    override val name: String,
    /**
     * The line, as the paths the service published it in.
     *
     * Kept apart rather than joined, for the reason DOC's tracks are: a service that publishes a track
     * in pieces means it, and joining paths that do not meet draws a line across whatever is between
     * them. The one exception is [joinedByName], which joins the pieces of a walkway the dataset has cut
     * up - because there the paths *do* meet, and the walker is looking at one walkway.
     */
    override val paths: List<List<GeoPoint>>,
    /** The row exactly as it arrived, by the service's own field names. Never carries `GlobalID`. */
    val facts: Map<String, String> = emptyMap()
) : Followable {

    /** The reference a pass that follows this track records: see [Followable]. */
    override val sourceRef: String get() = otherTrackSourceRef(globalId)

    /** How long the line is, which is what a row and a page say about it. */
    val lengthM: Double get() = AssetGeometry(paths).lengthM

    /**
     * How it is walked, in the words the app already uses for DOC's tracks.
     *
     * Null for a grade the app has no word for, which includes "not open to walking", "permitted" and
     * "unknown": a chip reading "Unknown" is a chip that says nothing, and the row's own second line
     * already says who published the track.
     */
    val walkGrade: String? get() = OtherTrackAccess.walk(fact(WALK))

    /** How it may be ridden, from the mountain-bike grades. Null as [walkGrade] is. */
    val bikeGrade: String? get() = OtherTrackAccess.bike(fact(BIKE))

    /** What the dog rules are, or null when the dataset said nothing the app has a word for. */
    val dogAccess: String? get() = OtherTrackAccess.dogs(fact(DOG_ACCESS))

    /** Whether a wheelchair, mobility scooter or pushchair can use it, or null when that is unknown. */
    val mobilityAccess: String? get() = OtherTrackAccess.mobility(fact(MOBILITY_ACCESS))

    /**
     * Whether the track is open, temporarily closed, proposed or gone.
     *
     * **Null for everything else, including the junk.** The dataset's status field carries values
     * inherited from its sources - "Com", "Stratford", a single space, "Closed for lambing" - and a page
     * that repeated those would be repeating somebody's typo as a fact about a track. 435 rows of the
     * app's set have a status it will not repeat.
     */
    val status: String? get() = OtherTrackAccess.status(fact(STATUS))

    /**
     * Whether the dataset says the track is open, or null when it says nothing the app has a word for.
     *
     * Read from the word the table produced rather than by comparing the raw code, so that the page's
     * chip and the table cannot come to disagree about which status is the good one.
     */
    val isOpen: Boolean? get() = status?.let { it == OtherTrackAccess.OPEN }

    /**
     * Whether the dataset says a bike may be ridden on it.
     *
     * True for a graded track *and* for one the dataset simply marks as permitted: the second is 940 rows
     * of the app's set that a chip reading only grades would leave out, and "you may ride this" is the
     * answer the chip is about.
     */
    val isRideable: Boolean get() = bikeGrade != null || fact(BIKE) == PERMITTED

    /** Whether the dataset says a wheelchair, mobility scooter or pushchair can use it. */
    val suitsWheelchair: Boolean get() = mobilityAccess == OtherTrackAccess.SUITABLE

    /**
     * Who published it, each named once.
     *
     * The field is comma-separated where a track was merged from several suppliers, and a walker reading
     * a page wants the names rather than one string with commas in it - so the same split the row's own
     * filter uses.
     */
    val suppliers: List<String> get() = fact(SUPPLIER)
        .orEmpty()
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    /** Who looks after it, or null when the dataset does not say. 21% of the app's set says. */
    val managedBy: String? get() = fact(MANAGED_BY)?.takeIf { !it.equals(UNKNOWN, ignoreCase = true) }

    /** The area the dataset puts it in: a forest or a park, not a town. */
    val locality: String? get() = fact(LOCALITY)

    /** The territorial authority it falls in, which the dataset derives by spatial join. */
    val localAuthority: String? get() = fact(LOCAL_AUTHORITY)

    /** What has to be known before walking it: "cross only at low tide", "closed during lambing". */
    val conditions: String? get() = fact(CONDITIONS)

    /** What the dataset says about it, with any markup taken out. On 46% of the app's set. */
    val description: String? get() = PlainText.plain(fact(DESCRIPTION))

    /** Somebody else's page about it, usually Trailforks. On 29% of the app's set. */
    val webLink: String? get() = fact(WEB_LINK)

    /**
     * What this row's answer is to a filter's question, or nothing when the dataset said nothing.
     *
     * **A list rather than one value**, which is the one place this differs from DOC's material: a track
     * merged from several suppliers names all of them, and a walker narrowing by "Wellington City Council"
     * means a track that council had a hand in, not only one where it happens to be named first. Every
     * other fact has one answer.
     *
     * The answers are the app's own words for the dataset's codes - see [OtherTrackAccess] - so a filter's
     * chips and a page's chips cannot come to disagree about what a track is.
     */
    fun filterValues(field: String): List<String> = when (field) {
        WALK -> listOfNotNull(walkGrade)
        BIKE -> listOfNotNull(bikeGrade)
        DOG_ACCESS -> listOfNotNull(dogAccess)
        SUPPLIER -> suppliers
        else -> listOfNotNull(fact(field))
    }

    /** One fact, trimmed, or null when the dataset sent nothing or nothing but spaces. */
    private fun fact(field: String): String? = facts[field]?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val WALK = "Walk"
        const val BIKE = "Bike"
        const val DOG_ACCESS = "DogAccess"
        const val MOBILITY_ACCESS = "MobilityAccess"
        const val STATUS = "Status"
        const val SUPPLIER = "DataSupplier"
        const val MANAGED_BY = "ManagedBy"
        const val LOCALITY = "Locality"
        const val LOCAL_AUTHORITY = "LocalAuthority"
        const val CONDITIONS = "ConditionsOfUse"
        const val DESCRIPTION = "Description"
        const val WEB_LINK = "WebLink"

        /** The dataset's own word for a field it left unset. */
        const val UNKNOWN = "Unknown"

        /**
         * The code the dataset uses for "permitted, with no grade recorded", in the access fields.
         *
         * 98 rather than a word, and it means something different from 99 ("unknown"): a track nobody has
         * graded but everybody may use is not a track nobody knows about.
         */
        const val PERMITTED = "98"
    }
}

/**
 * The app's own words for the codes the dataset publishes.
 *
 * **A table rather than a `when` at each reader**, because the same code is read by the page, by a row
 * and - in the next piece of this work - by the browser's filters, and three readings of one code is how
 * a page and a list come to disagree about what a track is.
 *
 * **Codes the app has no word for map to null, and that is the rule rather than an omission.** The
 * dataset is a merge, and its coded fields carry values inherited from sixteen suppliers: a status of
 * "Com", a dog rule of "Dogs prohibited" beside the domain's own "Prohibited". Showing those as they
 * arrived would be repeating somebody's typo as a fact about a track, so the app says what it knows and
 * says nothing where it does not. What it *does* know was read off the service's own coded value
 * domains, not guessed.
 *
 * **The words are values under a label rather than sentences**, because that is how every reader uses
 * them: a page shows "Dogs: On lead only" and a filter offers "On lead only" under a Dogs heading, and a
 * table of full sentences would have both of them saying "dogs" twice.
 */
object OtherTrackAccess {

    /**
     * How it is walked. Codes 1 to 6 are the walking grades, and they are **DOC's own words** - Path,
     * Short Walk, Walking Track, Tramping Track, Challenging Tramping Track, Route - because the dataset
     * uses DOC's vocabulary for them, so a walker's words do not change between the two layers.
     */
    private val WALK = mapOf(
        "1" to "Path",
        "2" to "Short Walk",
        "3" to "Walking Track",
        "4" to "Tramping Track",
        "5" to "Challenging Tramping Track",
        "6" to "Route"
    )

    /** How it may be ridden: the mountain-bike grades, which are the same words the app's bike page uses. */
    private val BIKE = mapOf(
        "1" to "Easiest",
        "2" to "Easy",
        "3" to "Intermediate",
        "4" to "Advanced",
        "5" to "Expert",
        "6" to "Extreme"
    )

    /** What the dog rules are. */
    private val DOGS = mapOf(
        "OffLeash" to "Off lead",
        "OnLeash" to "On lead only",
        "Permit" to "Permit required",
        "ProhibitedAtTimes" to "Prohibited at times",
        "Prohibited" to "Not allowed"
    )

    /** Whether the dataset says a wheelchair, mobility scooter or pushchair can use it. */
    private val MOBILITY = mapOf(
        "Yes" to SUITABLE,
        "No" to "Not suitable"
    )

    /** Whether the track is there at all. `Decommisioned` is the dataset's own spelling. */
    private val STATUS = mapOf(
        "Open" to OPEN,
        "TempClosed" to "Temporarily closed",
        "Proposed" to "Proposed",
        "Decommisioned" to "Removed from use"
    )

    /** The one status that is not a warning: the app's word for it, and what a chip is compared against. */
    const val OPEN = "Open"

    /** The one mobility answer that is a yes, and what a chip is compared against. */
    const val SUITABLE = "Suitable"

    fun walk(code: String?): String? = WALK[code]

    fun bike(code: String?): String? = BIKE[code]

    fun dogs(code: String?): String? = DOGS[code]

    fun mobility(code: String?): String? = MOBILITY[code]

    fun status(code: String?): String? = STATUS[code]
}
