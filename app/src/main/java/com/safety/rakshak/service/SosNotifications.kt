package com.safety.rakshak.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safety.rakshak.MainActivity
import com.safety.rakshak.R
import com.safety.rakshak.sos.LocationReport
import com.safety.rakshak.sos.SafeOutcome
import com.safety.rakshak.sos.SmsAvailability
import com.safety.rakshak.sos.SosOutcome
import com.safety.rakshak.sos.SosProgress

/**
 * Notifications for the SOS flow.
 *  - The progress notification belongs to the foreground service and goes away with it.
 *  - The result notification is separate and stays until the user dismisses it,
 *    so a failure is never hidden.
 */
class SosNotifications(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "SOS Alerts", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Emergency SOS progress and results" }
        manager.createNotificationChannel(channel)
        // Heads-up and a short vibration so the countdown is noticed, but no sound.
        val countdownChannel = NotificationChannel(
            COUNTDOWN_CHANNEL_ID, "SOS countdown", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "The few seconds before an SOS is sent, with a Cancel button"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 120, 250)
            setSound(null, null)
        }
        manager.createNotificationChannel(countdownChannel)
    }

    /**
     * The countdown shown for every trigger. Public visibility so Cancel and Send now
     * are usable from the lock screen.
     */
    fun countdown(secondsLeft: Int): Notification =
        NotificationCompat.Builder(context, COUNTDOWN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.countdown_title, secondsLeft))
            .setContentText(context.getString(R.string.countdown_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppAction())
            .addAction(0, context.getString(R.string.countdown_cancel), serviceAction(SOSService.ACTION_CANCEL_COUNTDOWN, REQUEST_CANCEL))
            .addAction(0, context.getString(R.string.countdown_send_now), serviceAction(SOSService.ACTION_SEND_NOW, REQUEST_SEND_NOW))
            .build()

    fun progress(text: String): Notification =
        base("SOS in progress", text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "I'm safe", safeAction())
            .build()

    fun updateProgress(progress: SosProgress) {
        val text = when (progress) {
            is SosProgress.SendingAlert ->
                "Sending alert to ${plural(progress.contactCount, "contact")}..."
            is SosProgress.AlertDispatched ->
                "Alert: ${progress.sent} sent, ${progress.unconfirmed} unconfirmed, ${progress.failed} failed"
            SosProgress.WaitingForLocation -> "Alert sent. Getting your location..."
            SosProgress.SendingLocation -> "Sending your location to contacts..."
        }
        notify(PROGRESS_ID, progress(text))
    }

    fun showOutcome(outcome: SosOutcome, contactNumbers: List<String>) {
        val callAndSms = listOf(
            "Call 112" to dialEmergencyAction(),
            "Open SMS app" to smsAppAction(contactNumbers),
        )
        when (outcome) {
            SosOutcome.NoContacts -> result(
                "SOS NOT SENT",
                "You have no emergency contacts. Open Rakshak to add them.",
                listOf("Call 112" to dialEmergencyAction()),
            )

            is SosOutcome.SmsUnavailable -> result(
                "SOS NOT SENT",
                when (outcome.reason) {
                    SmsAvailability.NO_PERMISSION -> "SMS permission is turned off for Rakshak."
                    SmsAvailability.NO_SIM -> "No SIM card detected. SMS cannot be sent."
                    SmsAvailability.NO_TELEPHONY -> "This device cannot send SMS."
                    SmsAvailability.READY -> "SMS could not be sent."
                },
                if (outcome.reason == SmsAvailability.NO_PERMISSION) callAndSms
                else listOf("Call 112" to dialEmergencyAction()),
            )

            is SosOutcome.Dispatched -> {
                val total = outcome.sent + outcome.unconfirmed + outcome.failed
                if (!outcome.anyReached) {
                    result("SOS FAILED", "No SMS could be sent to your ${plural(total, "contact")}.", callAndSms)
                    return
                }
                val summary = buildString {
                    append("Sent to ${outcome.sent} of ${plural(total, "contact")}.")
                    if (outcome.unconfirmed > 0) append(" ${outcome.unconfirmed} not confirmed by the network.")
                    if (outcome.failed > 0) append(" ${outcome.failed} failed.")
                    append(" ").append(locationLine(outcome.location))
                }
                val title = if (outcome.failed == 0 && outcome.unconfirmed == 0) "SOS alert sent" else "SOS partly sent"
                val actions = buildList {
                    add("I'm safe" to safeAction())
                    if (outcome.failed > 0 || outcome.unconfirmed > 0) add("Call 112" to dialEmergencyAction())
                }
                result(title, summary, actions)
            }
        }
    }

    fun showSafeOutcome(outcome: SafeOutcome) {
        when (outcome) {
            is SafeOutcome.Sent -> result(
                "\"I'm safe\" sent",
                "Told ${plural(outcome.sent + outcome.unconfirmed, "contact")} you are safe." +
                    if (outcome.failed > 0) " ${outcome.failed} failed." else "",
                emptyList(),
            )
            is SafeOutcome.SmsUnavailable -> result(
                "\"I'm safe\" NOT sent",
                "SMS is unavailable. Please contact your emergency contacts directly.",
                emptyList(),
            )
            SafeOutcome.NoOneAlerted, SafeOutcome.NoSession -> result(
                "SOS stopped", "No contacts had been alerted.", emptyList()
            )
            SafeOutcome.AlreadySent -> Unit
        }
    }

    fun showError() = result(
        "SOS FAILED",
        "Something went wrong while sending the alert.",
        listOf("Call 112" to dialEmergencyAction()),
    )

    fun showTimeLimitReached() = result(
        "SOS stopped by the system",
        "Android ended the SOS service early. Check that your contacts received the alert.",
        listOf("Call 112" to dialEmergencyAction()),
    )

    fun notify(id: Int, notification: Notification) {
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing")
        }
    }

    private fun result(title: String, text: String, actions: List<Pair<String, PendingIntent>>) {
        val builder = base(title, text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(false)
        actions.forEach { (label, intent) -> builder.addAction(0, label, intent) }
        notify(RESULT_ID, builder.build())
    }

    private fun base(title: String, text: String) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppAction())

    private fun locationLine(report: LocationReport) = when (report) {
        LocationReport.CURRENT -> "Location shared."
        LocationReport.LAST_KNOWN_STALE -> "Only an old location was available and was sent."
        LocationReport.NO_PERMISSION -> "Location not shared (permission off)."
        LocationReport.UNAVAILABLE -> "Location could not be determined."
    }

    private fun safeAction(): PendingIntent = PendingIntent.getForegroundService(
        context, REQUEST_SAFE,
        Intent(context, SOSService::class.java).setAction(SOSService.ACTION_SAFE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun serviceAction(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        context, requestCode,
        Intent(context, SOSService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun openAppAction(): PendingIntent = PendingIntent.getActivity(
        context, REQUEST_OPEN_APP,
        Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun dialEmergencyAction(): PendingIntent = PendingIntent.getActivity(
        context, REQUEST_DIAL,
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun smsAppAction(numbers: List<String>): PendingIntent = PendingIntent.getActivity(
        context, REQUEST_SMS_APP,
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + numbers.joinToString(";")))
            .putExtra("sms_body", "SOS! I need help. Please call me or 112.")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun plural(count: Int, noun: String) = "$count $noun${if (count == 1) "" else "s"}"

    companion object {
        private const val TAG = "SosNotifications"
        const val CHANNEL_ID = "sos_channel"
        const val COUNTDOWN_CHANNEL_ID = "sos_countdown_channel"
        const val PROGRESS_ID = 2001
        const val RESULT_ID = 2002
        private const val REQUEST_SAFE = 10
        private const val REQUEST_OPEN_APP = 11
        private const val REQUEST_DIAL = 12
        private const val REQUEST_SMS_APP = 13
        private const val REQUEST_CANCEL = 14
        private const val REQUEST_SEND_NOW = 15
    }
}
