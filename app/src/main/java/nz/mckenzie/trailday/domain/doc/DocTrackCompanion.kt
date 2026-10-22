package nz.mckenzie.trailday.domain.doc

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.track.GeoJsonParser

/**
 * The website-side facts about one of DOC's tracks.
 *
 * The line service is the geometry and its characteristics; this companion layer is the summary the
 * website writes above the map: a short introduction, a difficulty, a completion time, a link to the
 * full page and a thumbnail. It arrives from a separate public layer and is merged into the track's
 * facts so the rest of the app can read it through [DocItem] like any other DOC answer.
 */
data class DocTrackCompanion(
    val introduction: String? = null,
    val difficulty: String? = null,
    val completionTime: String? = null,
    val webPage: String? = null,
    val thumbnail: String? = null,
    /** The category the walk falls in, such as "1-4 hours", when the API gives both it and an exact time. */
    val walkingDuration: String? = null,
    /** What DOC permits here, joined into one line for the facts grid. */
    val permittedActivities: String? = null,
    /** DOC's own words about wheelchairs and buggies, when it has any. */
    val wheelchairsAndBuggies: String? = null,
    /** The mountain-bike facts, when DOC publishes them for the same track. */
    val mtbDuration: String? = null,
    val mtbCategory: String? = null,
    /** How long the track takes by kayak, when DOC says. */
    val kayakingDuration: String? = null,
    /** DOC's fuller dog rules, which often add a sentence to the line service's short answer. */
    val dogsAllowed: String? = null,
    /** The place DOC's website puts the track in, in its own words. */
    val place: String? = null,
    /** The region DOC's website puts the track in. */
    val region: String? = null
)

/**
 * [this] track with the website's summary folded into its facts.
 *
 * Only non-blank answers are added: a missing value must not replace a fact the line service did
 * publish. The keys are the same ones the other services use - `introduction`, `difficulty`,
 * `completionTime`, `walkingAndTrampingWebPage` - so [DocItem]'s existing getters read them without
 * a second vocabulary.
 */
fun DocItem.withCompanion(companion: DocTrackCompanion): DocItem = copy(
    facts = facts + buildMap {
        companion.introduction?.takeIf { it.isNotBlank() }?.let { put("introduction", it) }
        companion.difficulty?.takeIf { it.isNotBlank() }?.let { put("difficulty", it) }
        companion.completionTime?.takeIf { it.isNotBlank() }?.let { put("completionTime", it) }
        companion.webPage?.takeIf { it.isNotBlank() }
            ?.let { put("walkingAndTrampingWebPage", it) }
        companion.thumbnail?.takeIf { it.isNotBlank() }
            ?.let { put("introductionThumbnail", it) }
        companion.walkingDuration?.takeIf { it.isNotBlank() }
            ?.let { put("walkingDuration", it) }
        companion.permittedActivities?.takeIf { it.isNotBlank() }
            ?.let { put("permittedActivities", it) }
        companion.wheelchairsAndBuggies?.takeIf { it.isNotBlank() }
            ?.let { put("wheelchairsAndBuggies", it) }
        companion.mtbDuration?.takeIf { it.isNotBlank() }?.let { put("mtbDuration", it) }
        companion.mtbCategory?.takeIf { it.isNotBlank() }?.let { put("mtbTrackCategory", it) }
        companion.kayakingDuration?.takeIf { it.isNotBlank() }
            ?.let { put("kayakingDuration", it) }
        companion.dogsAllowed?.takeIf { it.isNotBlank() }?.let { put("dogsAllowed", it) }
        companion.place?.takeIf { it.isNotBlank() }?.let { put("place", it) }
        companion.region?.takeIf { it.isNotBlank() }?.let { put("region", it) }
    }
)

/** One walking-experience point from DOC's website layer, before it is matched to a track line. */
data class DocCompanion(
    val name: String,
    val point: GeoPoint?,
    val introduction: String? = null,
    val difficulty: String? = null,
    val completionTime: String? = null,
    val webPage: String? = null,
    val thumbnail: String? = null
) {
    /** The website facts, in the shape a track's facts are merged with. */
    fun summary(): DocTrackCompanion = DocTrackCompanion(
        introduction = introduction,
        difficulty = difficulty,
        completionTime = completionTime,
        webPage = webPage,
        thumbnail = thumbnail
    )
}

/**
 * Reads the walking-experience layer's own GeoJSON.
 *
 * The layer is a set of points, one per website page, with the summary above the page's map in its
 * properties. A feature with no name or no point is left out: without either it cannot be matched to a
 * track, and attaching it to the nearest line would be the app guessing which track DOC meant.
 */
object DocCompanionJson {
    fun parse(text: String): List<DocCompanion> = runCatching {
        GeoJsonParser.parse(text).mapNotNull { feature ->
            val name = feature.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (feature.point == null) return@mapNotNull null
            val facts = feature.properties
            DocCompanion(
                name = name,
                point = feature.point,
                introduction = facts["introduction"]?.let(DocText::plain),
                difficulty = facts["difficulty"]?.trim()?.takeIf { it.isNotEmpty() },
                completionTime = facts["completionTime"]?.trim()?.takeIf { it.isNotEmpty() },
                webPage = facts["walkingAndTrampingWebPage"]?.trim()?.takeIf { it.isNotEmpty() },
                thumbnail = facts["introductionThumbnail"]?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
    }.getOrDefault(emptyList())
}

/**
 * Finds the website summary that belongs to one track line.
 *
 * The line service calls a track "Forks Flat Tk" and the website layer calls the same track "Forks
 * Flat Track", so the trailing kind is dropped before names are compared. The point is then checked
 * against the line: a common stem in another part of the country must not put a different track's
 * summary on this one.
 */
object DocTrackCompanions {

    /** How far a website point may sit from its line and still be the same track, in metres. */
    private const val MATCH_M = 5_000.0

    fun bestFor(track: DocItem, companions: List<DocCompanion>): DocCompanion? {
        val stem = DocTrackNames.stem(track.name)
        val sameName = companions.filter {
            it.point != null && DocTrackNames.stem(it.name).equals(stem, ignoreCase = true)
        }
        return sameName.mapNotNull { companion ->
            val distance = DocHitTest.distanceTo(track, companion.point!!) ?: return@mapNotNull null
            if (distance <= MATCH_M) companion to distance else null
        }.minByOrNull { (_, distance) -> distance }?.first
    }
}

/**
 * The part of a track's name that is the track, rather than the kind of thing it is.
 *
 * One rule for the detail page and the offline download, so a track downloaded with its summary is
 * the same track the page later finds by name.
 */
object DocTrackNames {
    fun stem(name: String): String {
        val words = name.trim().split(Regex("\\s+")).toMutableList()
        if (words.size > 1) {
            val last = words.last().lowercase().trim('.')
            if (last in setOf("tk", "track", "walk", "walkway", "trail", "route")) {
                words.removeAt(words.lastIndex)
            }
        }
        return words.joinToString(" ").trim().ifBlank { name.trim() }
    }
}
