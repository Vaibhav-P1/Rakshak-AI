package com.safety.rakshak.sos

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * The test alert is completely separate from SOS: [TestAlertSender] is given only an SMS gateway
 * and the message texts. It has no session store, history store, countdown or orchestrator, so it
 * cannot create a session, appear in history or start the countdown. These tests pin its behaviour.
 */
class TestAlertSenderTest {

    private val messages = SosMessages(ZoneOffset.UTC)
    private val number = "+15555550101"

    @Test
    fun `sends exactly one message to exactly the selected number`() = runTest {
        val sms = FakeSms()
        val outcome = TestAlertSender(sms, messages).send(" +1 555-555-0101 ")
        assertEquals(TestAlertOutcome.Sent, outcome)
        assertEquals(listOf(SentSms(number, messages.test())), sms.sent)
    }

    @Test
    fun `the message says it is a test and is not an SOS`() {
        val text = messages.test()
        assertTrue(text, text.contains("TEST"))
        assertTrue(text, text.contains("not an emergency"))
        assertFalse("must not look like an SOS", text.contains("SOS", ignoreCase = true))
        assertFalse(text.contains("help", ignoreCase = true))
        assertFalse("no location link in a test", text.contains("http"))
    }

    @Test
    fun `every network result maps to its own outcome`() = runTest {
        fun outcomeFor(result: SmsSendResult): TestAlertOutcome {
            val sms = FakeSms().also { it.script(number, result) }
            return kotlinx.coroutines.runBlocking { TestAlertSender(sms, messages).send(number) }
        }
        assertEquals(TestAlertOutcome.Sent, outcomeFor(SmsSendResult.Sent))
        assertEquals(TestAlertOutcome.Unconfirmed, outcomeFor(SmsSendResult.Unconfirmed))
        assertEquals(TestAlertOutcome.Failed("no service"), outcomeFor(SmsSendResult.Failed("no service")))
    }

    @Test
    fun `unavailable SMS is reported with the reason and nothing is sent`() = runTest {
        for (reason in listOf(SmsAvailability.NO_PERMISSION, SmsAvailability.NO_SIM, SmsAvailability.NO_TELEPHONY)) {
            val sms = FakeSms(availability = reason)
            assertEquals(TestAlertOutcome.Unavailable(reason), TestAlertSender(sms, messages).send(number))
            assertTrue(sms.sent.isEmpty())
        }
    }

    @Test
    fun `an unusable number is rejected without sending`() = runTest {
        val sms = FakeSms()
        assertEquals(TestAlertOutcome.Failed("invalid number"), TestAlertSender(sms, messages).send("12"))
        assertTrue(sms.sent.isEmpty())
    }

    @Test
    fun `a gateway exception becomes a failure, not a crash`() = runTest {
        val throwing = object : SmsGateway {
            override fun availability() = SmsAvailability.READY
            override suspend fun send(phoneNumber: String, text: String): SmsSendResult = throw IllegalStateException("boom")
        }
        assertEquals(TestAlertOutcome.Failed("IllegalStateException"), TestAlertSender(throwing, messages).send(number))
    }
}
