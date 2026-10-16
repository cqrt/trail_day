package nz.mckenzie.trailday.domain.asset

/**
 * What deleting a place from the desk would take with it, and whether the desk may do it.
 *
 * [sentence] is the phone's own words for what is on the place, shown by the page *before* anything
 * goes - and [allowed] is the same decision as a value, so the page can grey the button out rather
 * than learn a rule by being refused.
 */
data class AssetRemoval(
    /** False once anything is recorded against it: the real delete is on the phone. */
    val allowed: Boolean,
    /** The numbers, in plain English, and what to do about them. */
    val sentence: String
)

/**
 * When a track may be deleted from a computer, and when it may not.
 *
 * **The plan said the desk should archive instead: `active = false`, and the real delete stays on
 * the phone.** Checked against the app rather than taken on faith, that is the one thing this feature
 * must not do. Nothing in the app writes `active = false` - only `createAsset` and the restore of a
 * backup have ever set that column - and nothing in the app shows an inactive place:
 * `AssetDao.observeAllAssets` has no caller outside the backup, the list and the map both read
 * `observeActiveAssets`, and the phone's own delete works on places it can see. So a place put away
 * from the desk disappears from the phone's map and list with no screen that would ever show it
 * again and no way to bring it back *except a backup restore*. That is a quiet removal, and this
 * app's own rule is that anything which deletes says what it will do, in numbers, before it does it -
 * which a screen nobody can open cannot.
 *
 * So the desk deletes only what has nothing recorded against it, which is the mis-drawn track the
 * plan's other half is about, and anything with history attached is refused in numbers, with the
 * phone named as the place to do it.
 *
 * **"Put away" was dropped rather than deferred** (the decision, taken after this shipped). The plan
 * had it as phase 3's *show-archived* and this file said it was waiting for that screen; the walker
 * who owns the data answered the question the other way round - there is no archive, so there is
 * nothing for a screen to show. The column stays as the record's own field, which is what a backup
 * carries and what the state document filters on, and no screen sets it.
 *
 * **The count is read at the moment of the delete, not quoted from the card.** A trip that lands
 * while the sheet is open is exactly the case where a desk's belief about a place is out of date,
 * so what decides is what the phone holds when the button is pressed.
 */
object AssetRemovalRules {

    fun of(name: String, recordings: Int): AssetRemoval {
        return if (recordings <= 0) {
            AssetRemoval(
                allowed = true,
                sentence = "Nothing is recorded against \"$name\", so deleting it here takes " +
                    "nothing else with it."
            )
        } else {
            AssetRemoval(
                allowed = false,
                sentence = "\"$name\" has ${count(recordings, "trip")} on the phone, so it is " +
                    "not deleted from here. Delete it on the phone, where what goes with it can be " +
                    "seen first."
            )
        }
    }

    /** "1 trip", "3 trips" - a number and its noun, which is how a person says it. */
    private fun count(value: Int, noun: String) = "$value $noun" + if (value == 1) "" else "s"
}
