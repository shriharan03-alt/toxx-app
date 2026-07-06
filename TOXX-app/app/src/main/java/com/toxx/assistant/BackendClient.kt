package com.toxx.assistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Talks to your own backend (see /backend in this project), which in turn
 * calls the Claude API. TOXX never calls any AI provider directly from the
 * device — this keeps API keys off the phone.
 */
object BackendClient {

    // TODO: point this at your deployed backend
    private const val BASE_URL = "https://your-toxx-backend.example.com"

    // If true, AI-drafted replies are sent automatically without confirmation.
    // Default is false: draft-and-confirm. Only flip this if you understand
    // the privacy/consent implications for the people you're texting.
    var AUTO_SEND = false

    private val client = OkHttpClient()

    fun processNotification(
        notification: CapturedNotification,
        onResult: (AiResult) -> Unit
    ) {
        val json = JSONObject().apply {
            put("app", notification.appPackage)
            put("title", notification.title)
            put("text", notification.text)
            put("timestamp", notification.postedAt)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/process-notification")
            .post(body)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val respBody = response.body?.string() ?: return@use
                    val parsed = JSONObject(respBody)
                    val result = AiResult(
                        summary = parsed.optString("summary"),
                        suggestedReply = parsed.optString("suggestedReply"),
                        priority = parsed.optInt("priority", 0)
                    )
                    onResult(result)
                }
            } catch (e: IOException) {
                // Network failure — fail silently for now, could retry/queue
            }
        }
    }

    /**
     * Sends a transcribed voice command to the backend, which asks Claude to
     * interpret intent and returns an action ("call", "digest",
     * "read_notifications", "none"), an optional target (e.g. contact name),
     * and a natural-language spokenResponse for TTS playback.
     */
    fun processVoiceCommandFull(
        commandText: String,
        onResult: (action: String, target: String, spokenResponse: String) -> Unit
    ) {
        val json = JSONObject().apply {
            put("command", commandText)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/voice-command")
            .post(body)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.newCall(request).execute().use { response ->
                    val respBody = response.body?.string()
                    if (!response.isSuccessful || respBody == null) {
                        onResult("none", "", "Sorry, I couldn't reach the backend.")
                        return@use
                    }
                    val parsed = JSONObject(respBody)
                    onResult(
                        parsed.optString("action", "none"),
                        parsed.optString("target", ""),
                        parsed.optString("spokenResponse", "Done.")
                    )
                }
            } catch (e: IOException) {
                onResult("none", "", "Sorry, I couldn't reach the backend.")
            }
        }
    }

    fun requestMorningDigest(onResult: (String) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/digest")
            .get()
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@use
                    onResult(JSONObject(body).optString("digest"))
                }
            } catch (e: IOException) {
                // handle failure
            }
        }
    }
}

data class AiResult(
    val summary: String,
    val suggestedReply: String,
    val priority: Int
)
