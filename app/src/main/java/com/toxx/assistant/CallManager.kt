package com.toxx.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

/**
 * Places outgoing calls. Requires CALL_PHONE permission granted at runtime.
 *
 * Note: auto-answering or rejecting inbound calls, or screening them with an
 * AI greeting before connecting, requires TOXX to be set as the device's
 * default Phone app (via RoleManager / TelecomManager on Android 10+). That
 * is a larger integration than this scaffold covers — see README.
 */
object CallManager {

    fun canMakeCalls(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    fun placeCall(context: Context, phoneNumber: String) {
        if (!canMakeCalls(context)) return
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        context.startActivity(intent)
    }

    fun isDefaultDialer(context: Context): Boolean {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return telecomManager.defaultDialerPackage == context.packageName
    }
}
