package com.safety.rakshak.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safety.rakshak.MainActivity
import com.safety.rakshak.R
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.Locale

class VoiceGuardService : Service(), RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isActive = false
    private var sosCooldown = false
    private val mainHandler = Handler(Looper.getMainLooper())


    companion object {
        private const val TAG = "VoiceGuardService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "voice_guard_channel"
        const val ACTION_START_VOICE_GUARD = "START_VOICE_GUARD"
        const val ACTION_STOP_VOICE_GUARD  = "STOP_VOICE_GUARD"
        const val ACTION_TRIGGER_SOS       = "TRIGGER_SOS"
        private const val SOS_COOLDOWN_MS  = 10_000L
        private const val MODEL_NAME       = "vosk-model-small-en-us-0.15"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VOICE_GUARD -> startVoiceGuard()
            ACTION_STOP_VOICE_GUARD  -> stopVoiceGuard()
        }
        return START_STICKY
    }

    // ── Wake lock ─────────────────────────────────────────────────
    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Rakshak::VoiceGuardVosk"
            ).apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) { }
    }

    // ── Voice Guard lifecycle ─────────────────────────────────────
    private fun startVoiceGuard() {
        isActive = true
        startForeground(NOTIFICATION_ID, createNotification(
            "Voice Guard Active", "Loading voice model..."
        ))

        // Manual model extraction — bypasses StorageService.unpack()
        // which has known issues with nested asset folder structures
        Thread {
            try {
                val modelDir = java.io.File(filesDir, MODEL_NAME)
                if (!modelDir.exists()) {
                    Log.d(TAG, "Extracting model from assets to ${modelDir.absolutePath}")
                    copyAssetFolder(MODEL_NAME, modelDir.absolutePath)
                }

                val loadedModel = Model(modelDir.absolutePath)
                model = loadedModel

                mainHandler.post {
                    updateNotification("Voice Guard Active", "Listening for emergency help...")
                    startListeningWithVosk()
                }
                Log.d(TAG, "Vosk model loaded successfully from ${modelDir.absolutePath}")

            } catch (e: Exception) {
                Log.e(TAG, "Manual model load failed: ${e.message}", e)
                mainHandler.post {
                    updateNotification("Voice Guard Error", "Model load failed: ${e.message}")
                }
            }
        }.start()
    }

    // Recursively copies an assets folder to internal storage
    private fun copyAssetFolder(assetPath: String, targetPath: String): Boolean {
        return try {
            val assetManager = assets
            val files = assetManager.list(assetPath) ?: return false

            java.io.File(targetPath).mkdirs()

            if (files.isEmpty()) {
                // It's a file, not a folder
                assetManager.open(assetPath).use { input ->
                    java.io.File(targetPath).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                // It's a folder, recurse into it
                for (file in files) {
                    val subAssetPath = "$assetPath/$file"
                    val subTargetPath = "$targetPath/$file"
                    val subFiles = assetManager.list(subAssetPath)
                    if (subFiles.isNullOrEmpty()) {
                        // Leaf file
                        assetManager.open(subAssetPath).use { input ->
                            java.io.File(subTargetPath).outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        // Nested folder — recurse
                        copyAssetFolder(subAssetPath, subTargetPath)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "copyAssetFolder error for $assetPath: ${e.message}", e)
            false
        }
    }

    private fun stopVoiceGuard() {
        isActive = false
        mainHandler.removeCallbacksAndMessages(null)
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startListeningWithVosk() {
        if (!isActive || model == null) return
        try {
            val sampleRate = 16000.0f
            val rec = Recognizer(model, sampleRate)

            val audioManager = getSystemService(android.media.AudioManager::class.java)
            audioManager.requestAudioFocus(
                null,
                android.media.AudioManager.STREAM_VOICE_CALL,
                android.media.AudioManager.AUDIOFOCUS_GAIN
            )

            speechService = SpeechService(rec, sampleRate)
            speechService?.startListening(this)
            Log.d(TAG, "Vosk listening with grammar at ${sampleRate}Hz")
        } catch (e: Exception) {
            Log.e(TAG, "startListeningWithVosk error: ${e.message}", e)
            if (isActive) mainHandler.postDelayed({ startListeningWithVosk() }, 2000)
        }
    }

    // ── Vosk RecognitionListener callbacks ──────────────────────────
    // These fire continuously as Vosk processes raw audio — completely
    // silent, no Android privacy beep since we never use SpeechRecognizer

    override fun onPartialResult(hypothesis: String?) {
        // Ignore partial results to avoid false SOS triggers.
    }

    override fun onResult(hypothesis: String?) {
        Log.d(TAG, "Result: $hypothesis")
        checkForWakeWord(hypothesis)
    }

    override fun onFinalResult(hypothesis: String?) {
        Log.d(TAG, "Final: $hypothesis")
        checkForWakeWord(hypothesis)
    }

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Vosk error: ${exception?.message}")
        if (isActive) {
            mainHandler.postDelayed({ startListeningWithVosk() }, 2000)
        }
    }

    override fun onTimeout() {
        // Vosk session timeout — restart listening
        if (isActive) startListeningWithVosk()
    }

    private fun checkForWakeWord(jsonResult: String?) {
        if (jsonResult.isNullOrBlank() || sosCooldown) return

        try {
            val json = JSONObject(jsonResult)

            val text = json.optString("text")
                .lowercase(Locale.ROOT)
                .replace("[unk]", "")
                .trim()

            if (text.isBlank()) return

            Log.d(TAG, "Recognized: $text")

            val helpCount = text
                .split(Regex("\\s+"))
                .count { it == "help" }

            if (helpCount >= 3) {
                Log.d(TAG, "Emergency phrase detected")

                sosCooldown = true
                mainHandler.postDelayed({
                    sosCooldown = false
                }, SOS_COOLDOWN_MS)

                triggerSOS()
            }

        } catch (e: Exception) {
            Log.e(TAG, "checkForWakeWord parse error: ${e.message}")
        }
    }

    // ── SOS Trigger ───────────────────────────────────────────────
    private fun triggerSOS() {
        sendBroadcast(Intent(ACTION_TRIGGER_SOS))
        val serviceIntent = Intent(this, SOSService::class.java).apply {
            action = SOSService.ACTION_TRIGGER_SOS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(serviceIntent)
        else
            startService(serviceIntent)
    }

    // ── Notification ──────────────────────────────────────────────
    private fun createNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, createNotification(title, content))
        } catch (e: Exception) { }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Voice Guard Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps Voice Guard running in background" }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isActive = false
        mainHandler.removeCallbacksAndMessages(null)
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        releaseWakeLock()
    }
}