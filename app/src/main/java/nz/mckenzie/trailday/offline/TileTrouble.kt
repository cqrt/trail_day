package nz.mckenzie.trailday.offline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Whether the map's tiles have stopped arriving.
 *
 * This is the one failure the proxy arrangement can produce that a walker cannot diagnose from the
 * phone. With the key on a Worker, **a key that has expired, a key LINZ is rate-limiting, and a Worker
 * that is simply down all look identical** from here - a map that draws nothing new - and the app used
 * to say nothing at all about any of them.
 *
 * So the app counts tiles that failed **in a row** and says something when there have been enough of
 * them, which is [showing]. A single failed tile is not news: a walker panning over the sea gets `404`s
 * by the dozen (those are [arrived] - the source answered, it just has no imagery there), and a phone
 * on a bad connection drops the odd one. A *run* of them is news.
 *
 * Deliberately not a count of every failure since launch: this is a statement about now, and a number
 * that only ever grew would leave the notice on screen for the rest of the session.
 */
object TileTrouble {

    /**
     * How many tiles in a row have to fail before the map says so.
     *
     * A screenful is 9-20 tiles, so a source that is genuinely not answering fails far more than this
     * in a single frame. A source that is merely slow rarely reaches it, because one tile arriving
     * puts the count back to zero.
     */
    const val RUN = 5

    private val failures = MutableStateFlow(0)
    private val answered = MutableStateFlow(false)

    /** How many tiles in a row have failed. Back to zero the moment one arrives. */
    val consecutiveFailures: StateFlow<Int> = failures.asStateFlow()

    /**
     * Whether a run of failures should be said out loud.
     *
     * False once the walker has answered the notice, until tiles start arriving again: a card that
     * came straight back after being dismissed would be the app arguing with the person reading it.
     */
    val showing: Flow<Boolean> = combine(failures, answered) { count, said ->
        isTrouble(count, said)
    }

    /**
     * A tile arrived, or the source said it has none here.
     *
     * Both are the source *answering*, which is the thing in doubt. The walker's answer is cleared with
     * it, so a source that comes back and then goes again is said again rather than swallowed.
     */
    fun arrived() {
        failures.value = 0
        answered.value = false
    }

    /** A tile did not arrive, and the source said so rather than "I have none here". */
    fun failed() {
        failures.value += 1
    }

    /** The walker has read the notice and answered it. */
    fun answered() {
        answered.value = true
    }

    /**
     * The basemap being asked has changed, so nothing counted about the last one means anything.
     *
     * A walker who answers the notice by switching to a drawn map has moved to a source that never
     * touches LINZ, so without this the count would sit where it was and the notice would follow them
     * onto a map that is working perfectly.
     */
    fun reset() {
        arrived()
    }
}

/**
 * Whether a run of failures is worth saying out loud.
 *
 * Pure, and separated out so the rule is held by a unit test rather than by reading the counter: it is
 * the whole of the judgement in this file.
 */
internal fun isTrouble(consecutiveFailures: Int, answered: Boolean): Boolean =
    consecutiveFailures >= TileTrouble.RUN && !answered
