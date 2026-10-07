package com.safety.rakshak.sos

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SosOrchestratorTest {

    private val now = 1_700_000_100_000L
    private val alice = SosContact("Alice", "+91 98765 43210")
    private val bob = SosContact("Bob", "98765-00000")
    private val aliceNumber = "+919876543210"
    private val bobNumber = "9876500000"

    private val contacts = FakeContacts(listOf(alice, bob))
    private val location = FakeLocation()
    private val sms = FakeSms()
    private val store = FakeStore()
    private val messages = SosMessages(ZoneOffset.UTC)
    private val config = SosConfig()

    private fun orchestrator() = SosOrchestrator(
        contacts, location, sms, store, { now }, messages, config, newId = { "session-1" }
    )

    // ── Location must never block the alert ─────────────────────

    @Test
    fun `no location permission - alert is still sent to every contact`() = runTest {
        location.access = LocationAccess.NONE
        val result = orchestrator().run(SosSource.APP_BUTTON) as SosOutcome.Dispatched
        assertEquals(LocationReport.NO_PERMISSION, result.location)
        assertEquals(2, result.sent)
        assertEquals(listOf(messages.alert(null, LocationAccess.NONE)), sms.textsTo(aliceNumber))
        assertTrue(sms.textsTo(aliceNumber).single().contains("location is not available"))
        assertEquals(0, location.currentCalls)
    }

    @Test
    fun `alert is sent before waiting for a location fix`() = runTest {
        location.lastKnown = null
        location.current = fix()
        location.currentDelayMillis = 20_000L
        var alertSentAt = -1L
        orchestrator().run(SosSource.WIDGET) { progress ->
            if (progress is SosProgress.AlertDispatched) alertSentAt = currentTime
        }
        assertEquals("Alert must not wait for the 20 s location fix", 0L, alertSentAt)
        val texts = sms.textsTo(aliceNumber)
        assertEquals(2, texts.size)
        assertTrue(texts[0].contains("Finding my location"))
        assertTrue(texts[1].startsWith("SOS update - my location: https://maps.google.com/?q=12.97160,77.59460"))
    }

    @Test
    fun `fresh cached fix goes into the alert and no follow-up is sent`() = runTest {
        location.lastKnown = fix(ageMillis = 30_000L)
        val result = orchestrator().run(SosSource.APP_BUTTON) as SosOutcome.Dispatched
        assertEquals(LocationReport.CURRENT, result.location)
        val texts = sms.textsTo(bobNumber)
        assertEquals(1, texts.size)
        assertTrue(texts.single().contains("My location: https://maps.google.com/?q=12.97160,77.59460"))
        assertEquals(0, location.currentCalls)
    }

    @Test
    fun `stale cached fix is never presented as current`() = runTest {
        location.lastKnown = fix(ageMillis = 25 * 60_000L)
        location.current = fix(lat = 1.0, lon = 2.0)
        val result = orchestrator().run(SosSource.APP_BUTTON) as SosOutcome.Dispatched
        val texts = sms.textsTo(aliceNumber)
        assertTrue(texts[0].contains("Finding my location"))
        assertTrue(texts[1].contains("q=1.00000,2.00000"))
        assertEquals(LocationReport.CURRENT, result.location)
    }

    @Test
    fun `no current fix - old fix is sent labelled with its age`() = runTest {
        location.lastKnown = fix(ageMillis = 25 * 60_000L)
        location.current = null
        val result = orchestrator().run(SosSource.APP_BUTTON) as SosOutcome.Dispatched
        assertEquals(LocationReport.LAST_KNOWN_STALE, result.location)
        val followUp = sms.textsTo(aliceNumber)[1]
        assertTrue(followUp.contains("Last known (25 min old, may be wrong)"))
    }

    @Test
    fun `no fix at all - contacts are told location is unavailable`() = runTest {
        location.lastKnown = null
        location.current = null
        val result = orchestrator().run(SosSource.APP_BUTTON) as SosOutcome.Dispatched
        assertEquals(LocationReport.UNAVAILABLE, result.location)
        assertEquals(messages.locationUnavailableUpdate(), sms.textsTo(aliceNumber)[1])
    }

    @Test
    fun `location timeout is bounded`() = runTest {
        location.current = fix()
        location.currentDelayMillis = 10 * 60_000L // provider never answers in time
        val result = orchestrator().run(SosSource.APP_BUTTON) as SosOutcome.Dispatched
        assertEquals(LocationReport.UNAVAILABLE, result.location)
        assertTrue("Finished at ${currentTime}ms", currentTime <= config.currentLocationTimeoutMillis + 5_000L)
    }

    @Test
    fun `approximate-only permission still shares location`() = runTest {
        location.access = LocationAccess.APPROXIMATE
        location.lastKnown = fix(accuracy = 2_300f, ageMillis = 5_000L)
        val result = orchestrator().run(SosSource.APP_BUTTON) as SosOutcome.Dispatched
        assertEquals(LocationReport.CURRENT, result.location)
        assertTrue(sms.textsTo(aliceNumber)[0].contains("+-2.3km"))
    }

    // ── Contacts and SMS availability ───────────────────────────

    @Test
    fun `no contacts - nothing is sent`() = runTest {
        contacts.contacts = emptyList()
        assertEquals(SosOutcome.NoContacts, orchestrator().run(SosSource.APP_BUTTON))
        assertTrue(sms.sent.isEmpty())
    }

    @Test
    fun `sms unavailable - reported, nothing sent, no session started`() = runTest {
        sms.availability = SmsAvailability.NO_PERMISSION
        assertEquals(
            SosOutcome.SmsUnavailable(SmsAvailability.NO_PERMISSION),
            orchestrator().run(SosSource.APP_BUTTON)
        )
        assertTrue(sms.sent.isEmpty())
        assertEquals(null, store.session)
    }

    @Test
    fun `duplicate and unusable numbers are handled`() = runTest {
        contacts.contacts = listOf(alice, alice.copy(name = "Alice again"), SosContact("Empty", "  "))
        val result = orchestrator().run(SosSource.APP_BUTTON) as SosOutcome.Dispatched
        assertEquals(1, sms.textsTo(aliceNumber).count { it.startsWith("SOS!") })
        assertEquals(1, result.sent)
        assertEquals("Blank number counts as failed", 1, result.failed)
    }

    // ── Per-contact results and retry ───────────────────────────

    @Test
    fun `failed contact is retried once and counted accurately`() = runTest {
        location.lastKnown = fix()
        sms.script(bobNumber, SmsSendResult.Failed("no service"), SmsSendResult.Sent)
        val result = orchestrator().run(SosSource.APP_BUTTON) as SosOutcome.Dispatched
        assertEquals(2, result.sent)
        assertEquals(0, result.failed)
        assertEquals(2, sms.textsTo(bobNumber).size)
        assertEquals(1, sms.textsTo(aliceNumber).size)
    }

    @Test
    fun `all sends failing is reported as nobody reached, without follow-up`() = runTest {
        sms.script(aliceNumber, SmsSendResult.Failed("radio off"))
        sms.script(bobNumber, SmsSendResult.Failed("radio off"))
        val result = orchestrator().run(SosSource.APP_BUTTON) as SosOutcome.Dispatched
        assertFalse(result.anyReached)
        assertEquals(2, result.failed)
        assertEquals("1 attempt + 1 retry, no follow-up", 2, sms.textsTo(aliceNumber).size)
    }

    @Test
    fun `unconfirmed results are counted separately and still get the follow-up`() = runTest {
        sms.script(aliceNumber, SmsSendResult.Unconfirmed)
        location.current = fix()
        val result = orchestrator().run(SosSource.APP_BUTTON) as SosOutcome.Dispatched
        assertEquals(1, result.sent)
        assertEquals(1, result.unconfirmed)
        assertEquals(2, sms.textsTo(aliceNumber).size)
    }

    @Test
    fun `gateway exception is treated as a failure, not a crash`() = runTest {
        val throwing = object : SmsGateway {
            override fun availability() = SmsAvailability.READY
            override suspend fun send(phoneNumber: String, text: String): SmsSendResult =
                throw IllegalStateException("boom")
        }
        val result = SosOrchestrator(contacts, location, throwing, store, { now }, messages)
            .run(SosSource.APP_BUTTON) as SosOutcome.Dispatched
        assertEquals(2, result.failed)
    }

    // ── Persistence and resume ──────────────────────────────────

    @Test
    fun `session is persisted as pending before sending and completed at the end`() = runTest {
        orchestrator().run(SosSource.TILE)
        val first = store.history.first()
        assertEquals(SosPhase.ALERTING, first.phase)
        assertTrue(first.alertStatus.values.all { it == ContactStatus.PENDING })
        val last = store.session!!
        assertEquals(SosPhase.COMPLETED, last.phase)
        assertEquals(SosSource.TILE, last.source)
        assertTrue(last.alertStatus.values.all { it == ContactStatus.SENT })
    }

    @Test
    fun `an active recent session is reported as resumable, a finished or old one is not`() {
        val active = SosSession(
            id = "s", source = SosSource.WIDGET, startedAtMillis = now - 60_000L,
            phase = SosPhase.LOCATING, alertStatus = mapOf(aliceNumber to ContactStatus.SENT),
        )
        assertFalse(orchestrator().hasResumableSession())
        store.session = active
        assertTrue(orchestrator().hasResumableSession())
        store.session = active.copy(phase = SosPhase.COMPLETED)
        assertFalse(orchestrator().hasResumableSession())
        store.session = active.copy(startedAtMillis = now - config.resumeWindowMillis - 1)
        assertFalse(orchestrator().hasResumableSession())
    }

    @Test
    fun `interrupted session is resumed - only unsent contacts are alerted`() = runTest {
        store.session = SosSession(
            id = "old", source = SosSource.WIDGET, startedAtMillis = now - 60_000L,
            phase = SosPhase.ALERTING,
            alertStatus = mapOf(aliceNumber to ContactStatus.SENT, bobNumber to ContactStatus.PENDING),
        )
        location.lastKnown = fix()
        orchestrator().run(SosSource.APP_BUTTON)
        assertTrue(sms.textsTo(aliceNumber).isEmpty())
        assertEquals(1, sms.textsTo(bobNumber).size)
        assertEquals("old", store.session!!.id)
        assertEquals(SosPhase.COMPLETED, store.session!!.phase)
    }

    @Test
    fun `resumed session after alert phase only sends the location follow-up`() = runTest {
        store.session = SosSession(
            id = "old", source = SosSource.WIDGET, startedAtMillis = now - 60_000L,
            phase = SosPhase.LOCATING,
            alertStatus = mapOf(aliceNumber to ContactStatus.SENT, bobNumber to ContactStatus.FAILED),
        )
        location.current = fix()
        orchestrator().run(SosSource.APP_BUTTON)
        assertEquals(listOf(true), sms.textsTo(aliceNumber).map { it.startsWith("SOS update") })
        assertTrue("Failed contact gets no follow-up", sms.textsTo(bobNumber).isEmpty())
    }

    @Test
    fun `old or finished sessions are not resumed`() = runTest {
        store.session = SosSession(
            id = "old", source = SosSource.WIDGET, startedAtMillis = now - config.resumeWindowMillis - 1,
            phase = SosPhase.ALERTING, alertStatus = mapOf(aliceNumber to ContactStatus.SENT),
        )
        orchestrator().run(SosSource.APP_BUTTON)
        assertEquals("session-1", store.session!!.id)
        assertEquals(1, sms.textsTo(aliceNumber).count { it.startsWith("SOS!") })
    }

    // ── "I'm safe" ──────────────────────────────────────────────

    @Test
    fun `safe message goes only to contacts that may have been alerted`() = runTest {
        store.session = SosSession(
            id = "s", source = SosSource.APP_BUTTON, startedAtMillis = now, phase = SosPhase.COMPLETED,
            alertStatus = mapOf(aliceNumber to ContactStatus.UNCONFIRMED, bobNumber to ContactStatus.FAILED),
        )
        val outcome = orchestrator().sendSafeMessage()
        assertEquals(SafeOutcome.Sent(sent = 1, unconfirmed = 0, failed = 0), outcome)
        assertEquals(listOf(messages.safe()), sms.textsTo(aliceNumber))
        assertTrue(sms.textsTo(bobNumber).isEmpty())
        assertEquals(SosPhase.SAFE, store.session!!.phase)
        assertEquals(SafeOutcome.AlreadySent, orchestrator().sendSafeMessage())
    }

    @Test
    fun `safe message includes contacts still pending when the SOS was cancelled`() = runTest {
        store.session = SosSession(
            id = "s", source = SosSource.APP_BUTTON, startedAtMillis = now, phase = SosPhase.ALERTING,
            alertStatus = mapOf(aliceNumber to ContactStatus.PENDING),
        )
        assertTrue(orchestrator().sendSafeMessage() is SafeOutcome.Sent)
        assertEquals(1, sms.textsTo(aliceNumber).size)
    }

    @Test
    fun `safe without any session does nothing`() = runTest {
        assertEquals(SafeOutcome.NoSession, orchestrator().sendSafeMessage())
        assertTrue(sms.sent.isEmpty())
    }
}
