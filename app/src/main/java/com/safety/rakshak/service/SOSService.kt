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
import android.os.IBinder
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
    private lateinit var locationHelper: LocationHelper
    private lateinit var smsHelper: SMSHelper
    private lateinit var database: RakshakDatabase

    companion object {
        private const val TAG = "SOSService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "sos_channel"
        const val ACTION_TRIGGER_SOS = "TRIGGER_SOS"
        private const val LOCATION_TIMEOUT_MS = 10_000L // 10 seconds max wait for location
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        locationHelper = LocationHelper(this)
        smsHelper = SMSHelper(this)
        database = RakshakDatabase.getDatabase(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRIGGER_SOS -> triggerSOS()
        }
        return START_NOT_STICKY
    }

    private fun triggerSOS() {
        // Check location permission before starting foreground
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            stopSelf()
            return
        }

        // Start foreground with correct type
        try {
            val notification = createNotification(
                "SOS Triggered",
                "Sending emergency alerts..."
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
            stopSelf()
            return
        }

        serviceScope.launch {
            try {
                // Get emergency contacts
                val contactsList = withContext(Dispatchers.IO) {
                    database.emergencyContactDao().getAllContactsList()
                }

                if (contactsList.isEmpty()) {
                    updateNotification("SOS Error", "No emergency contacts found. Please add contacts first.")
                    stopSelfAfterDelay()
                    return@launch
                }

                Log.d(TAG, "Found ${contactsList.size} contacts, getting location...")
                updateNotification("SOS Triggered", "Getting your location...")

                // Add 10 second timeout — if location takes too long, send SMS without it
                val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        locationHelper.getCurrentLocation()
                    }
                }

                if (location == null) {
                    Log.w(TAG, "Location timed out or unavailable — sending SOS without location")
                    updateNotification("SOS Triggered", "Sending alerts (location unavailable)...")
                } else {
                    Log.d(TAG, "Location obtained: ${location.latitude}, ${location.longitude}")
                }

                // Send SMS alerts
                updateNotification("SOS Triggered", "Sending SMS to ${contactsList.size} contacts...")

                smsHelper.sendSOSMessage(
                    contacts = contactsList,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    onSuccess = {
                        Log.d(TAG, "SOS SMS sent successfully")
                        updateNotification(
                            "SOS Sent ✅",
                            "Emergency alerts sent to ${contactsList.size} contacts"
                        )
                        stopSelfAfterDelay()
                    },
                    onError = { error ->
                        Log.e(TAG, "SOS SMS error: $error")
                        updateNotification("SOS Error ❌", error)
                        stopSelfAfterDelay()
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "SOS coroutine failed: ${e.message}")
                e.printStackTrace()
                updateNotification("SOS Error ❌", "Failed: ${e.message}")
                stopSelfAfterDelay()
            }
        }
    }

    private fun stopSelfAfterDelay() {
        android.os.Handler(mainLooper).postDelayed({
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 5000)
    }

    private fun updateNotification(title: String, content: String) {
        try {
            val notification = createNotification(title, content)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
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
                description = "Notifications for SOS emergency alerts"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            serviceScope.coroutineContext[Job]?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}