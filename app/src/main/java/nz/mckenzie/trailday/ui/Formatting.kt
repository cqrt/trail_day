package nz.mckenzie.trailday.ui

import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.domain.other.OtherTrackAccess
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** "850 m" / "2.35 km" - walker-facing distances. */
fun formatDistance(meters: Double): String = when {
    meters < 0.0 -> "-"
    meters < 1000.0 -> "${meters.roundToInt()} m"
    else -> String.format(Locale.US, "%.2f km", meters / 1000.0)
}

/**
 * How long a place is, or empty when there is nothing to say.
 *
 * A spot has no length - it is somewhere you stop - and a line nothing has been drawn along has
 * none either, so both say **nothing** rather than "0 m": a zero is a measurement, and there is no
 * measurement here. The list's rows and a place's own page both ask this one question, so they
 * cannot drift into answering it differently.
 */
fun formatLengthOf(shape: AssetShape, lengthM: Double): String =
    if (shape == AssetShape.LINE && lengthM > 0.0) formatDistance(lengthM) else ""

/**
 * What a row of DOC's material says under the name, in the app's own words and DOC's own values.
 *
 * **One rule for four services, and it is here** rather than on the screen that draws it, because the
 * phone's browser and the desk's draw the same rows: a rule kept on one of them is a rule the other
 * would have to copy, and the copy that fell behind is the one nobody looks at. Which parts a summary
 * has is the *source's* answer, so a row cannot say about a hut what only makes sense about a track -
 * and a part DOC said nothing about is left out rather than shown blank, because "no facilities line"
 * and "nothing worth saying" read very differently.
 */
fun docRowDetail(item: DocItem, distanceM: Double?): String {
    val parts: List<String?> = when (item.source) {
        DocSource.TRACKS -> listOf(
            item.kind,
            // How the track is walked, which tells a walker what they are taking on where a vertex
            // count would tell them nothing.
            item.trackType,
            item.lengthM.takeIf { it > 0.0 }?.let { formatDistance(it) }
        )

        DocSource.HUTS -> listOf(
            item.place,
            item.region,
            "Bookable".takeIf { item.isBookable == true }
        )

        DocSource.CAMPSITES -> listOf(
            item.place,
            item.region,
            item.kind,
            campsiteSites(item),
            "Bookable".takeIf { item.isBookable == true }
        )

        // DOC publishes the bike layer as places: how hard the riding is and how long it takes are the
        // two things worth reading on the row, and there is no line to measure.
        DocSource.BIKES -> listOf(item.difficulty, item.bikingTime)
    }

    return (parts + distanceM?.let { "${formatDistance(it)} away" }).filterNotNull().joinToString(" \u00b7 ")
}

/**
 * The line under a row of somebody else's tracks: what it is, whether it is closed, and who publishes it.
 *
 * Here rather than in the browser's own file because **the desk's browser draws the same rows** - see
 * [nz.mckenzie.trailday.web.WebEditorJson.otherTracksSearch] - and a rule kept on one of the two is a rule
 * the other would have to copy. A status of "Open" is left off: it is the ordinary case, and a row that
 * said it on every one of seventeen thousand tracks would be saying nothing.
 */
fun otherTrackRowDetail(track: OtherTrack): String = listOfNotNull(
    track.walkGrade,
    track.status?.takeIf { it != OtherTrackAccess.OPEN },
    track.suppliers.firstOrNull()
).joinToString(" \u00b7 ")

/** The line under that: where the track is, which is the dataset's own two words for it. */
fun otherTrackRowWhere(track: OtherTrack): String? =
    listOfNotNull(track.locality, track.localAuthority)
        .distinct()
        .joinToString(", ")
        .takeIf { it.isNotEmpty() }

/** How many sites a campsite has, in the walker's terms, or null when DOC said nothing. */
private fun campsiteSites(item: DocItem): String? {    val unpowered = item.unpoweredSites ?: return null
    val powered = item.poweredSites ?: 0
    val total = unpowered + powered
    return when {
        total == 0 -> null
        powered > 0 -> "$total sites, $powered powered"
        else -> "$total sites"
    }
}

/**
 * What the map's card says about something DOC publishes once it has been tapped: the facts worth
 * knowing while standing where the thing is, one line each, in the app's words.
 *
 * The row's summary ([docRowDetail]) is a glance; this is the same material opened out - how a track
 * is walked and how long it is, where a hut is and whether it must be booked, what is at a campsite
 * and whether the dog can come, how hard a ride is - with DOC's own prose left to the card to show
 * below. One rule for four services, and it is here, because the map's card and a row are two views
 * of one answer and a second copy is the copy that falls behind.
 */
