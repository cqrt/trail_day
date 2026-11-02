package nz.mckenzie.trailday.domain.track

import nz.mckenzie.trailday.domain.geo.GeoPoint

/**
 * When a walk that arrived in a file happened.
 *
 * **The file's own times, not its own date.** A walk exported from Strava, Garmin or a phone's recorder
 * carries the time of every fix, so the first one is when the walk began and the last is when it ended,
 * and the activity sorts where it belongs in the list. The file's date on disk says when it was copied
 * from machine to machine, which is not a fact about the walk, so it is never read.
 *
 * **No times is no length.** A GPX with no `<time>` in it, or a GeoJSON - which this app reads without
 * times at all - says nothing about when the walk happened beyond "the day it was brought in", and
 * nothing about how long it took. [endEpochMs] is then null, and the list and the page leave the
 * duration out rather than claiming "0s" next to a distance.
 */
data class ActivityDate(
    val startEpochMs: Long,
    val endEpochMs: Long?
) {
    /** True when the file gave times, which is what says whether a length can be claimed. */
    val hasTimes: Boolean get() = endEpochMs != null
}

object ActivityDates {

    /**
     * The date a track's own fixes give it, or today when they carry none.
     *
     * A zero time is [GeoPoint]'s way of saying "this fix has none" - a GPX written without `<time>`,
     * a KML with no `<when>`, anything from GeoJSON - so zeroes are dropped rather than treated as
     * 1970. One real time among them is still a time.
     */
    fun of(points: List<GeoPoint>, nowEpochMs: Long = System.currentTimeMillis()): ActivityDate {
        val times = points.map { it.timeMs }.filter { it > 0L }
        if (times.isEmpty()) return ActivityDate(startEpochMs = nowEpochMs, endEpochMs = null)
        return ActivityDate(startEpochMs = times.min(), endEpochMs = times.max())
    }
}
