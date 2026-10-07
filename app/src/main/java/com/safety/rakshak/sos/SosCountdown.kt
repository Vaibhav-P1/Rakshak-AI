package com.safety.rakshak.sos

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

sealed interface CountdownState {
    data object Idle : CountdownState
    data class Counting(val secondsLeft: Int, val source: SosSource) : CountdownState
}

enum class CountdownResult {
    /** Time ran out, or the user chose "Send now". The SOS should be sent. */
    COMPLETED,
    /** The user cancelled. Nothing may be sent. */
    CANCELLED,
    /** Another countdown was already running; this call did nothing. */
    ALREADY_RUNNING,
}

/**
 * The one countdown every trigger goes through (button, volume keys, widget, tile):
 * Trigger -> [SosCountdown] -> SosOrchestrator. Pure Kotlin, so it is JVM-testable.
 * It never sends anything itself: the caller sends only on [CountdownResult.COMPLETED].
 *
 * Call [run] from one coroutine at a time (the service does); [cancel] and [sendNow]
 * may be called from anywhere, e.g. a notification action or the Home screen.
 */
class SosCountdown(private val seconds: Int = DEFAULT_SECONDS) {

    private val _state = MutableStateFlow<CountdownState>(CountdownState.Idle)
    val state: StateFlow<CountdownState> = _state.asStateFlow()

    @Volatile
    private var signal: CompletableDeferred<CountdownResult>? = null

    suspend fun run(source: SosSource): CountdownResult {
        if (signal != null) return CountdownResult.ALREADY_RUNNING
        val mine = CompletableDeferred<CountdownResult>()
        signal = mine
        try {
            for (left in seconds downTo 1) {
                _state.value = CountdownState.Counting(left, source)
                withTimeoutOrNull(TICK_MILLIS) { mine.await() }?.let { return it }
            }
            return CountdownResult.COMPLETED
        } finally {
            signal = null
            _state.value = CountdownState.Idle
        }
    }

    fun cancel() {
        signal?.complete(CountdownResult.CANCELLED)
    }

    fun sendNow() {
        signal?.complete(CountdownResult.COMPLETED)
    }

    companion object {
        const val DEFAULT_SECONDS = 3
        private const val TICK_MILLIS = 1_000L
    }
}
