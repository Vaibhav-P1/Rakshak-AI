package com.safety.rakshak.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
    private var isListening  = false
    private var isActive     = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private val mainHandler  = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager

    private val WAKE_WORDS = listOf(
        "help rakshak", "rakshak help",
        "help rakshaak", "raksha help"
    )

    companion object {
        private const val TAG              = "VoiceGuardService"
        private const val NOTIFICATION_ID  = 1001
        private const val CHANNEL_ID       = "voice_guard_channel"
        const val ACTION_START_VOICE_GUARD = "START_VOICE_GUARD"
        const val ACTION_STOP_VOICE_GUARD  = "STOP_VOICE_GUARD"
        const val ACTION_TRIGGER_SOS       = "TRIGGER_SOS"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(AudioManager::class.java)
        acquireWakeLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VOICE_GUARD -> startVoiceGuard()
            ACTION_STOP_VOICE_GUARD  -> stopVoiceGuard()
        }
        return START_STICKY
    }

    // ── Wake locks ────────────────────────────────────────────────
    private fun acquireWakeLocks() {
        try {
            val pm = getSystemService(PowerManager::class.java)

            // PARTIAL_WAKE_LOCK — keeps CPU on, screen can be off
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Rakshak::VoiceGuardCPU"
            ).apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L)
            }

            // SCREEN_DIM_WAKE_LOCK — keeps screen just barely alive
            // so SpeechRecognizer can receive audio on lock screen
            @Suppress("DEPRECATION")
            screenWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                "Rakshak::VoiceGuardScreen"
            ).apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L)
            }

            Log.d(TAG, "WakeLocks acquired")
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock failed: ${e.message}")
        }
    }

    private fun releaseWakeLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (screenWakeLock?.isHeld == true) screenWakeLock?.release()
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock release: ${e.message}")
        }
    }

    // ── Voice Guard ───────────────────────────────────────────────
    private fun startVoiceGuard() {
        isActive = true
        startForeground(
            NOTIFICATION_ID,
            createNotification("Voice Guard Active", "Listening for 'Help Rakshak'...")
        )
        initRecognizer()
        startListening()
    }

    private fun stopVoiceGuard() {
        isActive = false
        mainHandler.removeCallbacksAndMessages(null)
        stopListening()
        releaseWakeLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun initRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(recognitionListener)
        } catch (e: Exception) {
            Log.e(TAG, "initRecognizer: ${e.message}")
        }
    }

    private fun startListening() {
        if (!isActive || isListening) return

        mainHandler.post {
            try {
                // Suppress beep by muting for 200ms
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_MUTE, 0
                )
                mainHandler.postDelayed({
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_UNMUTE, 0
                    )
                }, 200)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra("android.speech.extra.EXTRA_CALLING_PACKAGE", packageName)
                    // Long silence window keeps session alive longer
                    // reducing restart frequency = fewer beeps
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
                }

                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d(TAG, "Listening started")
            } catch (e: Exception) {
                Log.e(TAG, "startListening: ${e.message}")
                isListening = false
                scheduleRestart(2000L)
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
            Log.e(TAG, "stopListening: ${e.message}")
        }
    }

    private fun scheduleRestart(delayMs: Long = 1000L) {
        if (!isActive) return
        mainHandler.postDelayed({
            if (isActive && !isListening) {
                initRecognizer()
                startListening()
            }
        }, delayMs)
    }

    // ── Listener ──────────────────────────────────────────────────
    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { isListening = false }

        override fun onError(error: Int) {
            isListening = false
            if (!isActive) return

            val delay = when (error) {
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER          -> 5000L
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 3000L
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    Log.e(TAG, "Microphone permission denied")
                    stopVoiceGuard()
                    return
                }
                else -> 1000L
            }
            Log.w(TAG, "Error $error — restarting in ${delay}ms")
            scheduleRestart(delay)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.d(TAG, "Results: $matches")
            if (!checkForWakeWord(matches) && isActive) {
                // Reuse same recognizer — no destroy/recreate = no beep
                startListening()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (checkForWakeWord(matches)) speechRecognizer?.cancel()
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun checkForWakeWord(matches: ArrayList<String>?): Boolean {
        matches?.forEach { result ->
            if (WAKE_WORDS.any { result.lowercase().trim().contains(it) }) {
                Log.d(TAG, "Wake word detected: $result")
                triggerSOS()
                return true
            }
        }
        return false
    }

    // ── SOS ───────────────────────────────────────────────────────
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
        mainHandler.removeCallbacksAndMessages(null)
        try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_UNMUTE, 0
            )
        } catch (e: Exception) { }
        stopListening()
        releaseWakeLocks()
    }
}
