package nz.mckenzie.trailday.ui.screens

import nz.mckenzie.trailday.data.AssetWithDue
import nz.mckenzie.trailday.domain.asset.GroupableAsset
import nz.mckenzie.trailday.domain.asset.GroupTotals
import nz.mckenzie.trailday.ui.formatArea
import nz.mckenzie.trailday.ui.formatDistance

/**
 * One asset as the map's arithmetic sees it.
 *
 * The mapping is written once rather than once per screen, because it is a decision: what the map
 * comes to is the length of its lines, the ground and the estimates that make its area, and the work
 * still left - and a second copy of that mapping is a second chance to leave a field out of it, which
 * is how a screen quietly stops agreeing with the totals about what the map holds.
 */
internal fun AssetWithDue.asGroupable(): GroupableAsset = GroupableAsset(
    status = due.status,
    lengthM = asset.lengthM,
    swathWidthM = asset.swathWidthM,
    passesRequired = asset.passesRequired,
    groundSqm = asset.groundSqm
)

/**
 * What the whole map comes to, in the words the map's corner box says it in.
 *
 * The box has always answered "how much, and how urgent"; these lines add "how big", worked out by
 * the same arithmetic the totals use so the two cannot give different answers to one question. Every
 * line is left out when it is not known rather than shown as a zero - a map of places has no length
 * at all, and the area is one plain figure rather than a claim about how many of the places it
 * covers.
 *
 * Two lines, and no more: how long the work is and how much ground it covers. A third line used to
 * say how far there was still to drive, and it went - the counts above the rule already said how
 * much was left, and on a phone the line cost a fifth of the map to say something the box had just
 * said. The rule the box keeps is that a figure earns its line by being the only place it is said.
 */
internal fun farmStatsLines(totals: GroupTotals): List<String> {
    val lines = mutableListOf<String>()
    if (totals.lengthM > 0.0) lines += "Total length ${formatDistance(totals.lengthM)}"
    if (totals.areaSqm > 0.0) {
        lines += "Total area ${formatArea(totals.areaSqm)}"
    }
    return lines
}
