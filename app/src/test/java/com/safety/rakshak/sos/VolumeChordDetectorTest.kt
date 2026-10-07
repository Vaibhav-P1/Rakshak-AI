package com.safety.rakshak.sos

import com.safety.rakshak.sos.VolumeChordDetector.Decision
import com.safety.rakshak.sos.VolumeChordDetector.Key
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeChordDetectorTest {

    private val detector = VolumeChordDetector(staleAfterMillis = 3_000L)

    @Test
    fun `a single key is passed through so normal volume control works`() {
        assertEquals(Decision.PASS, detector.onKey(Key.UP, down = true, nowMillis = 0))
        assertEquals(Decision.PASS, detector.onKey(Key.UP, down = false, nowMillis = 100))
        assertEquals(Decision.PASS, detector.onKey(Key.DOWN, down = true, nowMillis = 5_000))
    }

    @Test
    fun `both keys held triggers exactly once`() {
        detector.onKey(Key.UP, down = true, nowMillis = 0)
        assertEquals(Decision.TRIGGER, detector.onKey(Key.DOWN, down = true, nowMillis = 50))
        // Key repeat while still held must not trigger again, but stays consumed.
        assertEquals(Decision.CONSUME, detector.onKey(Key.UP, down = true, nowMillis = 100))
        assertEquals(Decision.CONSUME, detector.onKey(Key.DOWN, down = true, nowMillis = 150))
    }

    @Test
    fun `holding the keys after a cancel does not trigger again until a key is released`() {
        detector.onKey(Key.DOWN, down = true, nowMillis = 0)
        assertEquals(Decision.TRIGGER, detector.onKey(Key.UP, down = true, nowMillis = 10))
        // Still held for a long time (user cancelled the countdown meanwhile), with key repeats.
        for (t in 500L..2_500L step 500L) {
            assertEquals(Decision.CONSUME, detector.onKey(Key.UP, down = true, nowMillis = t))
        }
        // Release one key and press it again: a deliberate new chord.
        detector.onKey(Key.UP, down = false, nowMillis = 2_600)
        assertEquals(Decision.TRIGGER, detector.onKey(Key.UP, down = true, nowMillis = 2_700))
    }

    @Test
    fun `a missed key-up cannot make a later single press trigger`() {
        detector.onKey(Key.UP, down = true, nowMillis = 0)
        // The key-up for UP was never delivered. Much later, only DOWN is pressed.
        assertEquals(Decision.PASS, detector.onKey(Key.DOWN, down = true, nowMillis = 60_000))
    }

    @Test
    fun `releasing a key ends the chord`() {
        detector.onKey(Key.UP, down = true, nowMillis = 0)
        detector.onKey(Key.DOWN, down = true, nowMillis = 10)
        assertEquals(Decision.PASS, detector.onKey(Key.UP, down = false, nowMillis = 200))
        assertEquals(Decision.PASS, detector.onKey(Key.DOWN, down = false, nowMillis = 210))
    }
}
