package com.safety.rakshak.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safety.rakshak.MainActivity
import com.safety.rakshak.R
import kotlin.math.sqrt

class ShakeDetectorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // ── Shake detection config ────────────────────────────────────
    // How hard the shake needs to be (m/s²). Gravity is ~9.8 so
    // SHAKE_THRESHOLD of 20 — slightly lower since SENSOR_DELAY_NORMAL misses peaks
    private val SHAKE_THRESHOLD     = 20f
    // Minimum time between two shake counts (ms)
    private val SHAKE_SLOP_MS       = 400L
    // How many shakes needed to trigger SOS
    private val REQUIRED_SHAKES     = 3
    // Wider window since NORMAL delay updates are less frequent
    private val SHAKE_WINDOW_MS     = 3000L

    private var shakeCount          = 0
    private var lastShakeTimeMs     = 0L
    private var firstShakeTimeMs    = 0L

    companion object {
        private const val TAG          = "ShakeDetectorService"
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID   = "shake_detector_channel"
        const val ACTION_START_SHAKE   = "START_SHAKE_DETECTOR"
        const val ACTION_STOP_SHAKE    = "STOP_SHAKE_DETECTOR"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager  = getSystemService(SensorManager::class.java)
        accelerometer  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SHAKE -> startDetector()
            ACTION_STOP_SHAKE  -> stopDetector()
        }
        return START_STICKY
    }

    // ── Detector lifecycle ────────────────────────────────────────

    private fun startDetector() {
        if (accelerometer == null) {
            Log.e(TAG, "No accelerometer found on this device")
            stopSelf()
            return
        }

        val notification = createNotification(
            "Shake Guard Active",
            "Shake phone 3 times quickly to trigger SOS"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // SENSOR_DELAY_NORMAL works better with screen off
        // registerListener with wakeLockTimeout keeps sensor alive in sleep
        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        Log.d(TAG, "Shake detector started")
    }

    private fun stopDetector() {
        sensorManager.unregisterListener(this)
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Shake detector stopped")
    }

    // ── Sensor callbacks ──────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate total acceleration magnitude minus gravity
        // Using gForce gives us net movement force regardless of orientation
        val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        if (gForce < SHAKE_THRESHOLD) return  // not a shake, ignore

        val nowMs = System.currentTimeMillis()

        // Ignore if too soon after last shake (sensor noise / same shake)
        if (nowMs - lastShakeTimeMs < SHAKE_SLOP_MS) return

        // Reset count if shake window has expired
        if (nowMs - firstShakeTimeMs > SHAKE_WINDOW_MS) {
            shakeCount     = 0
            firstShakeTimeMs = nowMs
        }

        lastShakeTimeMs = nowMs
        shakeCount++

        Log.d(TAG, "Shake detected! Count: $shakeCount / $REQUIRED_SHAKES (force: $gForce)")

        // Update notification to show progress
        updateNotification(
            "Shake Guard Active",
            if (shakeCount < REQUIRED_SHAKES)
                "Shake ${REQUIRED_SHAKES - shakeCount} more time${if (REQUIRED_SHAKES - shakeCount > 1) "s" else ""} to trigger SOS"
            else
                "Triggering SOS..."
        )

        if (shakeCount >= REQUIRED_SHAKES) {
            shakeCount = 0
            firstShakeTimeMs = 0L
            triggerSOS()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ── SOS Trigger ───────────────────────────────────────────────

    private fun triggerSOS() {
        Log.d(TAG, "SOS triggered by shake!")

        val serviceIntent = Intent(this, SOSService::class.java).apply {
            action = SOSService.ACTION_TRIGGER_SOS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    // ── Wake Lock ─────────────────────────────────────────────────
    // Keeps CPU alive so sensor events fire even with screen off

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(PowerManager::class.java)
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Rakshak::ShakeDetectorWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L) // 1 hour max
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock release failed: ${e.message}")
        }
    }

    // ── Notification ──────────────────────────────────────────────

    private fun updateNotification(title: String, content: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, createNotification(title, content))
        } catch (e: Exception) {
            Log.e(TAG, "updateNotification failed: ${e.message}")
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shake Detector",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps shake detection running in background"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        releaseWakeLock()
        Log.d(TAG, "ShakeDetectorService destroyed")
    }
}