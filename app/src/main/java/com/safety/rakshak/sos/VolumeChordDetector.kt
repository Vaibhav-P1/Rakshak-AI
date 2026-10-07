package com.safety.rakshak.sos

/**
 * Detects "Volume Up and Volume Down held together". Pure Kotlin so it is unit-testable;
 * the accessibility service only feeds it key events.
 *
 *  - Triggers once per chord. Holding the keys (key repeat) does not trigger again, so
 *    cancelling the countdown while still holding does not restart it.
 *  - A missed key-up is forgotten after [staleAfterMillis] without any volume event, so a
 *    stuck state can never turn one later key press into an SOS.
 */
class VolumeChordDetector(private val staleAfterMillis: Long = 3_000L) {

    enum class Key { UP, DOWN }

    enum class Decision {
        /** Not part of a chord: let Android handle the key. */
        PASS,

        /** Part of a chord that already fired: swallow the key. */
        CONSUME,

        /** The chord was just completed: start the SOS countdown and swallow the key. */
        TRIGGER,
    }

    private var upHeld = false
    private var downHeld = false
    private var fired = false
    private var lastEventAt = 0L

    fun onKey(key: Key, down: Boolean, nowMillis: Long): Decision {
        if (nowMillis - lastEventAt > staleAfterMillis) {
            upHeld = false
            downHeld = false
            fired = false
        }
        lastEventAt = nowMillis

        if (key == Key.UP) upHeld = down else downHeld = down
        if (!down) fired = false // any release re-arms the chord

        val both = upHeld && downHeld
        return when {
            both && !fired -> {
                fired = true
                Decision.TRIGGER
            }
            both -> Decision.CONSUME
            else -> Decision.PASS
        }
    }
}
