package nz.mckenzie.trailday.domain.journey

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.PathPoint
import nz.mckenzie.trailday.domain.geo.nearestOnPath
import nz.mckenzie.trailday.domain.geo.pathFrom
import nz.mckenzie.trailday.domain.geo.polylineLengthMeters
import nz.mckenzie.trailday.domain.reference.Followable
import kotlin.math.abs

/**
 * Walking onto another line, without the walker touching the phone.
 *
 * Following a published track is measured against that track and nothing else, so a walker who cuts a
 * corner, or who takes the footpath where the published line carries straight on, keeps being measured
 * against ground they left. [SectionHandOver] answers the same question at the **end** of a section; this
 * answers it in the middle of one.
 *
 * **Three numbers, and the first two are also the guard against flapping.** Off the line by
 * [OFF_FOLLOWED_M] before it looks at all, on the other line by [ON_CANDIDATE_M] to count, and
 * [SETTLED_M] of progress *along* it before it believes the walker. That last one is what tells walking a
 * line from crossing it: a walker crossing at a right angle is within 20 m of the line for 40 m of
 * walking, but almost none of it is progress along the line.
 *
 * The state - which line is being considered, and how far along it the walker has come since - is held
 * here rather than by the caller, the way [nz.mckenzie.trailday.domain.geo.DriftWord] holds its own. One
 * fix is handed in and an answer comes out, so the rule can be tested without a recorder, a screen or a
 * clock.
 *
 * **Silence is the ordinary answer.** Most of a walk is on the line, or off every line, and both answer
 * nothing.
 */
class JoiningTrack(
    /** How far off the followed line the walker has to be before this looks at anything else. */
    private val offFollowedM: Double = OFF_FOLLOWED_M,
    /** How near another line counts as being on it. */
    private val onCandidateM: Double = ON_CANDIDATE_M,
    /** How far along the other line the walker must have walked, in one direction, before it counts. */
    private val settledM: Double = SETTLED_M
) {

    private var candidate: Candidate? = null

    /**
     * One fix, and the line the walker has walked onto - or nothing, which is the usual answer.
     *
     * [followed] is the piece being measured, [offFollowed] how far the walker is from it and [at] where
     * they are. [candidates] are the published lines worth considering at all, already narrowed to those
     * within reach: this rule measures the few it is handed rather than searching the country on every fix.
     */
    fun consider(
        followed: List<GeoPoint>,
        at: GeoPoint,
        offFollowed: Double,
        candidates: List<Followable>
    ): Join? {
        if (followed.size < 2 || offFollowed < offFollowedM) {
            reset()
            return null
        }

        val found = nearest(at, followed, candidates)
        if (found == null) {
            reset()
            return null
        }

        val held = candidate
        if (held == null || held.track.sourceRef != found.track.sourceRef || held.pathIndex != found.pathIndex) {
            // A different line, or the first fix on this one: how far along it the walker has come starts
            // again from here, so arriving does not count as walking it.
            candidate = found.copy(joinedAlongM = found.on.alongM, lastAlongM = found.on.alongM)
            return null
        }

        val progress = found.on.alongM - held.joinedAlongM
        val wasGoing = held.lastAlongM - held.joinedAlongM
        if (progress != 0.0 && wasGoing != 0.0 && (progress > 0) != (wasGoing > 0)) {
            // Turned round. The ground covered one way is not the start of walking the other.
            candidate = found.copy(joinedAlongM = found.on.alongM, lastAlongM = found.on.alongM)
            return null
        }
        candidate = found.copy(joinedAlongM = held.joinedAlongM, lastAlongM = found.on.alongM)

        if (abs(progress) < settledM) return null

        // Turned to start where the walker joined, and cut at where they are, so the readout says 0 m of
        // what is left rather than how far along somebody else's line they happen to be.
        val line = pathFrom(found.path, found.on.alongM, forward = progress > 0)
        if (line.size < 2) return null
        return Join(track = found.track, line = line, atAlongM = found.on.alongM)
    }

    /** A new target, or a line let go: whatever was true of the last line is not true of this one. */
    fun reset() {
        candidate = null
    }

    /**
     * The nearest candidate path the walker is standing on, or null when they are on none of them.
     *
     * The line being walked is left out, and so is the published line it was cut from - see
     * [isUnderfoot]. A *different* piece of the same track is allowed: a walkway the dataset cut into nine
     * rows is one walkway, and stepping from one row onto the next is the walk carrying on, which changes
     * no name and says nothing.
     */
    private fun nearest(at: GeoPoint, followed: List<GeoPoint>, candidates: List<Followable>): Candidate? {
        var best: Candidate? = null
        for (track in candidates) {
            track.paths.forEachIndexed { index, path ->
                if (path.size < 2) return@forEachIndexed
                val on = nearestOnPath(path, at) ?: return@forEachIndexed
                if (on.offM > onCandidateM) return@forEachIndexed
                if (isUnderfoot(path, followed)) return@forEachIndexed
                val closest = best
                if (closest == null || on.offM < closest.on.offM) {
                    best = Candidate(track, index, path, on, on.alongM, on.alongM)
                }
            }
        }
        return best
    }

    /** One path the walker is standing on, and how far along it they were when this line was first seen. */
    private data class Candidate(
        val track: Followable,
        val pathIndex: Int,
        val path: List<GeoPoint>,
        val on: PathPoint,
        val joinedAlongM: Double,
        val lastAlongM: Double
    )
}

