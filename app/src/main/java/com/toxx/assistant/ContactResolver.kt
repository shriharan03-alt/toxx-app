package com.toxx.assistant

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Resolves a spoken contact name (e.g. "mom", "John Smith") to a phone
 * number using the device's contacts. Requires READ_CONTACTS permission,
 * granted at runtime.
 */
object ContactResolver {

    data class Match(val name: String, val phoneNumber: String)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Looks up the best-matching contact for [spokenName]. Tries an exact
     * (case-insensitive) match first, then falls back to a partial/contains
     * match (handles nicknames like "mom" if that's literally the saved
     * contact name, or partial names like "john" matching "John Smith").
     * Returns null if no match or permission isn't granted.
     */
    fun resolve(context: Context, spokenName: String): Match? {
        if (!hasPermission(context)) return null
        val query = spokenName.trim().lowercase()
        if (query.isBlank()) return null

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val candidates = mutableListOf<Match>()

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                val number = cursor.getString(numberIdx) ?: continue
                candidates.add(Match(name, number))
            }
        }

        // Exact match first (case-insensitive)
        candidates.firstOrNull { it.name.lowercase() == query }?.let { return it }

        // Fallback: partial match either direction ("john" matches "John Smith",
        // "john smith" matches a contact literally named "John")
        candidates.firstOrNull {
            it.name.lowercase().contains(query) || query.contains(it.name.lowercase())
        }?.let { return it }

        return null
    }
}
