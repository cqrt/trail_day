package nz.mckenzie.trailday.domain.journey

import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.haversineMeters
import nz.mckenzie.trailday.domain.geo.nearestPointOnSegment
import kotlin.math.abs

/**
 * A walk put together out of tracks somebody else published.
 *
 * DOC publishes the Kepler Track as nine separate rows, and a walker who wants to follow it wants one
 * line, not nine. A journey is that line: the pieces DOC published, joined where they touch, cut where
 * the walker tapped, and kept as an ordinary track afterwards.
 *
 * The building is pure Kotlin, so the awkward parts - a tap in the middle of a section, a hole in DOC's
 * line, a side trip that is not the way through - are held by tests rather than found on a hill.
 */

/** One published track a journey can be built from: what DOC calls it, and its own paths. */
data class JourneyTrack(val name: String, val paths: List<List<GeoPoint>>)

/** One piece of the walk in the order it is walked. A bridge is a part DOC publishes no line for. */
data class JourneyPart(val points: List<GeoPoint>, val bridge: Boolean = false)

/** A walk that can be kept: the line, the parts it came from, and what it costs. */
data class JourneyPlan(
    val parts: List<JourneyPart>,
    val lengthM: Double,
    val bridgeM: Double,
    /** How many of DOC's rows the walk was cut from. */
    val sections: Int,
    /** The words every joined track shares, offered as a name: "Kepler" for the Kepler's nine. */
    val name: String
) {

    /**
     * The whole walk as one line, which is what is saved.
     *
     * A joining point is written once rather than twice: a line with the same point twice is a line the
     * length arithmetic has to be careful about, and nothing else has to be.
     */
    val line: List<GeoPoint>
        get() {
            val points = mutableListOf<GeoPoint>()
            for (part in parts) {
                val start = part.points.firstOrNull() ?: continue
                if (points.isNotEmpty() && points.last() == start) {
                    points += part.points.drop(1)
                } else {
                    points += part.points
                }
            }
            return points
        }
}

/** What came of asking for a journey. */
sealed interface JourneyResult {

    data class Built(val plan: JourneyPlan) : JourneyResult

    /** A tap landed further from every one of DOC's tracks than the reach. */
    data object NotOnATrack : JourneyResult

    /** DOC's tracks do not lead from one tapped point to the other. */
    data object NotConnected : JourneyResult
}

object JourneyBuilder {

    /**
     * How close a tap has to be to a track to land on it.
     *
     * **A quarter of a kilometre, because a thumb is that wide on a phone.** At the zoom a journey is
     * built at, the whole of a valley is on the screen and a finger covers a couple of hundred metres of
     * it, so a hundred metres had walkers tapping again and again at a line they could see they were
     * touching. It is still the *nearest* track that is taken, so a tap between two of DOC's lines lands
     * on the nearer one, and a tap that misses by more than this is told how far off it was rather than
     * quietly snapping to somewhere the walker did not mean.
     */
    const val SNAP_M = 250.0

    /** Two ends this close are the same place on the ground: one section carrying on from another. */
    const val TOUCH_M = 60.0

    /**
     * The widest hole in DOC's line that is bridged rather than refused.
     *
     * Measured on the Kepler: Hanging Valley and Iris Burn are 1,074 m apart and Rainbow Reach and Te
     * Anau 159 m, and both are part of the walk. Two kilometres takes those and stops short of joining
     * two unrelated tracks that happen to run near each other.
     */
    const val BRIDGE_M = 2_000.0

    /**
     * What a bridge costs the search on top of its own length.
     *
     * **A bridge is a hole, not a shortcut.** Without this the Kepler's nine rows would be joined by
     * straight lines wherever two of them happened to be close - the sections either side of a hole are
     * nearer to each other than the section that really goes between them - and the walk would cut across
     * ground nobody publishes. Ten kilometres is more than any bridge the app allows, so a way that
     * follows real tracks always wins, and a bridge is only taken when there is nothing else.
     */
    const val BRIDGE_PENALTY_M = 10_000.0

