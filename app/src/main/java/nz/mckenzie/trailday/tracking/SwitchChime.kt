package nz.mckenzie.trailday.tracking

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The noise the recorder makes when it takes up another track by itself.
 *
 * **Two short notes, the second higher, and a double buzz.** The recorder changes the line it is measuring
 * without being asked, so the walker has to be told - and telling them on the screen only works for a
 * walker looking at the screen, which is not what they are doing. The phone is in a pocket.
 *
 * **Synthesised rather than a sound file.** [ToneGenerator] needs nothing shipped, nothing fetched and no
 * permission, and it plays on the notification stream - so the phone's own volume, its silent mode and
 * its Do Not Disturb do what the walker set them to, and the app has no opinion about whether a walk
 * should be audible.
 *
 * **A voice saying the track's name was the other way to do this**, and it is better information: you
 * would know *which* track without getting the phone out. It is a bigger thing than a chime - a speech
 * engine, a voice for the right language, and a wait before it speaks - and it was left for the day
 * somebody asks for it.
 *
 * The buzz is not conditional on anything the app can read: Android decides whether a phone may vibrate,
 * and a phone on silent with vibration off stays quiet without being asked.
 */
object SwitchChime {

    /** How loud, from nothing to all of it. The stream's own volume is applied on top of this. */
    private const val VOLUME = 90

    /** How long one note lasts, in milliseconds. Short: it is a signal, not a tune. */
    private const val NOTE_MS = 140

    /** How long between the two notes, in milliseconds. Far enough apart to be two notes. */
    private const val GAP_MS = 170L

    /** When the generator is let go: after the second note has finished sounding. */
    private const val RELEASE_MS = 460L

    /** What the buzz is: twice, shortly. The same shape as the pair of notes, in the hand. */
    private val BUZZ = longArrayOf(0, 60, 90, 60)

    /**
     * Two notes and a buzz.
     *
     * Called on the main thread, from the recorder, at the moment a switch happens. The generator is made
     * and released around the two notes rather than held: a `ToneGenerator` held for the length of a walk
     * is an audio stream open in a pocket for hours for two hundred milliseconds of sound.
     */
    fun play(context: Context) {
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME) }.getOrNull()
        if (tone != null) playNotes(tone)
        buzz(context)
    }

    private fun playNotes(tone: ToneGenerator) {
        val handler = Handler(Looper.getMainLooper())
        runCatching {
            // The first note, then a step higher: two notes rather than the same one twice, so a switch
            // cannot be mistaken for a message arriving.
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, NOTE_MS)
            handler.postDelayed(
                { runCatching { tone.startTone(ToneGenerator.TONE_PROP_BEEP2, NOTE_MS) } },
                GAP_MS
            )
        }
        handler.postDelayed({ runCatching { tone.release() } }, RELEASE_MS)
    }

    private fun buzz(context: Context) {
        val vibrator = runCatching { vibratorOf(context) }.getOrNull() ?: return
        if (!vibrator.hasVibrator()) return
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(BUZZ, -1)) }
    }

    private fun vibratorOf(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
