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
import androidx.core.app.NotificationManagerCompat
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
        if (intent?.action == ACTION_TRIGGER_SOS) triggerSOS()
        return START_NOT_STICKY
    }

    private fun triggerSOS() {
        // 1. Check location permission
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            stopSelf()
            return
        }

        // 2. Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "POST_NOTIFICATIONS not granted — notifications will not show")
                // Don't stop — still send the SMS, just no notification
            }
        }

        // 3. Start foreground — required before any async work
        try {
            val notification = buildNotification("SOS Triggered", "Sending emergency alerts...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "Foreground service started")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
            stopSelf()
            return
        }

        // 4. Run SOS flow
        serviceScope.launch {
            try {
                val contactsList = withContext(Dispatchers.IO) {
                    database.emergencyContactDao().getAllContactsList()
                }

                if (contactsList.isEmpty()) {
                    Log.w(TAG, "No contacts found")
                    showNotification("SOS Error", "No emergency contacts found")
                    scheduleStop()
                    return@launch
                }

                showNotification("SOS Triggered", "Getting your location...")
                Log.d(TAG, "Getting location for ${contactsList.size} contacts")

                val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { locationHelper.getCurrentLocation() }
                }

                Log.d(TAG, if (location != null) "Location: ${location.latitude},${location.longitude}" else "Location unavailable")
                showNotification("SOS Triggered", "Sending SMS to ${contactsList.size} contact${if (contactsList.size > 1) "s" else ""}...")

                smsHelper.sendSOSMessage(
                    contacts  = contactsList,
                    latitude  = location?.latitude,
                    longitude = location?.longitude,
                    onSuccess = {
                        Log.d(TAG, "SMS sent successfully")
                        mainHandler.post {
                            showNotification(
                                "Alert Sent",
                                "Emergency SMS sent to ${contactsList.size} contact${if (contactsList.size > 1) "s" else ""}"
                            )
                            scheduleStop()
                        }
                    },
                    onError = { error ->
                        Log.e(TAG, "SMS error: $error")
                        mainHandler.post {
                            showNotification("SOS Failed", error)
                            scheduleStop()
                        }
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "SOS coroutine error: ${e.message}")
                mainHandler.post {
                    showNotification("SOS Failed", "Something went wrong")
                    scheduleStop()
                }
            }
        }
    }

    private fun showNotification(title: String, content: String) {
        try {
            Log.d(TAG, "Notification: $title — $content")
            notificationManager.notify(NOTIFICATION_ID, buildNotification(title, content))
        } catch (e: Exception) {
            Log.e(TAG, "showNotification failed: ${e.message}")
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
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency SOS alert notifications"
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        }
    }

    private fun scheduleStop() {
        mainHandler.postDelayed({
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d(TAG, "Service stopped")
            } catch (e: Exception) {
                Log.e(TAG, "scheduleStop error: ${e.message}")
            }
        }, STOP_DELAY_MS)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        try { serviceScope.coroutineContext[Job]?.cancel() } catch (e: Exception) { }
    }
}