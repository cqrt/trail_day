package nz.mckenzie.trailday.domain.doc

/**
 * One **named download** of DOC's material: what the walker called the ground, and how much came back.
 *
 * The same shape as somebody else's tracks' named areas, because it is the same idea: the cache answers
 * "what is on the phone", and this answers "where did it come from". A walker who downloaded DOC's huts for
 * Otago and for the Tararuas wants to see both, and a count of records cannot tell them apart.
 *
 * [downloadedAtEpochMs] is when the material was read, which is what DOC's licence asks to be stated.
 */
data class DocArea(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val downloadedAtEpochMs: Long
)