/** What the recorder moves onto: whose line it is, and the piece ahead of where the walker joined it. */
data class Join(
    val track: Followable,
    /** The line to be measured from now on: it starts where the walker joined and runs the way they are going. */
    val line: List<GeoPoint>,
    /** How far along the published line the walker was when they joined it. */
    val atAlongM: Double
)

/**
 * Whether [candidate] is the ground the walker is already being measured against.
 *
 * Two ways to be the same ground, and the second is the one that is easy to miss. A published piece is
 * left out by its two ends, either way round. A piece the recorder **cut** from a published line - which
 * is what a mid-route hand-over hands over - no longer has that line's ends at all, so it is left out by
 * its own ends both lying on the candidate: a piece of a line is still that line, and without this the
 * rule would join the walker to the line they are already on every twenty metres, for ever.
 */
private fun isUnderfoot(candidate: List<GeoPoint>, followed: List<GeoPoint>): Boolean {
    if (isTheSamePiece(candidate, followed)) return true
    if (polylineLengthMeters(followed) > polylineLengthMeters(candidate) + 1.0) return false
    val start = nearestOnPath(candidate, followed.first()) ?: return false
    val end = nearestOnPath(candidate, followed.last()) ?: return false
    return start.offM < 1.0 && end.offM < 1.0
}

/**
 * How far off the followed line the walker has to be before another line is considered.
 *
 * **Thirty metres, and it is a guard as much as a trigger.** Switched onto a line, the walker is on it, so
 * the rule is asleep until they leave it by this much - which is what stops a junction, or two lines
 * running a few metres apart, from handing the walker back and forth on every fix. A line that is merely
 * coarse, or a fix that wanders, does not start it looking; a deliberate step off a track does.
 */
const val OFF_FOLLOWED_M = 30.0

/** How near another line counts as standing on it. A track's own width, and then some. */
const val ON_CANDIDATE_M = 20.0

/**
 * How far along the other line the walker must have walked, in one direction, to have walked it.
 *
 * Twenty metres, which is four or five fixes, and it is measured **along the line** rather than as the
 * crow flies. That is what a crossing cannot do: a walker crossing at a right angle spends 40 m of walking
 * within 20 m of the line and almost none of it moving along it.
 */
const val SETTLED_M = 20.0
