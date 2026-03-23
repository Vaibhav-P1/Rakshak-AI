package com.safety.rakshak.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safety.rakshak.MainActivity
import com.safety.rakshak.R

class VoiceGuardService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isActive = false  // tracks if service should keep restarting
    private var wakeLock: PowerManager.WakeLock? = null

    // Wake words to detect
    private val WAKE_WORDS = listOf("help rakshak", "rakshak help", "help rakshaak")

    companion object {
        private const val TAG = "VoiceGuardService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "voice_guard_channel"
        const val ACTION_START_VOICE_GUARD = "START_VOICE_GUARD"
        const val ACTION_STOP_VOICE_GUARD  = "STOP_VOICE_GUARD"
        const val ACTION_TRIGGER_SOS       = "TRIGGER_SOS"
        private const val RESTART_DELAY_MS = 1000L
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
        return START_STICKY // restart service if killed by system
    }

    // ── Wake Lock ─────────────────────────────────────────────────
    // Keeps CPU awake so recognition continues even with screen off
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(PowerManager::class.java)
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Rakshak::VoiceGuardWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L) // acquire for max 1 hour
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock release failed: ${e.message}")
        }
    }

    // ── Voice Guard ───────────────────────────────────────────────
    private fun startVoiceGuard() {
        isActive = true
        startForeground(NOTIFICATION_ID, createNotification(
            "Voice Guard Active",
            "Listening for 'Help Rakshak'..."
        ))
        initRecognizer()
        startListening()
        Log.d(TAG, "Voice Guard started")
    }

    private fun stopVoiceGuard() {
        isActive = false
        stopListening()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Voice Guard stopped")
    }

    private fun initRecognizer() {
        // Always destroy old instance before creating new one
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        Log.d(TAG, "SpeechRecognizer initialized")
    }

    private fun startListening() {
        if (!isActive || isListening) return

        // SpeechRecognizer MUST run on main thread
        android.os.Handler(mainLooper).post {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                }

                speechRecognizer?.setRecognitionListener(recognitionListener)
                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d(TAG, "Listening started")
            } catch (e: Exception) {
                Log.e(TAG, "startListening error: ${e.message}")
                isListening = false
                scheduleRestart()
            }
        }
    }

    private fun stopListening() {
        isListening = false
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "stopListening error: ${e.message}")
        }
    }

    // Restart after a short delay — necessary since SpeechRecognizer
    // only listens for one session at a time
    private fun scheduleRestart(delayMs: Long = RESTART_DELAY_MS) {
        if (!isActive) return
        android.os.Handler(mainLooper).postDelayed({
            if (isActive && !isListening) {
                initRecognizer()
                startListening()
            }
        }, delayMs)
    }

    // ── Recognition listener ──────────────────────────────────────
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
            Log.d(TAG, "End of speech")
        }

        override fun onError(error: Int) {
            isListening = false
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO              -> "Audio error"
                SpeechRecognizer.ERROR_CLIENT             -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission error"
                SpeechRecognizer.ERROR_NETWORK            -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT    -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH           -> "No match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY    -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER             -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT     -> "Speech timeout"
                else -> "Unknown error $error"
            }
            Log.w(TAG, "Recognition error: $errorMsg")

            // Restart with longer delay for network errors, short for others
            val delay = when (error) {
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> 3000L
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY   -> 2000L
                else -> RESTART_DELAY_MS
            }
            scheduleRestart(delay)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.d(TAG, "Results: $matches")
            if (checkForWakeWord(matches)) return
            scheduleRestart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.d(TAG, "Partial: $matches")
            if (checkForWakeWord(matches)) {
                speechRecognizer?.cancel()
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // Returns true if wake word was detected and SOS triggered
    private fun checkForWakeWord(matches: ArrayList<String>?): Boolean {
        matches?.forEach { result ->
            val lower = result.lowercase().trim()
            if (WAKE_WORDS.any { lower.contains(it) }) {
                Log.d(TAG, "Wake word detected: $result")
                triggerSOS()
                return true
            }
        }
        return false
    }

    // ── SOS Trigger ───────────────────────────────────────────────
    private fun triggerSOS() {
        Log.d(TAG, "Triggering SOS from VoiceGuard")

        // Broadcast to MainActivity
        sendBroadcast(Intent(ACTION_TRIGGER_SOS))

        // Start SOS service directly
        val serviceIntent = Intent(this, SOSService::class.java).apply {
            action = SOSService.ACTION_TRIGGER_SOS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    // ── Notification ──────────────────────────────────────────────
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Guard Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Voice Guard running in background"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isActive = false
        stopListening()
        releaseWakeLock()
        Log.d(TAG, "VoiceGuardService destroyed")
    }
}