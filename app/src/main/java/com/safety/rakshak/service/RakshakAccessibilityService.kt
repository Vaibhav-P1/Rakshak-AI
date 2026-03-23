package com.safety.rakshak.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class RakshakAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var volumeUpPressed     = false
    private var volumeDownPressed   = false
    private var sosAlreadyTriggered = false

    private val resetTriggerLock = Runnable { sosAlreadyTriggered = false }

    companion object {
        private const val TAG = "RakshakAccessibility"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Explicitly rebuild serviceInfo — this is required for locked screen
        // Some devices ignore XML config for key events when screen is off
        val info = AccessibilityServiceInfo().apply {
            eventTypes          = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType        = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            // FLAG_REQUEST_FILTER_KEY_EVENTS — intercept volume keys
            // FLAG_RETRIEVE_INTERACTIVE_WINDOWS — needed to stay active on lock screen
            flags               = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
        Log.d(TAG, "Accessibility service connected — key events active")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)   volumeUpPressed   = true
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) volumeDownPressed = true

                if (volumeUpPressed && volumeDownPressed && !sosAlreadyTriggered) {
                    sosAlreadyTriggered = true
                    mainHandler.removeCallbacks(resetTriggerLock)
                    mainHandler.postDelayed(resetTriggerLock, 2000L)
                    triggerSOS()
                    return true
                }
            }
            KeyEvent.ACTION_UP -> {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)   volumeUpPressed   = false
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) volumeDownPressed = false
            }
        }

        return volumeUpPressed && volumeDownPressed
    }

    private fun triggerSOS() {
        Log.d(TAG, "SOS triggered by Volume Up + Down on locked screen!")
        val intent = Intent(this, SOSService::class.java).apply {
            action = SOSService.ACTION_TRIGGER_SOS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Accessibility service destroyed")
    }
}