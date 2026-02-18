package com.safety.rakshak.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.safety.rakshak.MainActivity
import com.safety.rakshak.R
import com.safety.rakshak.data.RakshakDatabase
import com.safety.rakshak.utils.LocationHelper
import com.safety.rakshak.utils.SMSHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SOSService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var locationHelper: LocationHelper
    private lateinit var smsHelper: SMSHelper
    private lateinit var database: RakshakDatabase

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "sos_channel"
        const val ACTION_TRIGGER_SOS = "TRIGGER_SOS"
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
        val notification = createNotification(
            "SOS Triggered",
            "Sending emergency alerts..."
        )
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            try {
                // Get current location
                val location = locationHelper.getCurrentLocation()
                
                // Get emergency contacts
                val contacts = database.emergencyContactDao().getAllContacts()
                var contactsList = emptyList<com.safety.rakshak.data.EmergencyContact>()
                
                contacts.collect { list ->
                    contactsList = list
                }

                // Send SMS alerts
                smsHelper.sendSOSMessage(
                    contacts = contactsList,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    onSuccess = {
                        updateNotification(
                            "SOS Sent Successfully",
                            "Emergency alerts sent to ${contactsList.size} contacts"
                        )
                        stopSelfAfterDelay()
                    },
                    onError = { error ->
                        updateNotification(
                            "SOS Error",
                            error
                        )
                        stopSelfAfterDelay()
                    }
                )
            } catch (e: Exception) {
                updateNotification(
                    "SOS Error",
                    "Failed to send emergency alerts: ${e.message}"
                )
                stopSelfAfterDelay()
            }
        }
    }

    private fun stopSelfAfterDelay() {
        android.os.Handler(mainLooper).postDelayed({
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }, 5000)
    }

    private fun updateNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
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
            .setAutoCancel(true)
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
}
