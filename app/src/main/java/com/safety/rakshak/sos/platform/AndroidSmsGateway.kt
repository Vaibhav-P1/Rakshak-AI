package com.safety.rakshak.sos.platform

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.safety.rakshak.sos.SmsAvailability
import com.safety.rakshak.sos.SmsGateway
import com.safety.rakshak.sos.SmsSendResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * [SmsGateway] backed by SmsManager. Unlike the previous implementation, it
 * waits for the radio's sent-result for every part, so "sent" means the
 * network accepted the message (it is still not a delivery receipt).
 */
class AndroidSmsGateway(
    private val context: Context,
    private val confirmTimeoutMillis: Long = 30_000L,
) : SmsGateway {

    override fun availability(): SmsAvailability {
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return SmsAvailability.NO_TELEPHONY
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return SmsAvailability.NO_PERMISSION
        if (allSimSlotsEmpty()) return SmsAvailability.NO_SIM
        return SmsAvailability.READY
    }

    /**
     * Reports NO_SIM only when every slot is definitely ABSENT. Transient states
     * (UNKNOWN, NOT_READY) still attempt to send: a failed attempt is reported,
     * while wrongly skipping the SOS would be silent.
     */
    private fun allSimSlotsEmpty(): Boolean {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return false
        val slots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tm.activeModemCount
        } else {
            @Suppress("DEPRECATION")
            tm.phoneCount
        }
        if (slots <= 0) return false
        return (0 until slots).all { tm.getSimState(it) == TelephonyManager.SIM_STATE_ABSENT }
    }

    override suspend fun send(phoneNumber: String, text: String): SmsSendResult {
        val manager = smsManager() ?: return SmsSendResult.Failed("SmsManager unavailable")

        // A unique action per message keeps concurrent sends' results separate.
        val action = "${context.packageName}.SMS_SENT.${UUID.randomUUID()}"
        val result = CompletableDeferred<SmsSendResult>()
        val parts = try {
            manager.divideMessage(text)
        } catch (e: Exception) {
            return SmsSendResult.Failed(e.javaClass.simpleName)
        }

        var remaining = parts.size
        var failure: String? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (resultCode != Activity.RESULT_OK && failure == null) failure = describe(resultCode)
                remaining--
                if (remaining <= 0) {
                    result.complete(failure?.let { SmsSendResult.Failed(it) } ?: SmsSendResult.Sent)
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED
        )

        return try {
            val sentIntents = ArrayList(parts.indices.map { index ->
                PendingIntent.getBroadcast(
                    context,
                    index,
                    Intent(action).setPackage(context.packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                )
            })
            if (parts.size == 1) {
                manager.sendTextMessage(phoneNumber, null, parts[0], sentIntents[0], null)
            } else {
                manager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
            }
            withTimeoutOrNull(confirmTimeoutMillis) { result.await() } ?: SmsSendResult.Unconfirmed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Seen on an Android 14 emulator in airplane mode: SecurityException even with
            // SEND_SMS granted. Report the exception type rather than guessing the cause.
            Log.w(TAG, "SMS send threw ${e.javaClass.simpleName}: ${e.message}")
            SmsSendResult.Failed(e.javaClass.simpleName)
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // Already unregistered.
            }
        }
    }

    private fun smsManager(): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    private companion object {
        const val TAG = "AndroidSmsGateway"
    }

    private fun describe(resultCode: Int): String = when (resultCode) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> "no service"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "radio off"
        SmsManager.RESULT_ERROR_NULL_PDU -> "null PDU"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "SMS limit exceeded"
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "short code not allowed"
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "generic failure"
        else -> "error $resultCode"
    }
}
