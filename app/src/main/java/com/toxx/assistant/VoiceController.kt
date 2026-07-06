package com.toxx.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Push-to-talk voice layer: listens for a single spoken command via
 * SpeechRecognizer, and speaks TOXX's response back via TextToSpeech.
 *
 * This is NOT always-on / wake-word listening — the mic only activates when
 * you call `startListening()`, e.g. from a button press. That keeps battery
 * use sane and avoids the Play Store scrutiny that comes with background
 * always-on microphone access.
 */
class VoiceController(private val context: Context) {

    interface Callback {
        fun onSpeechResult(text: String)
        fun onSpeechError(message: String)
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ttsReady = true
            }
        }
    }

    fun startListening(callback: Callback) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onSpeechError("Speech recognition not available on this device.")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        callback.onSpeechResult(text)
                    } else {
                        callback.onSpeechError("Didn't catch that — try again.")
                    }
                }

                override fun onError(error: Int) {
                    callback.onSpeechError("Speech error code: $error")
                }

                // Required overrides, not needed for this simple flow
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "toxx_utterance")
        }
    }

    fun release() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
