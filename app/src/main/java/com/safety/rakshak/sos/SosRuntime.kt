package com.safety.rakshak.sos

/**
 * Process-wide holder for the single [SosCountdown]. `SOSService` runs it; the Home
 * screen only observes `countdown.state` and calls `cancel()` / `sendNow()`, so there
 * is exactly one countdown implementation for every trigger.
 */
object SosRuntime {
    val countdown = SosCountdown()
}
