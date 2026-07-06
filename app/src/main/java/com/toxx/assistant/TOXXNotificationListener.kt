package com.toxx.assistant

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Captures every notification posted on the device (once the user has granted
 * Notification Access in system settings). Forwards relevant ones to the
 * backend for AI summarization / reply drafting.
 */
class TOXXNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val appPackage = sbn.packageName

        // Skip TOXX's own notifications to avoid feedback loops
        if (appPackage == packageName) return

        val captured = CapturedNotification(
            appPackage = appPackage,
            title = title,
            text = text,
            postedAt = sbn.postTime
        )

        scope.launch {
            NotificationRepository.store(applicationContext, captured)
            BackendClient.processNotification(captured) { result ->
                // result may include: priority score, summary, suggested reply
                NotificationRepository.attachAiResult(applicationContext, captured, result)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Optional: mark as read/dismissed in local store
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Good place to trigger an initial sync/backlog summary if desired
    }
}

data class CapturedNotification(
    val appPackage: String,
    val title: String,
    val text: String,
    val postedAt: Long
)
