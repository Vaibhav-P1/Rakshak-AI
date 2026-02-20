package com.safety.rakshak.utils

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.safety.rakshak.data.EmergencyContact

class SMSHelper(private val context: Context) {

    companion object {
        private const val TAG = "SMSHelper"
        private const val SMS_SENT_ACTION = "com.safety.rakshak.SMS_SENT"
    }

    fun sendSOSMessage(
        contacts: List<EmergencyContact>,
        latitude: Double?,
        longitude: Double?,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!hasSMSPermission()) {
            onError("SMS permission not granted")
            return
        }

        if (contacts.isEmpty()) {
            onError("No emergency contacts found")
            return
        }

        val locationUrl = if (latitude != null && longitude != null) {
            "https://maps.google.com/?q=$latitude,$longitude"
        } else {
            "Location unavailable"
        }

        val message = """
            🚨 EMERGENCY ALERT FROM RAKSHAK 🚨
            
            I need immediate help!
            
            My current location:
            $locationUrl
            
            Please contact me immediately or call emergency services.
            
            - Sent via Rakshak Safety App
        """.trimIndent()

        try {
            // Fix: Use getDefault() instead of getSystemService() for reliability
            @Suppress("DEPRECATION")
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    ?: SmsManager.getDefault()
            } else {
                SmsManager.getDefault()
            }

            var failedCount = 0
            val totalContacts = contacts.size

            contacts.forEach { contact ->
                try {
                    val phoneNumber = contact.phoneNumber.trim()

                    if (phoneNumber.isBlank()) {
                        Log.w(TAG, "Skipping contact ${contact.name} — empty phone number")
                        failedCount++
                        return@forEach
                    }

                    val parts = smsManager.divideMessage(message)

                    // Create sent PendingIntent for tracking
                    val sentIntent = PendingIntent.getBroadcast(
                        context,
                        contact.phoneNumber.hashCode(),
                        Intent(SMS_SENT_ACTION),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    val sentIntents = ArrayList<PendingIntent>().apply {
                        repeat(parts.size) { add(sentIntent) }
                    }

                    smsManager.sendMultipartTextMessage(
                        phoneNumber,
                        null,
                        parts,
                        sentIntents,
                        null
                    )

                    Log.d(TAG, "SMS dispatched to ${contact.name} ($phoneNumber)")

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send SMS to ${contact.name}: ${e.message}")
                    failedCount++
                }
            }

            // Report result based on how many succeeded
            if (failedCount == totalContacts) {
                onError("Failed to send SMS to all contacts")
            } else if (failedCount > 0) {
                onSuccess() // Partial success — at least some went through
            } else {
                onSuccess()
            }

        } catch (e: Exception) {
            Log.e(TAG, "SMSHelper crashed: ${e.message}")
            onError("Failed to send emergency messages: ${e.message}")
        }
    }

    private fun hasSMSPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
}