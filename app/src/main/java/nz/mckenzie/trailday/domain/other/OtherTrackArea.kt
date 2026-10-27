package nz.mckenzie.trailday.domain.other

/**
 * One **named download** of somebody else's tracks: what the walker called the box, and what came back.
 *
 * The tracks are held once and shared, so this is not where they live - it is the walker's own record of
 * what they asked for. Three downloads of three hillsides are three of these, and the offline screen lists
 * them, because "412 tracks" alone cannot tell a walker whether the Tararuas are on the phone or a box
 * they drew by accident.
 *
 * [downloadedAtEpochMs] is when it was read from the service, which is what the dataset's licence asks to
 * be stated as the moment the material was acquired.
 */
data class OtherTrackArea(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val downloadedAtEpochMs: Long
)
