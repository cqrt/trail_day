package nz.mckenzie.trailday.domain.doc

import nz.mckenzie.trailday.domain.geo.TravelMode
import nz.mckenzie.trailday.domain.geo.TravelTime

/**
 * One thing DOC publishes, picked on the map: the item itself, and how far the phone is from it *now*.
 *
 * The distance is measured again on every fix rather than kept from the moment of the tap. A card that said
 * how far away a track was when it was tapped would be wrong by the time the walker had walked to the end of
 * the sentence - and a card under a walker's nose saying "80 m away" about the track they are standing on is
 * worse than one that says nothing.
 */
data class DocPick(
    val item: DocItem,
    /** How far the phone is from it, in metres on the ground; null when the phone does not know where it is. */
    val distanceM: Double?,
    /**
     * The picture DOC published for it, when the phone kept one with the area.
     *
     * A picture is a copy of something DOC publishes and arrives with the material around it; a phone
     * that has kept none has none, and the card is still a card without it.
     */
    val picture: ByteArray? = null,
    /**
     * DOC's bike point for this thing, when it is a track that may be ridden.
     *
     * The bike point is a note about the track rather than a place of its own: the card says what DOC
     * says about riding it, and the map draws the point as a badge on the line.
     */
    val bike: DocItem? = null
) {

    /**
     * How long getting there takes on foot, or null without a fix.
     *
     * The straight line and no climb: a hut over the next spur is further than this says, exactly as the
     * nearest-thing card's own estimate is. See [TravelTime].
     */
    fun onFootSeconds(): Long? = distanceM?.let {
        TravelTime.seconds(it, climbM = 0.0, mode = TravelMode.WALKING)
    }
}