    /**
     * The walk from [picks] in order, or why there is none.
     *
     * Each pick extends the walk from the last, so this is the same question asked once per pair: which
     * way along DOC's tracks leads from here to there. Asking it per pair rather than as one long route
     * keeps a mistake - a tap on the wrong track - to the part it spoiled.
     */
    fun build(
        picks: List<GeoPoint>,
        tracks: List<JourneyTrack>,
        snapM: Double = SNAP_M,
        touchM: Double = TOUCH_M,
        bridgeM: Double = BRIDGE_M
    ): JourneyResult {
        if (picks.size < 2) return JourneyResult.NotConnected
        val chains = chainsOf(tracks)
        if (chains.isEmpty()) return JourneyResult.NotOnATrack

        val parts = mutableListOf<JourneyPart>()
        val used = linkedSetOf<Int>()
        var bridged = 0.0

        for (index in 1 until picks.size) {
            when (val step = leg(picks[index - 1], picks[index], chains, snapM, touchM, bridgeM)) {
                Leg.Missed -> return JourneyResult.NotOnATrack
                Leg.Unreachable -> return JourneyResult.NotConnected
                is Leg.Ok -> {
                    parts += step.parts
                    used += step.chains
                    bridged += step.bridgeM
                }
            }
        }

        if (parts.isEmpty()) return JourneyResult.NotConnected
        return JourneyResult.Built(
            JourneyPlan(
                parts = parts,
                lengthM = parts.sumOf { part -> lengthOf(part.points) },
                bridgeM = bridged,
                sections = used.size,
                name = sharedName(used.map { chains[it].name })
            )
        )
    }

    /**
     * Whether [point] is close enough to one of DOC's tracks to be a point on it.
     *
     * Asked of every tap, including the first: a walk that starts in a paddock is not a walk, and a first
     * point accepted without asking would only fail at the second tap, where the walker would read it as
     * the second point being wrong.
     */
    fun isOnATrack(point: GeoPoint, tracks: List<JourneyTrack>, snapM: Double = SNAP_M): Boolean =
        snap(point, chainsOf(tracks), snapM) != null

    /** How far [point] is from the nearest track, for saying so plainly when a tap misses. */
    fun nearestDistanceM(point: GeoPoint, tracks: List<JourneyTrack>): Double {
        val chains = chainsOf(tracks)
        if (chains.isEmpty()) return Double.MAX_VALUE
        return snap(point, chains, Double.MAX_VALUE)?.let { pick ->
            chains[pick.chain].pointAt(pick.arc).let { near -> haversineMeters(point.lat, point.lng, near.lat, near.lng) }
        } ?: Double.MAX_VALUE
    }

    // --- one leg ------------------------------------------------------------------------------

    private sealed interface Leg {
        data class Ok(val parts: List<JourneyPart>, val chains: List<Int>, val bridgeM: Double) : Leg
        data object Missed : Leg
        data object Unreachable : Leg
    }

    private fun leg(
        from: GeoPoint,
        to: GeoPoint,
        chains: List<Chain>,
        snapM: Double,
        touchM: Double,
        bridgeM: Double
    ): Leg {
        val start = snap(from, chains, snapM) ?: return Leg.Missed
        val finish = snap(to, chains, snapM) ?: return Leg.Missed

        // Both taps on one section: the way between them is that section, cut at both ends.
        if (start.chain == finish.chain) {
            return Leg.Ok(
                parts = listOf(JourneyPart(chains[start.chain].slice(start.arc, finish.arc))),
                chains = listOf(start.chain),
                bridgeM = 0.0
            )
        }

        val way = shortestWay(chains, start, finish, touchM, bridgeM) ?: return Leg.Unreachable

        val parts = mutableListOf<JourneyPart>()
        val used = linkedSetOf<Int>()
        var bridged = 0.0

        // Off the first section, to the end the way leaves by.
        val first = way.first()
        parts += JourneyPart(chains[start.chain].slice(start.arc, chains[start.chain].arcOf(first.port)))
        used += start.chain

        var previous = first
        for (node in way.drop(1)) {
            if (node.chain == previous.chain) {
                // Through this section, out of its other end.
                parts += JourneyPart(
                    chains[node.chain].slice(chains[node.chain].arcOf(previous.port), chains[node.chain].arcOf(node.port))
                )
            } else {
                // From one section's end to another's: touching, or a hole bridged straight.
                val gap = distanceBetween(chains[previous.chain].endAt(previous.port), chains[node.chain].endAt(node.port))
                if (gap > MIN_GAP_M) {
                    parts += JourneyPart(
                        points = listOf(
                            chains[previous.chain].endAt(previous.port),
                            chains[node.chain].endAt(node.port)
                        ),
                        bridge = gap > touchM
                    )
                    if (gap > touchM) bridged += gap
                }
            }
            used += node.chain
            previous = node
        }

        // Into the tapped point along the last section.
        parts += JourneyPart(
            chains[previous.chain].slice(chains[previous.chain].arcOf(previous.port), finish.arc)
        )

        return Leg.Ok(parts = parts, chains = used.toList(), bridgeM = bridged)
    }

