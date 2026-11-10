package nz.mckenzie.trailday.domain.track

import nz.mckenzie.trailday.domain.recording.ActivityMode

/**
 * Whether a file says its track was a walk or a ride, and in whose words.
 *
 * **A file from another app already knows what it was**, and asking the walker a question the file has
 * answered is the sort of thing that makes an import feel like paperwork. So the file's own type text
 * is read first, and only a file that says nothing is asked about.
 *
 * Two sources of words, strongest first:
 *
 * 1. **The type the file carries** - Garmin's `<type>cycling</type>`, a Strava export's `activityType`,
 *    a KML placemark's ExtendedData - read as single words with everything that is not a letter
 *    between them.
 * 2. **The track's own name and the file's own name.** `Taupo_rides.gpx` is a ride and so is
 *    `Wanaka bike loop`; a file called `Morning_Walk_2026-11-03.gpx` is a walk. Names are the weaker
 *    source and are only consulted when the type says nothing, because a type is a field and a name is
 *    whatever somebody typed.
 *
 * **Both, or neither, says nothing.** "Walk the bike shop" is a route either could have been walked
 * to; a name that names a bike and a walk is a name to ask about rather than to guess from. The same
 * goes for the type: a file claiming both is a file whose type is not worth believing.
 *
 * What this gets wrong is a file whose name is a place - "Bike Creek Track" walked on foot - and the
 * answer is the trip's own page, one tap after the import.
 */
object ActivityHint {

    /** The words that make a track ridden, lowercase and singular where it matters. */
    private val RIDE_WORDS = setOf(
        "ride", "rides", "riding",
        "bike", "bikes", "biking",
        "bicycle", "cycle", "cycles", "cycling",
        "mtb", "ebike", "emtb"
    )

    /** And the words that make it walked. A run is a walk here: it is somebody on their own two feet. */
    private val WALK_WORDS = setOf(
        "walk", "walks", "walking",
        "hike", "hikes", "hiking",
        "tramp", "tramps", "tramping",
        "run", "runs", "running"
    )

    /**
     * What a file says about one of its tracks, or null when it says nothing worth believing.
     *
     * [type] is the file's own type text, [trackName] what the file calls the track, and [fileName]
     * what the file is called with its extension already dropped - see
     * [FileTracks.baseNameOf].
     */
    fun of(type: String?, trackName: String?, fileName: String?): ActivityMode? =
        modeOf(wordsIn(type))
            ?: modeOf(wordsIn(trackName) + wordsIn(fileName))

    /** The letters a text is made of, lowercased: "Taupo_rides" is "taupo" and "rides". */
    private fun wordsIn(text: String?): List<String> =
        text.orEmpty().lowercase().split(NOT_A_LETTER).filter { it.isNotEmpty() }

    /** The one side that was named, or null when both were or neither was. */
    private fun modeOf(words: List<String>): ActivityMode? {
        val rides = words.any { it in RIDE_WORDS }
        val walks = words.any { it in WALK_WORDS }
        return when {
            rides && !walks -> ActivityMode.RIDE
            walks && !rides -> ActivityMode.WALK
            else -> null
        }
    }

    private val NOT_A_LETTER = Regex("[^a-z]+")
}