fun docCardFacts(item: DocItem): List<String> {
    val what = when (item.source) {
        DocSource.TRACKS -> listOfNotNull(
            item.kind,
            item.trackType,
            item.lengthM.takeIf { it > 0.0 }?.let { formatDistance(it) }
        )

        DocSource.HUTS -> listOfNotNull(item.kind)
        DocSource.CAMPSITES -> listOfNotNull(
            item.kind,
            campsiteSites(item)
        )

        DocSource.BIKES -> listOfNotNull(item.difficulty, item.bikingTime)
    }

    val where = listOfNotNull(item.place, item.region).distinct()

    return listOfNotNull(
        what.joinToString(" \u00b7 ").takeIf { it.isNotBlank() },
        where.joinToString(" \u00b7 ").takeIf { it.isNotBlank() },
        // DOC's own word, said as a sentence rather than as a flag: "No" and "nobody said" are different
        // answers about a hut, and a card that dropped the "No" would make a hut look bookable.
        item.bookable?.let { "Bookable: $it" },
        item.free?.let { "Free: $it" },
        item.facilities?.takeIf { it.isNotBlank() },
        item.dogsAllowed?.takeIf { it.isNotBlank() }?.let { "Dogs: $it" }
    ).distinct()
}

/**
 * What DOC says about riding a track, in the app's words: "Bike-friendly · Intermediate · 3 hr".
 *
 * A bike point DOC publishes on a track is a note about the track, not a place of its own. The line
 * says that it can be ridden, then DOC's own difficulty and time when it published them.
 */
fun docBikeLine(bike: DocItem): String =
    listOfNotNull("Bike track", bike.difficulty, bike.bikingTime).joinToString(" \u00b7 ")

/** DOC's difficulty and ride time alone, for a card whose header already says Bike track. */
fun docBikeFacts(bike: DocItem): String? =
    listOfNotNull(bike.difficulty, bike.bikingTime).joinToString(" \u00b7 ").takeIf { it.isNotBlank() }

/**
 * What DOC says about the place in prose, ready for the card, or null when it says nothing.
 *
 * Trimmed to a readable length: the card is a glance at a line under the walker's thumb, not the
 * page DOC publishes, and the last thing it should do is push the line off the screen.
 */
fun docCardProse(item: DocItem): String? {
    val prose = item.introduction?.takeIf { it.isNotBlank() } ?: return null
    val limit = 600
    return if (prose.length <= limit) prose else prose.take(limit).trimEnd() + "\u2026"
}

/** "45s" / "12m 30s" / "1h 05m" - elapsed recording time. */
fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "0s"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
        minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, seconds)
        else -> "${seconds}s"
    }
}

/**
 * How long an estimate says: "18 min", "2h 45 min", "3h".
 *
 * Deliberately not [formatDuration]. That one is a stopwatch's answer, to the second, because it is what
 * happened; an estimate in seconds would claim a precision nobody has, since the arithmetic under it is a
 * rule of thumb about a hill. So this counts in minutes, and a whole number of hours is "3h" rather than
 * "3h 00m".
 *
 * **Minutes are "min", not "m"**, and that is not fussiness: this app writes distances as "40 m", so an
 * estimate of "40m to go" reads as forty metres - which is a different thing to plan a walk around.
 */
fun formatEstimate(seconds: Long): String {
    val minutes = ceil(seconds / 60.0).toLong()
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> "${hours}h ${rest} min"
        hours > 0 -> "${hours}h"
        else -> "${minutes} min"
    }
}

/** "13 Sep 2026" - local calendar date of an instant, for a recording's date. */
fun formatDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMs).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))

/** "13 Sep" - a short label, used to suggest a name for a freshly recorded track. */
fun formatShortDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMs).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM", Locale.US))

/**
 * "46.4130°S, 168.3480°E" - where something is, in a form that works offline.
 *
 * A place name would read better, but reverse geocoding needs a connection and can
 * fail exactly when the walker is deciding what to cache *because* they are about
 * to lose reception.
 */
fun formatCoordinates(lat: Double, lng: Double): String = String.format(
    Locale.US,
    "%.4f\u00b0%s, %.4f\u00b0%s",
    abs(lat),
    if (lat < 0.0) "S" else "N",
    abs(lng),
    if (lng < 0.0) "W" else "E"
)
