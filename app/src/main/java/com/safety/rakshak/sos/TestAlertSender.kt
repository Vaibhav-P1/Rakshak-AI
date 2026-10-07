package com.safety.rakshak.sos

import kotlinx.coroutines.CancellationException

sealed interface TestAlertOutcome {
    data object Sent : TestAlertOutcome
    data object Unconfirmed : TestAlertOutcome
    data class Failed(val reason: String) : TestAlertOutcome
    data class Unavailable(val reason: SmsAvailability) : TestAlertOutcome
}

/**
 * Sends one clearly labelled test SMS to one number. It is NOT part of the SOS pipeline and is
 * kept structurally separate from it: it is given only an [SmsGateway] and the message texts, so
 * it has no way to create an SOS session, write history, start the countdown or run the
 * orchestrator. The caller shows the result in its own words (no SOS notification).
 */
class TestAlertSender(
    private val sms: SmsGateway,
    private val messages: SosMessages,
) {
    suspend fun send(phoneNumber: String): TestAlertOutcome {
        val number = normalizePhoneNumber(phoneNumber) ?: return TestAlertOutcome.Failed("invalid number")
        val availability = sms.availability()
        if (availability != SmsAvailability.READY) return TestAlertOutcome.Unavailable(availability)
        return try {
            when (val result = sms.send(number, messages.test())) {
                SmsSendResult.Sent -> TestAlertOutcome.Sent
                SmsSendResult.Unconfirmed -> TestAlertOutcome.Unconfirmed
                is SmsSendResult.Failed -> TestAlertOutcome.Failed(result.reason)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TestAlertOutcome.Failed(e.javaClass.simpleName)
        }
    }
}
