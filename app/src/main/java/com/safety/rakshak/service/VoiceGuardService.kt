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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.safety.rakshak.MainActivity
import com.safety.rakshak.R

class VoiceGuardService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val WAKE_WORDS = listOf("help rakshak", "help rakshak", "rakshak help")

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "voice_guard_channel"
        const val ACTION_START_VOICE_GUARD = "START_VOICE_GUARD"
        const val ACTION_STOP_VOICE_GUARD = "STOP_VOICE_GUARD"
        const val ACTION_TRIGGER_SOS = "TRIGGER_SOS"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VOICE_GUARD -> startVoiceGuard()
            ACTION_STOP_VOICE_GUARD -> stopVoiceGuard()
        }
        return START_STICKY
    }

    private fun startVoiceGuard() {
        val notification = createNotification("Voice Guard Active", "Listening for 'Help Rakshak'...")
        startForeground(NOTIFICATION_ID, notification)
        startListening()
    }

    private fun stopVoiceGuard() {
        stopListening()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startListening() {
        if (isListening) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                // Restart listening after error
                android.os.Handler(mainLooper).postDelayed({
                    if (this@VoiceGuardService.isListening.not()) {
                        startListening()
                    }
                }, 1000)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.forEach { result ->
                    if (WAKE_WORDS.any { result.lowercase().contains(it) }) {
                        triggerSOS()
                        return
                    }
                }
                // Continue listening
                startListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.forEach { result ->
                    if (WAKE_WORDS.any { result.lowercase().contains(it) }) {
                        triggerSOS()
                        speechRecognizer?.cancel()
                        return
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            isListening = false
        }
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun triggerSOS() {
        // Send broadcast to trigger SOS
        val sosIntent = Intent(ACTION_TRIGGER_SOS)
        sendBroadcast(sosIntent)
        
        // Also start SOS service
        val serviceIntent = Intent(this, SOSService::class.java).apply {
            action = SOSService.ACTION_TRIGGER_SOS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
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
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Guard Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification for Voice Guard service"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }
}