    /**
     * The way from [start] to [finish] across the sections, as the ends it passes through.
     *
     * Dijkstra over **section ends** rather than vertices: the question is only which section end is
     * within reach of which other one, and is going that way shorter than the way already found. A
     * hundred sections is two hundred nodes, which is small enough to run on every tap.
     */
    private fun shortestWay(
        chains: List<Chain>,
        start: Pick,
        finish: Pick,
        touchM: Double,
        bridgeM: Double
    ): List<Node>? {
        val nodes = chains.size * 2
        val cost = DoubleArray(nodes) { Double.MAX_VALUE }
        val previous = IntArray(nodes) { -1 }

        cost[start.chain * 2] = start.arc
        cost[start.chain * 2 + 1] = chains[start.chain].length - start.arc

        val todo = mutableListOf<Int>()
        for (node in 0 until nodes) if (cost[node] < Double.MAX_VALUE) todo += node

        while (todo.isNotEmpty()) {
            val node = todo.minBy { cost[it] }
            todo.remove(node)
            val chain = node / 2
            val port = node % 2

            // Along the section to its other end.
            val other = chain * 2 + (1 - port)
            val through = cost[node] + chains[chain].length
            if (through < cost[other]) {
                cost[other] = through
                previous[other] = node
                if (other !in todo) todo += other
            }

            // To another section's end, if it is within reach.
            for (next in 0 until chains.size) {
                if (next == chain) continue
                for (nextPort in 0..1) {
                    val gap = distanceBetween(chains[chain].endAt(port), chains[next].endAt(nextPort))
                    if (gap > bridgeM) continue
                    val target = next * 2 + nextPort
                    // A bridged edge costs three times its own length as well, so a real track beats a straight
                    // line over the same ground - which is the difference between following the walk and
                    // cutting across it.
                    val candidate = cost[node] + if (gap > touchM) gap * 3 + BRIDGE_PENALTY_M else gap
                    if (candidate < cost[target]) {
                        cost[target] = candidate
                        previous[target] = node
                        if (target !in todo) todo += target
                    }
                }
            }
        }

        // Out of the finish's section, from whichever of its ends is nearer the tapped point.
        var best = Double.MAX_VALUE
        var bestNode = -1
        for (port in 0..1) {
            val node = finish.chain * 2 + port
            if (cost[node] == Double.MAX_VALUE) continue
            val rest = if (port == 0) finish.arc else chains[finish.chain].length - finish.arc
            if (cost[node] + rest < best) {
                best = cost[node] + rest
                bestNode = node
            }
        }
        if (bestNode < 0) return null

        val backwards = mutableListOf<Node>()
        var at = bestNode
        while (at >= 0) {
            backwards += Node(chain = at / 2, port = at % 2)
            at = previous[at]
        }
        backwards.reverse()
        if (backwards.isEmpty() || backwards.first().chain != start.chain) return null
        return backwards
    }

    private data class Node(val chain: Int, val port: Int)

    private data class Pick(val chain: Int, val arc: Double)

    // --- pieces of a line ---------------------------------------------------------------------

    /** One published path, with the arc lengths that slicing needs. */
    private class Chain(val name: String, val points: List<GeoPoint>) {

