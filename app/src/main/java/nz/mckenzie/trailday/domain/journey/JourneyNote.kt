package nz.mckenzie.trailday.domain.journey

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a journey was made from, kept on the track it became.
 *
 * A journey is an ordinary track - that is what lets it be followed, exported, backed up and deleted
 * with nothing new - so the only thing that marks one is this note: how many of DOC's sections went into
 * it, how much of it is bridged over holes in DOC's line, and how much climbing it has. The last one is
 * kept rather than worked out again because the list shows a walking time for every journey, and reading
 * the heights of every track to answer that would be a query per row.
 *
 * Optional and additive: a track with no note is not a journey, and a note written by a later build with
 * a field this one does not know still reads.
 */
@Serializable
data class JourneyNote(
    val sections: Int = 0,
    val bridgeM: Double = 0.0,
    val climbM: Double = 0.0
) {

    fun toJson(): String = Json.encodeToString(this)

    companion object {

        /** The note a place carries, or null for the great majority that are not journeys. */
        fun of(journey: String?): JourneyNote? =
            journey?.let { text -> runCatching { Json.decodeFromString<JourneyNote>(text) }.getOrNull() }
    }
}
