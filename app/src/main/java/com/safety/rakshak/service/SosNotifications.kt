package com.safety.rakshak.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
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
/** The primary contact, for the "Call <name>" shortcut on result notifications. */
data class PrimaryContact(val name: String, val number: String)

class SosNotifications(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun str(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)
    private fun count(@PluralsRes id: Int, n: Int, vararg args: Any) =
        context.resources.getQuantityString(id, n, *args)

    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, str(R.string.notif_channel_sos), NotificationManager.IMPORTANCE_HIGH)
            .apply { description = str(R.string.notif_channel_sos_desc) }
        manager.createNotificationChannel(channel)
        // Heads-up and a short vibration so the countdown is noticed, but no sound.
        val countdownChannel = NotificationChannel(
            COUNTDOWN_CHANNEL_ID, str(R.string.notif_channel_countdown), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = str(R.string.notif_channel_countdown_desc)
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
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(str(R.string.countdown_title, secondsLeft))
            .setContentText(str(R.string.countdown_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppAction())
            .addAction(0, str(R.string.countdown_cancel), serviceAction(SOSService.ACTION_CANCEL_COUNTDOWN, REQUEST_CANCEL))
            .addAction(0, str(R.string.countdown_send_now), serviceAction(SOSService.ACTION_SEND_NOW, REQUEST_SEND_NOW))
            .build()

    fun progress(text: String): Notification =
        base(str(R.string.notif_progress_title), text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, str(R.string.notif_action_safe), safeAction())
            .build()

    fun updateProgress(progress: SosProgress) {
        val text = when (progress) {
            is SosProgress.SendingAlert ->
                count(R.plurals.notif_progress_sending_contacts, progress.contactCount, progress.contactCount)
            is SosProgress.AlertDispatched ->
                str(R.string.notif_progress_dispatched, progress.sent, progress.unconfirmed, progress.failed)
            SosProgress.WaitingForLocation -> str(R.string.notif_progress_waiting_location)
            SosProgress.SendingLocation -> str(R.string.notif_progress_sending_location)
        }
        notify(PROGRESS_ID, progress(text))
    }

    fun showOutcome(outcome: SosOutcome, contactNumbers: List<String>, primary: PrimaryContact? = null) {
        // Only adds a shortcut to the dialer; every contact was already alerted (or tried) by the pipeline.
        val call112 = str(R.string.call_112) to dialEmergencyAction()
        val callPrimary = primary?.let { listOf(str(R.string.notification_call_contact, it.name) to dialAction(it.number)) }.orEmpty()
        val callAndSms = listOf(call112, str(R.string.notif_action_open_sms) to smsAppAction(contactNumbers)) + callPrimary
        when (outcome) {
            SosOutcome.NoContacts -> result(
                str(R.string.notif_not_sent_title),
                str(R.string.notif_no_contacts),
                listOf(call112),
            )

            is SosOutcome.SmsUnavailable -> result(
                str(R.string.notif_not_sent_title),
                when (outcome.reason) {
                    SmsAvailability.NO_PERMISSION -> str(R.string.notif_sms_no_permission)
                    SmsAvailability.NO_SIM -> str(R.string.notif_sms_no_sim)
                    SmsAvailability.NO_TELEPHONY -> str(R.string.notif_sms_no_telephony)
                    SmsAvailability.READY -> str(R.string.notif_sms_unknown)
                },
                if (outcome.reason == SmsAvailability.NO_PERMISSION) callAndSms else listOf(call112) + callPrimary,
            )

            is SosOutcome.Dispatched -> {
                val total = outcome.sent + outcome.unconfirmed + outcome.failed
                if (!outcome.anyReached) {
                    result(str(R.string.notif_failed_title), count(R.plurals.notif_failed_none_sent, total, total), callAndSms)
                    return
                }
                val summary = buildString {
                    append(count(R.plurals.notif_summary_sent, total, outcome.sent, total))
                    if (outcome.unconfirmed > 0) append(" ").append(str(R.string.notif_summary_unconfirmed, outcome.unconfirmed))
                    if (outcome.failed > 0) append(" ").append(str(R.string.notif_summary_failed, outcome.failed))
                    append(" ").append(locationLine(outcome.location))
                }
                val title = str(
                    if (outcome.failed == 0 && outcome.unconfirmed == 0) R.string.notif_sent_title else R.string.notif_partly_title
                )
                val actions = buildList {
                    add(str(R.string.notif_action_safe) to safeAction())
                    if (outcome.failed > 0 || outcome.unconfirmed > 0) {
                        add(call112)
                        addAll(callPrimary)
                    }
                }
                result(title, summary, actions)
            }
        }
    }

    fun showSafeOutcome(outcome: SafeOutcome) {
        when (outcome) {
            is SafeOutcome.Sent -> {
                val told = outcome.sent + outcome.unconfirmed
                result(
                    str(R.string.notif_safe_sent_title),
                    count(R.plurals.notif_safe_sent_body, told, told) +
                        if (outcome.failed > 0) " " + str(R.string.notif_summary_failed, outcome.failed) else "",
                    emptyList(),
                )
            }
            is SafeOutcome.SmsUnavailable -> result(
                str(R.string.notif_safe_unavailable_title),
                str(R.string.notif_safe_unavailable_body),
                emptyList(),
            )
            SafeOutcome.NoOneAlerted, SafeOutcome.NoSession -> result(
                str(R.string.notif_stopped_title), str(R.string.notif_stopped_body), emptyList()
            )
            SafeOutcome.AlreadySent -> Unit
        }
    }

    fun showError() = result(
        str(R.string.notif_failed_title),
        str(R.string.notif_error_body),
        listOf(str(R.string.call_112) to dialEmergencyAction()),
    )

    fun showTimeLimitReached() = result(
        str(R.string.notif_timelimit_title),
        str(R.string.notif_timelimit_body),
        listOf(str(R.string.call_112) to dialEmergencyAction()),
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
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppAction())

    private fun locationLine(report: LocationReport) = when (report) {
        LocationReport.CURRENT -> str(R.string.notif_loc_current)
        LocationReport.LAST_KNOWN_STALE -> str(R.string.notif_loc_stale)
        LocationReport.NO_PERMISSION -> str(R.string.notif_loc_no_permission)
        LocationReport.UNAVAILABLE -> str(R.string.notif_loc_unavailable)
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

    private fun dialAction(number: String): PendingIntent = PendingIntent.getActivity(
        context, REQUEST_CALL_PRIMARY,
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun smsAppAction(numbers: List<String>): PendingIntent = PendingIntent.getActivity(
        context, REQUEST_SMS_APP,
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + numbers.joinToString(";")))
            .putExtra("sms_body", str(R.string.sos_sms_fallback_body))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

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
        private const val REQUEST_CALL_PRIMARY = 16
    }
}