        private val cumulative = DoubleArray(points.size).also { seen ->
            for (index in 1 until points.size) {
                seen[index] = seen[index - 1] +
                    haversineMeters(points[index - 1].lat, points[index - 1].lng, points[index].lat, points[index].lng)
            }
        }

        val length: Double get() = cumulative.lastOrNull() ?: 0.0

        fun arcOf(port: Int): Double = if (port == 0) 0.0 else length

        fun endAt(port: Int): GeoPoint = if (port == 0) points.first() else points.last()

        /** The point at [arc] metres along, between the two vertices either side of it. */
        fun pointAt(arc: Double): GeoPoint {
            val wanted = arc.coerceIn(0.0, length)
            for (index in 1 until points.size) {
                if (cumulative[index] >= wanted) {
                    val span = cumulative[index] - cumulative[index - 1]
                    val t = if (span <= 0.0) 0.0 else (wanted - cumulative[index - 1]) / span
                    val a = points[index - 1]
                    val b = points[index]
                    return GeoPoint(lat = a.lat + (b.lat - a.lat) * t, lng = a.lng + (b.lng - a.lng) * t)
                }
            }
            return points.last()
        }

        /** The line from [fromM] to [toM], both ends interpolated onto it, in the order walked. */
        fun slice(fromM: Double, toM: Double): List<GeoPoint> {
            val forward = toM >= fromM
            val low = minOf(fromM, toM)
            val high = maxOf(fromM, toM)
            val points = mutableListOf(pointAt(low))
            for (index in 1 until this.points.size) {
                val arc = cumulative[index]
                if (arc > low && arc < high) points += this.points[index]
            }
            points += pointAt(high)
            return if (forward) points else points.reversed()
        }
    }

    private fun chainsOf(tracks: List<JourneyTrack>): List<Chain> =
        tracks.flatMap { track -> track.paths.filter { it.size >= 2 }.map { Chain(track.name, it) } }

    /** The nearest point on the nearest section, or null when the tap is further off than [snapM]. */
    private fun snap(point: GeoPoint, chains: List<Chain>, snapM: Double): Pick? {
        var best: Pick? = null
        var bestDistance = Double.MAX_VALUE
        chains.forEachIndexed { chainIndex, chain ->
            var travelled = 0.0
            for (index in 1 until chain.points.size) {
                val a = chain.points[index - 1]
                val b = chain.points[index]
                val near = nearestPointOnSegment(point, a, b)
                val away = distanceBetween(point, near)
                if (away < bestDistance) {
                    bestDistance = away
                    best = Pick(chainIndex, travelled + distanceBetween(a, near))
                }
                travelled += distanceBetween(a, b)
            }
        }
        return if (bestDistance <= snapM) best else null
    }

    private fun lengthOf(points: List<GeoPoint>): Double =
        (1 until points.size).sumOf { index -> distanceBetween(points[index - 1], points[index]) }

    private fun distanceBetween(a: GeoPoint, b: GeoPoint): Double =
        haversineMeters(a.lat, a.lng, b.lat, b.lng)

    /**
     * The words every joined name shares: "Kepler" for "Kepler Track - Control Gates To Brod Bay" and
     * "Kepler Tk - Luxmoore Saddle To Hanging V".
     *
     * Word by word and case-blind, stopping at the first word that differs. A walker can type over it,
     * so its only job is a name that is not absurd.
     */
    private fun sharedName(names: List<String>): String {
        val words = names.map { it.split(' ').filter { word -> word.isNotBlank() } }
        val first = words.firstOrNull().orEmpty()
        val shared = mutableListOf<String>()
        for (index in first.indices) {
            val word = first[index]
            if (words.all { it.size > index && it[index].equals(word, ignoreCase = true) }) {
                shared += word
            } else {
                break
            }
        }
        val name = shared.joinToString(" ").trim().trimEnd('-', ',', ':', '.').trim()
        return name.ifEmpty { "Journey" }
    }

    /** Below this a "connector" is the same point written twice and is not drawn at all. */
    private const val MIN_GAP_M = 0.5
}
