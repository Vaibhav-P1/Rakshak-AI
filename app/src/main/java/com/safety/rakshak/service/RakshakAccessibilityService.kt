package com.safety.rakshak.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.safety.rakshak.sos.SosSource
import com.safety.rakshak.sos.VolumeChordDetector
import com.safety.rakshak.sos.VolumeChordDetector.Decision
import com.safety.rakshak.sos.VolumeChordDetector.Key

/**
 * Volume Guard. Uses the accessibility API only to filter key events: the service
 * declares no event types and cannot read window content (see
 * res/xml/accessibility_service_config.xml). Pressing Volume Up + Down together starts
 * the same SOS countdown as every other trigger.
 */
class RakshakAccessibilityService : AccessibilityService() {

    private val chord = VolumeChordDetector()

    companion object {
        private const val TAG = "RakshakAccessibility"
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> Key.UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> Key.DOWN
            else -> return false
        }
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }

        return when (chord.onKey(key, down, SystemClock.elapsedRealtime())) {
            Decision.TRIGGER -> {
                Log.d(TAG, "Volume Up + Down pressed together")
                SOSService.trigger(this, SosSource.VOLUME_KEYS)
                true
            }
            Decision.CONSUME -> true
            Decision.PASS -> false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }
}
