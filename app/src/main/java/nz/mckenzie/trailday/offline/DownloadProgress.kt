package nz.mckenzie.trailday.offline

import kotlin.math.roundToInt
import nz.mckenzie.trailday.ui.formatEstimate

/**
 * What a download is for, in the walker's words - **the same words the offline screen's cards carry.**
 *
 * Named once because they are read in three places (a card's title, the live sentence, the notification),
 * and a pack called "LINZ imagery" on one and "Imagery" on another is how a walker comes to think there
 * are two of them.
 */
object DownloadPacks {
    const val IMAGERY = "LINZ imagery"
    const val TOPOGRAPHY = "Topographic map"
    const val DOC = "DOC assets"
}

/**
 * How a download is going, as **one shape for every pack.**
 *
 * Imagery, the drawn map and DOC's material are three different things fetched three different ways, and a
 * walker watching one of them wants the same four answers from all three: how much is done, how big it is,
 * how fast it is coming, and how long is left. One shape, worked out once, so that the picker's card, the
 * offline screen and the notification cannot describe the same download three different ways.
 *
 * **The rate and the time left are derived from elapsed time rather than measured.** A download that has
 * been going for two seconds has not earned a speed yet, and one that has fetched nothing has no rate at
 * all - so both answer null until there is something honest to say, and the sentence simply leaves them out.
 */
data class DownloadProgress(
    /** Which pack, from [DownloadPacks]. */
    val pack: String,
    /** What is counted, as it reads in the sentence: "tiles", "records". */
    val unit: String,
    val done: Int,
    val total: Int,
    val bytes: Long,
    /** How long this has been going, so a rate can be worked out. */
    val elapsedMs: Long,
    /**
     * The part being fetched, when a pack has more than one: "the map", "the heights".
     *
     * Null for a pack that is one act, which is every pack but the drawn map - its tiles and the heights
     * its shade is drawn from are two downloads, and saying which one is running is the difference
     * between a bar that appears to go backwards and one that does not.
     */
    val phase: String? = null,
    /** Units the service has nothing for: not failures, and not counted as done. */
    val unavailable: Int = 0,
    /** Units that failed after retries. */
    val failed: Int = 0
) {

    /** 0-100, and 100 only once there is nothing left to wait for. */
    val percent: Int
        get() = when {
            total <= 0 -> 0
            done >= total -> 100
            else -> ((done * 100L) / total).toInt().coerceIn(0, 99)
        }

    /**
     * Units a second, or null before there is enough time behind the figure to mean anything.
     *
     * Two seconds is the threshold: the first chunk of four tiles lands in a few hundred milliseconds and
     * would read as an absurd rate, and a walker who is shown "400 a second" once does not believe the
     * honest number that follows it.
     */
    val perSecond: Double?
        get() = if (elapsedMs < RATE_AFTER_MS || done <= 0) null else done / (elapsedMs / 1000.0)

    /** What is left, in seconds, or null while the rate is unknown. */
    val remainingSeconds: Long?
        get() {
            val rate = perSecond ?: return null
            if (rate <= 0.0) return null
            return ((total - done).coerceAtLeast(0) / rate).toLong()
        }

    private companion object {
        const val RATE_AFTER_MS = 2_000L
    }
}

/**
 * The sentence a download in flight is described by, **wherever it is read.**
 *
 * "the heights: 212 of 1,203 tiles - 18.4 MB - 22 tiles a second - about 1 min left", and the same words
 * on the picker's card, on the offline screen and in the notification: three surfaces describing one
 * download three ways is three chances to be wrong about it.
 *
 * The parts that are not known yet are left out rather than shown as zero: "0 a second" and "0s left" are
 * claims, and the first two seconds of a download have no rate to claim.
 */
fun downloadProgressWords(progress: DownloadProgress): String {
    val parts = mutableListOf<String>()
    val what = progress.phase?.let { "$it: " }.orEmpty()
    // A pack whose total nobody knows - DOC's material before its inventory has been read - says how many
    // it has rather than "412 of 0".
    parts += if (progress.total > 0) {
        "$what${progress.done} of ${progress.total} ${progress.unit}"
    } else {
        "$what${progress.done} ${progress.unit}"
    }
    if (progress.bytes > 0L) parts += formatBytes(progress.bytes)
    progress.perSecond?.let { parts += "${it.roundToInt()} ${progress.unit} a second" }
    progress.remainingSeconds?.let { parts += "about ${remainingWords(it)} left" }
    return parts.joinToString(" \u00b7 ")
}

/**
 * What is left, in the walker's words.
 *
 * Seconds under a minute, then the app's own minutes and hours: [formatEstimate] counts in minutes because
 * an estimate about a hill should not claim seconds it does not have - but a download's time left is
 * measured from a rate it is actually achieving, and "about 1 min left" for seven seconds is not rough,
 * it is wrong.
 */
private fun remainingWords(seconds: Long): String =
    if (seconds < 60L) "${seconds.coerceAtLeast(1)}s" else formatEstimate(seconds)
