package nz.mckenzie.trailday.domain.doc

/**
 * What shape a service publishes: a line to travel along, or a place to stop at.
 *
 * One of the two things that differs between DOC's services, and the one that decides how a row is
 * read and drawn. The other is which fields it carries, which is the rest of this table.
 */
enum class DocShape { LINE, POINT }

/**
 * How many free name-and-value slots the tracks service publishes.
 *
 * Eleven, and it is DOC's number rather than the app's: the service keeps whatever characteristics
 * somebody needs by putting them in a pair of columns, and the app reads the pairs it has a word for
 * and keeps the rest as they arrived.
 *
 * Top-level rather than in the companion, because the table below is built from it: the companion does
 * not exist yet while the entries are being constructed.
 */
private const val CHARACTERISTIC_SLOT_COUNT = 11

/** The characteristic the app has a word for: one way, loop or return. */
private const val TRACK_TYPE_SLOT_NAME = "WEB_TRACK_TYPE"

/**
 * One of DOC's published services, with everything about it that the app has to know.
 *
 * Four sources, and the differences between them are real: tracks are lines drawn on the ground, and
 * huts, campsites and bike locations are single points. Their fields differ too - a hut says whether
 * it is bookable and what its facilities are, a bike location says how hard it is and how long it
 * takes, and a campsite says how many sites it has and whether they are powered. That is why this is a
 * table rather than a `when` somewhere: the same facts scattered across a URL builder, a parser, a
 * filter and a card is how two surfaces end up disagreeing about what DOC said.
 *
 * **This was read off the live services**, field by field, not assumed - see the test that pins each
 * one. DOC warns a replacement dataset is coming for these layers, so if a field goes the test fails
 * rather than a card quietly emptying.
 *
 * An id is what the app stores and what a cache row is keyed by, so it is never renamed. The service
 * name is DOC's own folder on their ArcGIS site.
 */
