package app.danebrain.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Hands-free dictation on top of the phone's speech recognizer. The recognizer ends a session at
 * every pause, so this restarts it until [stop] is called or nothing has been said for
 * [MAX_SILENT_ROUNDS] sessions in a row. All calls must be on the main thread.
 *
 * [onPartial] gets the words of the phrase in progress; [onFinal] gets each finished phrase once.
 */
class Dictation(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onProblem: (String) -> Unit,
) {
    var listening by mutableStateOf(false)
        private set

    /** Microphone loudness from 0 to 1, for the listening indicator. */
    var level by mutableFloatStateOf(0f)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var onDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    private var lastPartial = ""
    private var silentRounds = 0

    fun start() {
        if (listening) return
        if (!onDevice && !SpeechRecognizer.isRecognitionAvailable(context)) {
            onProblem("This phone has no speech recognition service")
            return
        }
        listening = true
        silentRounds = 0
        listen()
    }

    /** Stops listening. The phrase in progress still arrives through [onFinal]. */
    fun stop() {
        if (!listening) return
        finish()
        recognizer?.stopListening()
    }

    /** Stops and frees the recognizer. Words heard so far are kept. */
    fun release() {
        finish()
        handler.removeCallbacksAndMessages(null)
        commitPartial()
        recognizer?.destroy()
        recognizer = null
    }

    private fun finish() {
        listening = false
        level = 0f
    }

    private fun commitPartial() {
        val text = lastPartial
        lastPartial = ""
        if (text.isNotBlank()) onFinal(text)
    }

    private fun listen() {
        lastPartial = ""
        level = 0f
        val r = recognizer ?: create().also { recognizer = it }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        if (Build.VERSION.SDK_INT >= 33) {
            // Asks for punctuation and capitals where the recognizer supports it.
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
        }
        r.startListening(intent)
    }

    private fun create(): SpeechRecognizer {
        val r = if (onDevice && Build.VERSION.SDK_INT >= 31) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        r.setRecognitionListener(listener)
        return r
    }

    /** Starts the next session after a short gap; restarting inside a callback can report "busy". */
    private fun listenAgain() {
        handler.postDelayed({ if (listening) listen() }, RESTART_DELAY_MS)
    }

    private fun first(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            val text = first(partialResults)
            if (text.isEmpty()) return
            lastPartial = text
            onPartial(text)
        }

        override fun onResults(results: Bundle?) {
            val text = first(results)
            if (text.isNotEmpty()) {
                lastPartial = ""
                silentRounds = 0
                onFinal(text)
            } else {
                commitPartial()
                silentRounds++
            }
            continueOrFinish()
        }

        override fun onError(error: Int) {
            val heard = lastPartial.isNotBlank()
            commitPartial()
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (heard) silentRounds = 0 else silentRounds++
                    continueOrFinish()
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    if (onDevice && listening) {
                        // The offline language pack is missing; use the phone's default recognizer instead.
                        onDevice = false
                        recognizer?.destroy()
                        recognizer = null
                        listenAgain()
                    } else {
                        fail("Dictation is not available for this language")
                    }
                }
                // Reported after a manual stop on some phones; nothing went wrong.
                SpeechRecognizer.ERROR_CLIENT -> finish()
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Microphone permission is needed for dictation")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> fail("Dictation needs a connection right now")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> fail("The speech recognizer is busy; try again")
                else -> fail("Dictation stopped (error $error)")
            }
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (listening) level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun continueOrFinish() {
        if (!listening) return
        if (silentRounds >= MAX_SILENT_ROUNDS) finish() else listenAgain()
    }

    private fun fail(message: String) {
        val wasListening = listening
        finish()
        if (wasListening) onProblem(message)
    }

    companion object {
        private const val MAX_SILENT_ROUNDS = 3
        private const val RESTART_DELAY_MS = 150L

        /** Appends dictated [text] to [base] with one space between, keeping a trailing line break. */
        fun join(base: String, text: String): String = when {
            base.isBlank() -> text
            base.endsWith("\n") -> base + text
            else -> base.trimEnd() + " " + text
        }
    }
}
