package com.safety.rakshak.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.safety.rakshak.data.EmergencyContact

class SMSHelper(private val context: Context) {

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
            val smsManager = context.getSystemService(SmsManager::class.java)
            
            contacts.forEach { contact ->
                try {
                    val parts = smsManager.divideMessage(message)
                    smsManager.sendMultipartTextMessage(
                        contact.phoneNumber,
                        null,
                        parts,
                        null,
                        null
                    )
                } catch (e: Exception) {
                    onError("Failed to send SMS to ${contact.name}: ${e.message}")
                }
            }
            
            onSuccess()
        } catch (e: Exception) {
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
