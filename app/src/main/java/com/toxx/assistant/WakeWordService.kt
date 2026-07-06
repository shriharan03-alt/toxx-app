package com.toxx.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * Runs continuously in the foreground, restarting SpeechRecognizer in a loop
 * to approximate always-on wake-word detection for the phrase "wake up toxx".
 *
 * IMPORTANT — read before relying on this:
 * - This is a restart-loop approximation, not a true low-power wake-word
 *   engine. It uses more battery than a dedicated engine like Porcupine and
 *   can occasionally miss the phrase or need a brief pause between listening
 *   cycles (Android's SpeechRecognizer isn't built for tight back-to-back
 *   restarts). For a production-grade always-listening experience, swap this
 *   for Porcupine (or similar) — same wake phrase, hook it into
 *   `onWakeWordDetected()` below.
 * - Requires RECORD_AUDIO permission granted at runtime before starting.
 * - Must run as a foreground service with type "microphone" (already
 *   declared in the manifest) or Android will kill it quickly.
 */
class WakeWordService : Service() {

    private val channelId = "toxx_wake_word_service"
    private var recognizer: SpeechRecognizer? = null
    private var voiceController: VoiceController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private val wakePhrase = "wake up toxx"

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        voiceController = VoiceController(applicationContext)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TOXX is listening")
            .setContentText("Say \"wake up toxx\" to activate")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        startForeground(2, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            listenCycle()
        }
        return START_STICKY
    }

    private fun listenCycle() {
        if (!isRunning) return
        if (!SpeechRecognizer.isRecognitionAvailable(applicationContext)) return

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val heard = matches?.firstOrNull()?.lowercase(Locale.getDefault()).orEmpty()

                    if (heard.contains(wakePhrase)) {
                        onWakeWordDetected()
                    } else {
                        restartAfterDelay()
                    }
                }

                override fun onError(error: Int) {
                    // Common during rapid restarts (e.g. ERROR_NO_MATCH, ERROR_SPEECH_TIMEOUT)
                    restartAfterDelay()
                }

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
        recognizer?.startListening(intent)
    }

    private fun restartAfterDelay() {
        // Small delay avoids hammering SpeechRecognizer, which tends to
        // throw errors if restarted with zero gap.
        handler.postDelayed({ listenCycle() }, 400)
    }

    /**
     * Called once "wake up toxx" is heard. Speaks an acknowledgment, then
     * hands off to a single command-listening pass via VoiceController.
     */
    private fun onWakeWordDetected() {
        voiceController?.speak("Yes?")
        voiceController?.startListening(object : VoiceController.Callback {
            override fun onSpeechResult(text: String) {
                BackendClient.processVoiceCommandFull(text) { action, target, spokenResponse ->
                    if (action == "call" && target.isNotBlank()) {
                        val match = ContactResolver.resolve(applicationContext, target)
                        if (match != null) {
                            voiceController?.speak("Calling ${match.name}.")
                            CallManager.placeCall(applicationContext, match.phoneNumber)
                        } else if (target.any { it.isDigit() }) {
                            // Looks like a number was spoken directly rather than a name
                            voiceController?.speak(spokenResponse)
                            CallManager.placeCall(applicationContext, target)
                        } else {
                            voiceController?.speak("I couldn't find $target in your contacts.")
                        }
                    } else {
                        voiceController?.speak(spokenResponse)
                    }
                    restartAfterDelay()
                }
            }

            override fun onSpeechError(message: String) {
                voiceController?.speak("Sorry, I didn't catch that.")
                restartAfterDelay()
            }
        })
    }

    override fun onDestroy() {
        isRunning = false
        recognizer?.destroy()
        voiceController?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "TOXX Wake Word Listener",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
