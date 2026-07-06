package com.toxx.assistant

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Minimal in-memory store for the scaffold. Swap this for a Room database
 * (dependency already included in build.gradle.kts) once you need
 * persistence across app restarts.
 */
object NotificationRepository {

    data class Entry(
        val notification: CapturedNotification,
        var aiResult: AiResult? = null
    )

    private val entries = CopyOnWriteArrayList<Entry>()

    fun store(context: Context, notification: CapturedNotification) {
        entries.add(Entry(notification))
    }

    fun attachAiResult(context: Context, notification: CapturedNotification, result: AiResult) {
        entries.find { it.notification == notification }?.aiResult = result
    }

    fun all(): List<Entry> = entries.toList()

    fun highPriority(threshold: Int = 7): List<Entry> =
        entries.filter { (it.aiResult?.priority ?: 0) >= threshold }
}
