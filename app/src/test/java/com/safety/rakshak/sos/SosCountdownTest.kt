package com.safety.rakshak.sos

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SosCountdownTest {

    private val countdown = SosCountdown(seconds = 3)

    @Test
    fun `completes after exactly three seconds for every source`() = runTest {
        for (source in SosSource.entries) {
            val start = currentTime
            val result = countdown.run(source)
            assertEquals(CountdownResult.COMPLETED, result)
            assertEquals(3_000L, currentTime - start)
            assertEquals(CountdownState.Idle, countdown.state.value)
        }
    }

    @Test
    fun `state ticks 3 2 1 then goes idle`() = runTest {
        val job = async { countdown.run(SosSource.WIDGET) }
        runCurrent()
        assertEquals(CountdownState.Counting(3, SosSource.WIDGET), countdown.state.value)
        advanceTimeBy(1_000L); runCurrent()
        assertEquals(CountdownState.Counting(2, SosSource.WIDGET), countdown.state.value)
        advanceTimeBy(1_000L); runCurrent()
        assertEquals(CountdownState.Counting(1, SosSource.WIDGET), countdown.state.value)
        advanceTimeBy(1_000L); runCurrent()
        assertEquals(CountdownResult.COMPLETED, job.await())
        assertEquals(CountdownState.Idle, countdown.state.value)
    }

    @Test
    fun `cancel stops the countdown and reports cancelled`() = runTest {
        val job = async { countdown.run(SosSource.VOLUME_KEYS) }
        advanceTimeBy(1_500L); runCurrent()
        countdown.cancel()
        assertEquals(CountdownResult.CANCELLED, job.await())
        assertEquals("Cancel must not wait for the remaining time", 1_500L, currentTime)
        assertEquals(CountdownState.Idle, countdown.state.value)
    }

    @Test
    fun `send now finishes immediately as completed`() = runTest {
        val job = async { countdown.run(SosSource.APP_BUTTON) }
        advanceTimeBy(500L); runCurrent()
        countdown.sendNow()
        assertEquals(CountdownResult.COMPLETED, job.await())
        assertEquals(500L, currentTime)
        assertEquals(CountdownState.Idle, countdown.state.value)
    }

    @Test
    fun `a second run while counting is ignored and does not restart the clock`() = runTest {
        val first = async { countdown.run(SosSource.TILE) }
        advanceTimeBy(1_000L); runCurrent()
        assertEquals(CountdownResult.ALREADY_RUNNING, countdown.run(SosSource.WIDGET))
        assertEquals(CountdownState.Counting(2, SosSource.TILE), countdown.state.value)
        advanceTimeBy(2_000L); runCurrent()
        assertEquals(CountdownResult.COMPLETED, first.await())
        assertEquals(3_000L, currentTime)
    }

    @Test
    fun `can start again after a cancel`() = runTest {
        val first = async { countdown.run(SosSource.APP_BUTTON) }
        runCurrent()
        countdown.cancel()
        assertEquals(CountdownResult.CANCELLED, first.await())
        val start = currentTime
        assertEquals(CountdownResult.COMPLETED, countdown.run(SosSource.APP_BUTTON))
        assertEquals(3_000L, currentTime - start)
    }

    @Test
    fun `cancel and send now are harmless when nothing is counting`() = runTest {
        countdown.cancel()
        countdown.sendNow()
        assertEquals(CountdownState.Idle, countdown.state.value)
        // A stale cancel must not cancel the next countdown.
        assertEquals(CountdownResult.COMPLETED, countdown.run(SosSource.TILE))
    }

    @Test
    fun `coroutine cancellation resets the state`() = runTest {
        val job = async { countdown.run(SosSource.TILE) }
        runCurrent()
        assertTrue(countdown.state.value is CountdownState.Counting)
        job.cancel()
        runCurrent()
        assertEquals(CountdownState.Idle, countdown.state.value)
    }
}
