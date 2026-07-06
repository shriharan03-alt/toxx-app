package com.toxx.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service so Android doesn't kill TOXX's background work.
 * Shows a persistent low-priority notification (required by Android for
 * any foreground service).
 */
class TOXXForegroundService : Service() {

    private val channelId = "toxx_core_service"

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TOXX is active")
            .setContentText("Monitoring notifications and standing by.")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "TOXX Core Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
