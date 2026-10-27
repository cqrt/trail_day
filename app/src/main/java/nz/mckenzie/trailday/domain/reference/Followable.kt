package nz.mckenzie.trailday.domain.reference

import nz.mckenzie.trailday.domain.geo.GeoPoint

/**
 * A line somebody published, which the walker can ask the recorder to follow.
 *
 * **The three things following needs, and nothing about who published it.** DOC's tracks and the tracks
 * councils and trusts publish both reach the recorder the same way - the walker taps "Follow this" on a
 * page or a card, the screen changes to the recorder, and the line becomes what the pass is measured
 * against - so the recorder is handed these three and no more. A page, a card and a map tap therefore
 * offer one action between them rather than one per publisher.
 *
 * [reference] is what the pass records it followed, so a recorded walk remembers which line it was
 * against: `doc-…` for DOC's material, `other:…` for the national tracks dataset.
 */
interface Followable {

    /** The exact reference of the line, as the source names it. Written into a pass that follows it. */
    val sourceRef: String

    /** What the line is called, which the recorder shows while a walk is being followed. */
    val name: String

    /** The line, as the paths it was published in. A path of one point is not a line. */
    val paths: List<List<GeoPoint>>
}
