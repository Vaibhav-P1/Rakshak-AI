package com.safety.rakshak.service

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.safety.rakshak.data.RakshakDatabase
import com.safety.rakshak.sos.CountdownResult
import com.safety.rakshak.sos.CountdownState
import com.safety.rakshak.sos.SosCountdown
import com.safety.rakshak.sos.SosOrchestrator
import com.safety.rakshak.sos.SosRuntime
import com.safety.rakshak.sos.SosSource
import com.safety.rakshak.sos.normalizePhoneNumber
import com.safety.rakshak.sos.platform.SosPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs the SOS pipeline as a foreground service: every trigger goes
 * trigger() -> [SosCountdown] -> [SosOrchestrator]. Nothing is sent (and no session is
 * created) until the countdown completes, except when an interrupted SOS is being resumed.
 *
 * The service never aborts the SOS because of the foreground-service type: it
 * tries the `location` type and falls back to `shortService` (Android 14+).
 * If neither is allowed, it still runs the pipeline.
 */
class SOSService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var orchestrator: SosOrchestrator
    private lateinit var notifications: SosNotifications

    private var sosJob: Job? = null
    private var runningWork = 0

    companion object {
        private const val TAG = "SOSService"
        const val ACTION_TRIGGER_SOS = "TRIGGER_SOS"
        const val ACTION_SAFE = "com.safety.rakshak.action.SOS_SAFE"
        const val ACTION_CANCEL_COUNTDOWN = "com.safety.rakshak.action.CANCEL_COUNTDOWN"
        const val ACTION_SEND_NOW = "com.safety.rakshak.action.SEND_NOW"
        const val EXTRA_SOURCE = "source"

        /**
         * Single entry point for every SOS trigger. Falls back to a plain
         * startService if a foreground start is refused (e.g. from the background).
         * Returns false only if the service could not be started at all.
         */
        fun trigger(context: Context, source: SosSource): Boolean {
            val intent = Intent(context, SOSService::class.java)
                .setAction(ACTION_TRIGGER_SOS)
                .putExtra(EXTRA_SOURCE, source.name)
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Foreground start refused (${e.javaClass.simpleName}); trying plain start")
                try {
                    context.startService(intent)
                    true
                } catch (e2: Exception) {
                    Log.e(TAG, "Could not start SOS service: ${e2.javaClass.simpleName}")
                    false
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        orchestrator = SosPlatform.orchestrator(this)
        notifications = SosNotifications(this).also { it.ensureChannel() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_SAFE -> {
                goForeground(notifications.progress("Telling your contacts you are safe..."))
                handleSafe()
                START_NOT_STICKY
            }
            ACTION_TRIGGER_SOS -> {
                if (sosJob?.isActive == true) {
                    // Already counting down or sending: the foreground notification is in place.
                    Log.d(TAG, "SOS already running; duplicate trigger ignored")
                } else {
                    // Only a redelivery after the process was killed may skip the countdown: that SOS
                    // was already confirmed. A fresh trigger always gets one, even if a stale session exists.
                    val resuming = (flags and START_FLAG_REDELIVERY) != 0 && orchestrator.hasResumableSession()
                    goForeground(
                        if (resuming) notifications.progress("Sending emergency alert...")
                        else notifications.countdown(SosCountdown.DEFAULT_SECONDS)
                    )
                    val source = intent.getStringExtra(EXTRA_SOURCE)
                        ?.let { name -> SosSource.entries.firstOrNull { it.name == name } }
                        ?: SosSource.APP_BUTTON
                    startSos(source, countdown = !resuming)
                }
                // If the process is killed mid-SOS, the system redelivers this intent
                // and the orchestrator resumes the persisted session.
                START_REDELIVER_INTENT
            }
            ACTION_CANCEL_COUNTDOWN, ACTION_SEND_NOW -> {
                if (intent.action == ACTION_CANCEL_COUNTDOWN) SosRuntime.countdown.cancel()
                else SosRuntime.countdown.sendNow()
                if (sosJob?.isActive != true) finishIfIdle() // stale notification action
                // Not START_NOT_STICKY: that would stop the system restarting a running SOS.
                START_REDELIVER_INTENT
            }
            else -> {
                goForeground(notifications.progress("Rakshak"))
                finishIfIdle()
                START_NOT_STICKY
            }
        }
    }

    private fun startSos(source: SosSource, countdown: Boolean) {
        runningWork++
        sosJob = scope.launch {
            try {
                if (countdown && !awaitCountdown(source)) {
                    Log.d(TAG, "SOS cancelled during the countdown; nothing was sent")
                    return@launch
                }
                notifications.notify(SosNotifications.PROGRESS_ID, notifications.progress("Sending emergency alert..."))
                val outcome = orchestrator.run(source) { notifications.updateProgress(it) }
                notifications.showOutcome(outcome, contactNumbers())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "SOS pipeline error: ${e.javaClass.simpleName}")
                notifications.showError()
            } finally {
                runningWork--
                finishIfIdle()
            }
        }
    }

    /** Runs the one countdown, mirroring it in the notification. True if the SOS should be sent. */
    private suspend fun awaitCountdown(source: SosSource): Boolean {
        val countdown = SosRuntime.countdown
        val ticker = scope.launch {
            countdown.state.collect { state ->
                if (state is CountdownState.Counting) {
                    notifications.notify(SosNotifications.PROGRESS_ID, notifications.countdown(state.secondsLeft))
                }
            }
        }
        try {
            return countdown.run(source) == CountdownResult.COMPLETED
        } finally {
            ticker.cancel()
        }
    }

    private fun handleSafe() {
        val runningSos = sosJob
        runningSos?.cancel()
        runningWork++
        scope.launch {
            try {
                runningSos?.join()
                notifications.showSafeOutcome(orchestrator.sendSafeMessage())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Safe message error: ${e.javaClass.simpleName}")
                notifications.showError()
            } finally {
                runningWork--
                finishIfIdle()
            }
        }
    }

    private suspend fun contactNumbers(): List<String> = try {
        RakshakDatabase.getDatabase(this).emergencyContactDao().getAllContactsList()
            .mapNotNull { normalizePhoneNumber(it.phoneNumber) }
    } catch (e: Exception) {
        emptyList()
    }

    /** Tries each allowed foreground type in order. Returns false if none worked. */
    private fun goForeground(notification: Notification): Boolean {
        val types = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasAnyLocationPermission()) {
                add(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            } else {
                add(0) // Before Android 14: no runtime type check
            }
        }
        for (type in types) {
            try {
                if (type == 0) startForeground(SosNotifications.PROGRESS_ID, notification)
                else ServiceCompat.startForeground(this, SosNotifications.PROGRESS_ID, notification, type)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "startForeground(type=$type) refused: ${e.javaClass.simpleName}")
            }
        }
        Log.e(TAG, "Running SOS without foreground status")
        return false
    }

    private fun hasAnyLocationPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun finishIfIdle() {
        if (runningWork > 0) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Android 14+: a shortService has about 3 minutes. The SOS pipeline normally finishes well within that. */
    override fun onTimeout(startId: Int) {
        Log.w(TAG, "shortService time limit reached")
        notifications.showTimeLimitReached()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
