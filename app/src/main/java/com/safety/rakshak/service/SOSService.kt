package com.safety.rakshak.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.safety.rakshak.MainActivity
import com.safety.rakshak.R
import com.safety.rakshak.data.RakshakDatabase
import com.safety.rakshak.utils.LocationHelper
import com.safety.rakshak.utils.SMSHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class SOSService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val mainHandler  = Handler(Looper.getMainLooper())
    private lateinit var locationHelper: LocationHelper
    private lateinit var smsHelper: SMSHelper
    private lateinit var database: RakshakDatabase
    private lateinit var notificationManager: NotificationManager
    private var isRunning = false

    companion object {
        private const val TAG                 = "SOSService"
        private const val NOTIFICATION_ID     = 2001
        private const val CHANNEL_ID          = "sos_channel"
        private const val LOCATION_TIMEOUT_MS = 10_000L
        private const val STOP_DELAY_MS       = 4_000L
        const val ACTION_TRIGGER_SOS          = "TRIGGER_SOS"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        locationHelper = LocationHelper(this)
        smsHelper      = SMSHelper(this)
        database       = RakshakDatabase.getDatabase(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TRIGGER_SOS) {
            if (!isRunning) {
                isRunning = true
                triggerSOS()
            } else {
                Log.d(TAG, "SOS already running — ignoring duplicate")
            }
        }
        return START_NOT_STICKY
    }

    private fun triggerSOS() {
        // Check location permission
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            isRunning = false
            stopSelf()
            return
        }

        // Start foreground service
        try {
            val notification = buildNotification("SOS Triggered", "Sending emergency alerts...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "Foreground started")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
            isRunning = false
            stopSelf()
            return
        }

        // Run SOS flow
        serviceScope.launch {
            try {
                val contactsList = withContext(Dispatchers.IO) {
                    database.emergencyContactDao().getAllContactsList()
                }

                if (contactsList.isEmpty()) {
                    showNotification("SOS Error", "No emergency contacts found")
                    scheduleStop()
                    return@launch
                }

                showNotification("SOS Triggered", "Getting your location...")

                val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { locationHelper.getCurrentLocation() }
                }

                showNotification("SOS Triggered",
                    "Sending SMS to ${contactsList.size} contact${if (contactsList.size > 1) "s" else ""}...")

                smsHelper.sendSOSMessage(
                    contacts  = contactsList,
                    latitude  = location?.latitude,
                    longitude = location?.longitude,
                    onSuccess = {
                        mainHandler.post {
                            showNotification("Alert Sent ✓",
                                "SMS sent to ${contactsList.size} contact${if (contactsList.size > 1) "s" else ""}")
                            scheduleStop()
                        }
                    },
                    onError = { error ->
                        mainHandler.post {
                            showNotification("SOS Failed", error)
                            scheduleStop()
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "SOS error: ${e.message}")
                mainHandler.post {
                    showNotification("SOS Failed", "Something went wrong")
                    scheduleStop()
                }
            }
        }
    }

    private fun showNotification(title: String, content: String) {
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(title, content))
            Log.d(TAG, "Notification: $title")
        } catch (e: Exception) {
            Log.e(TAG, "showNotification: ${e.message}")
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            // Force notification to show immediately even when app is in foreground
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Emergency SOS alert notifications" }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun scheduleStop() {
        mainHandler.postDelayed({
            try {
                isRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d(TAG, "Service stopped")
            } catch (e: Exception) {
                Log.e(TAG, "scheduleStop: ${e.message}")
            }
        }, STOP_DELAY_MS)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        try { serviceScope.coroutineContext[Job]?.cancel() } catch (e: Exception) { }
    }
}