enum class DocSource(
    /** Stable id: it names the source in stored preferences and in a cache row. Never renamed. */
    val id: String,
    /** What the walker reads on a tab or a filter: "Huts", "Bike locations". */
    val displayName: String,
    /** One of these in the singular, for naming a row DOC gave no name to. */
    val singular: String,
    /** DOC's own service folder, under the host below. */
    val service: String,
    /** A line, or a place. */
    val shape: DocShape,
    /** The field DOC keeps the name in. Not the same field in every service. */
    val nameField: String,
    /**
     * What DOC says about each thing, beyond its name - the fields the app asks for and keeps.
     *
     * The values are DOC's own field names, and they arrive as text: a fact this build does not have a
     * word for is still kept, so a later build can read one without asking DOC again.
     */
    val fields: List<String>
) {

    /**
     * The tracks: DOC's walking and tramping network, as lines.
     *
     * The odd one out twice over. It is the only line service, so a row is a path and a length rather
     * than a coordinate; and its name lives in `TechObjectName` while the other three call it `name`.
     * Its extras are also the oddest: rather than fields of its own it publishes eleven free slots,
     * each a name and a value, and the app reads the one it knows (`WEB_TRACK_TYPE`: one way, loop or
     * return) - keeping the rest as they came.
     */
    TRACKS(
        id = "tracks",
        displayName = "Tracks",
        singular = "Track",
        service = "DOC_Tracks_EAM",
        shape = DocShape.LINE,
        nameField = "TechObjectName",
        fields = listOf("SubObjectType", "FlocID") + characteristicSlots(CHARACTERISTIC_SLOT_COUNT)
    ),

    /**
     * The huts.
     *
     * No bunk count: DOC does not publish one, and no amount of wanting it makes one appear. What it
     * does publish is whether a hut is bookable, its facilities line, its place and region, and a
     * picture. That line is often empty and that flag is often "No" - which is a fact about the hut,
     * not a missing field, and the two must not be shown as the same thing.
     */
    HUTS(
        id = "huts",
        displayName = "Huts",
        singular = "Hut",
        service = "DOC_Huts",
        shape = DocShape.POINT,
        nameField = "name",
        fields = listOf(
            "place", "region", "bookable", "facilities", "hasAlerts",
            "introductionThumbnail", "staticLink", "locationString"
        )
    ),

    /**
     * The campsites.
     *
     * The richest of the four: how many sites it has and whether they are powered, whether it is free
     * or bookable, whether dogs are allowed, and what is there. Several of those arrive as HTML - a
     * link inside a sentence - so the words are stripped before anybody reads them.
     */
    CAMPSITES(
        id = "campsites",
        displayName = "Campsites",
        singular = "Campsite",
        service = "DOC_Campsites",
        shape = DocShape.POINT,
        nameField = "name",
        fields = listOf(
            "place", "region", "introduction", "campsiteCategory", "numberOfPoweredSites",
            "numberOfUnpoweredSites", "bookable", "free", "facilities", "activities", "dogsAllowed",
            "landscape", "access", "hasAlerts", "introductionThumbnail", "staticLink", "locationString"
        )
    ),

    /**
     * The mountain bike locations.
     *
     * **Points, not lines**, and the name says so. DOC publishes where riding is permitted with a
     * difficulty and an estimated time - it is a finder for a track worth riding, not geometry to
     * follow, and presenting it as a line would be the app inventing a route.
     */
    BIKES(
        id = "bikes",
        displayName = "Bike locations",
        singular = "Bike location",
        service = "DOC_Mountain_Bike_Tracks",
        shape = DocShape.POINT,
        nameField = "name",
        fields = listOf(
            "introduction", "difficulty", "bikingTime", "hasAlerts", "introductionThumbnail",
            "mountainBikingTrackWebPage"
        )
    );

    /** Whether this source publishes lines rather than places. */
    val isLine: Boolean get() = shape == DocShape.LINE

    /**
     * What to ask DOC for: the key, the name, and everything this source says about a thing.
     *
     * `distinct` because a source that named one of its own fields as the name field would otherwise
     * ask for it twice, which ARC GIS accepts but which reads like a mistake.
     */
    val outFields: String
        get() = (listOf(OBJECT_ID_FIELD, nameField) + fields).distinct().joinToString(",")

    /** Where this service answers. The host is named once, below. */
    val serviceUrl: String get() = "$HOST/$service/$LAYER/query"

    companion object {
        /** DOC's own ArcGIS host: the same organisation for all four services. */
        const val HOST = "https://services1.arcgis.com/3JjYDyG3oajxU6HO/arcgis/rest/services"

        /** The one layer of each service the app reads. */
        const val LAYER = "FeatureServer/0"

        /** The key DOC numbers every feature with, and the one field every service has. */
        const val OBJECT_ID_FIELD = "OBJECTID"

        /**
         * How many free name-and-value slots the tracks service publishes.
         *
         * Eleven, and it is DOC's number rather than the app's: the service keeps whatever characteristics
         * somebody needs by putting them in a pair of columns, and the app reads the pairs it is asked
         * for and keeps the rest as they arrived.
         */
        const val CHARACTERISTIC_SLOTS = CHARACTERISTIC_SLOT_COUNT

        /** The characteristic the app has a word for: one way, loop or return. */
        const val TRACK_TYPE_CHARACTERISTIC = TRACK_TYPE_SLOT_NAME

        /** The source a stored id names, or null when nothing here does. */
        fun known(id: String?): DocSource? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The eleven name-and-value pairs, in the order DOC publishes them.
 *
 * A function rather than a constant list so the slots and the count cannot disagree - the count is
 * DOC's, and a build that asked for ten of eleven would lose a characteristic without saying so.
 */
private fun characteristicSlots(count: Int): List<String> =
    (1..count).flatMap { slot -> listOf("CharName$slot", "CharValue$slot") }
