package com.toxx.assistant

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class WakeWordService : Service() {

    companion object {
        private const val TAG = "TOXX-WakeWord"
    }

    private val channelId = "toxx_wake_word_service"
    private var recognizer: SpeechRecognizer? = null
    private var voiceController: VoiceController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var consecutiveErrors = 0

    private val wakePhrase = "wake up toxx"

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        voiceController = VoiceController(applicationContext)
        startForeground(2, buildNotification("Say \"wake up toxx\" to activate"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasMicPermission()) {
            Log.e(TAG, "RECORD_AUDIO permission not granted — cannot start listening.")
            updateNotification("Mic permission missing — tap to open TOXX and grant it")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
            Log.e(TAG, "SpeechRecognizer.isRecognitionAvailable() returned false.")
            updateNotification("No speech recognition service available on this device")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isRunning) {
            isRunning = true
            Log.i(TAG, "Wake word listening started.")
            listenCycle()
        }
        return START_STICKY
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun listenCycle() {
        if (!isRunning) return

        if (!hasMicPermission()) {
            Log.e(TAG, "RECORD_AUDIO permission revoked while running. Stopping.")
            updateNotification("Mic permission was revoked — tap to open TOXX and re-grant it")
            isRunning = false
            stopSelf()
            return
        }

        ensureRecognizerCreated()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        try {
            recognizer?.cancel()
            recognizer?.startListening(intent)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting recognizer.", e)
            updateNotification("Mic permission missing — tap to open TOXX and grant it")
            isRunning = false
            stopSelf()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Recognizer in bad state, recreating.", e)
            recognizer?.destroy()
            recognizer = null
            restartAfterDelay()
        }
    }

    private fun ensureRecognizerCreated() {
        if (recognizer != null) return

        recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    consecutiveErrors = 0
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val heard = matches?.firstOrNull()?.lowercase(Locale.getDefault()).orEmpty()
                    Log.d(TAG, "Heard: \"$heard\"")

                    if (heard.contains(wakePhrase)) {
                        onWakeWordDetected()
                    } else {
                        restartAfterDelay()
                    }
                }

                override fun onError(error: Int) {
                    logRecognizerError(error)

                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        updateNotification("Mic permission missing — tap to open TOXX and grant it")
                        isRunning = false
                        stopSelf()
                        return
                    }

                    consecutiveErrors++
                    if (consecutiveErrors >= 10) {
                        Log.w(TAG, "10 consecutive recognizer errors — backing off for 30s.")
                        updateNotification("Having trouble listening — retrying periodically")
                        consecutiveErrors = 0
                        handler.postDelayed({ listenCycle() }, 30_000)
                        return
                    }

                    restartAfterDelay()
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech.")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun logRecognizerError(error: Int) {
        val name = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
            else -> "UNKNOWN_ERROR_$error"
        }
        Log.w(TAG, "Recognizer error: $name")
    }

    private fun restartAfterDelay() {
        handler.postDelayed({ listenCycle() }, 400)
    }

    private fun onWakeWordDetected() {
        Log.i(TAG, "Wake word detected.")
        voiceController?.speak("Yes?")
        voiceController?.startListening(object : VoiceController.Callback {
            override fun onSpeechResult(text: String) {
                Log.d(TAG, "Command heard: \"$text\"")
                val ringing = TOXXInCallService.isRinging()
                val waiting = TOXXInCallService.isCallWaiting()
                val heldCall = TOXXInCallService.hasHeldCall()
                BackendClient.processVoiceCommandFull(text, ringing, waiting, heldCall) { result ->
                    handleVoiceCommandResult(result)
                }
            }

            override fun onSpeechError(message: String) {
                Log.w(TAG, "Command listening error: $message")
                voiceController?.speak("Sorry, I didn't catch that.")
                restartAfterDelay()
            }
        })
    }

    private fun handleVoiceCommandResult(result: VoiceCommandResult) {
        when (result.action) {
            "call" -> {
                if (result.target.isNotBlank()) {
                    val match = ContactResolver.resolve(applicationContext, result.target)
                    when {
                        match != null -> {
                            voiceController?.speak("Calling " + match.name + ".")
                            CallManager.placeCall(applicationContext, match.phoneNumber)
                        }
                        result.target.any { it.isDigit() } -> {
                            voiceController?.speak(result.spokenResponse)
                            CallManager.placeCall(applicationContext, result.target)
                        }
                        else -> voiceController?.speak("I couldn't find " + result.target + " in your contacts.")
                    }
                }
                restartAfterDelay()
            }

            "answer_call" -> {
                if (!TOXXInCallService.isRinging()) {
                    voiceController?.speak("There's no call ringing right now.")
                    restartAfterDelay()
                    return
                }

                TOXXInCallService.answerRingingCall()

                if (result.messageToCaller.isNotBlank()) {
                    handler.postDelayed({
                        val player = CallAudioPlayer(applicationContext)
                        player.speakIntoCall(result.messageToCaller) {
                            player.release()
                            restartAfterDelay()
                        }
                    }, 1200)
                } else {
                    voiceController?.speak(result.spokenResponse)
                    restartAfterDelay()
                }
            }

            "reject_call" -> {
                if (!TOXXInCallService.isRinging()) {
                    voiceController?.speak("There's no call ringing right now.")
                } else {
                    TOXXInCallService.rejectRingingCall()
                    voiceController?.speak(result.spokenResponse)
                }
                restartAfterDelay()
            }

            "swap_calls" -> {
                if (!TOXXInCallService.hasHeldCall()) {
                    voiceController?.speak("There's no other call to switch to.")
                } else {
                    TOXXInCallService.swapCalls()
                    voiceController?.speak(result.spokenResponse)
                }
                restartAfterDelay()
            }

            "end_call" -> {
                if (TOXXInCallService.activeCallOrNull() == null) {
                    voiceController?.speak("There's no active call to end.")
                } else {
                    TOXXInCallService.endActiveCall()
                    voiceController?.speak(result.spokenResponse)
                }
                restartAfterDelay()
            }

            else -> {
                voiceController?.speak(result.spokenResponse)
                restartAfterDelay()
            }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "WakeWordService destroyed.")
        isRunning = false
        recognizer?.destroy()
        voiceController?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, channelId)
            .setContentTitle("TOXX")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(2, buildNotification(text))
    }

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
