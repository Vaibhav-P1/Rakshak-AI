package com.safety.rakshak.sos

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * The SOS pipeline. Reliability rules:
 *  1. The alert SMS goes out immediately. It never waits for, or depends on,
 *     location (permission, GPS state or a fix).
 *  2. A cached location goes in the alert only if it is fresh. Otherwise a fresh
 *     fix is requested in parallel and sent as a follow-up SMS. If none arrives,
 *     an old fix is sent clearly labelled with its age, or contacts are told
 *     that location is unavailable.
 *  3. Every contact's SMS result is tracked. Failures are retried once.
 *  4. Session state is persisted, so an SOS interrupted by process death is
 *     resumed (contacts not yet confirmed are re-sent) instead of lost.
 */
class SosOrchestrator(
    private val contacts: ContactsSource,
    private val location: LocationSource,
    private val sms: SmsGateway,
    private val store: SessionStore,
    private val clock: SosClock,
    private val messages: SosMessages,
    private val config: SosConfig = SosConfig(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    suspend fun run(
        source: SosSource,
        onProgress: (SosProgress) -> Unit = {},
    ): SosOutcome = coroutineScope {
        val allContacts = contacts.load()
        if (allContacts.isEmpty()) return@coroutineScope SosOutcome.NoContacts

        val numbers = allContacts.mapNotNull { normalizePhoneNumber(it.phoneNumber) }.distinct()
        val invalidCount = allContacts.count { normalizePhoneNumber(it.phoneNumber) == null }
        if (numbers.isEmpty()) {
            return@coroutineScope SosOutcome.Dispatched(0, 0, invalidCount, LocationReport.UNAVAILABLE)
        }

        val availability = sms.availability()
        if (availability != SmsAvailability.READY) {
            return@coroutineScope SosOutcome.SmsUnavailable(availability)
        }

        var session = resumeOrStart(source, numbers)
        store.save(session)

        val access = location.access()
        val alertPending = session.alertStatus.containsValue(ContactStatus.PENDING)

        // Fresh cached fix → include it in the alert itself.
        val freshFix = if (alertPending && access != LocationAccess.NONE) {
            location.safeLastKnown()?.takeIf { it.ageMillis <= config.freshLocationMaxAgeMillis }
        } else null

        val needsFollowUp = access != LocationAccess.NONE &&
            !session.followUpSent &&
            !(session.alertIncludedLocation || freshFix != null)

        // Start acquiring a fix now, in parallel with sending the alert.
        val currentFix: Deferred<GeoFix?>? =
            if (needsFollowUp) async { location.safeCurrent(config.currentLocationTimeoutMillis) } else null

        if (alertPending) {
            val alertText = messages.alert(freshFix, access)
            val pending = session.alertStatus.filterValues { it == ContactStatus.PENDING }.keys
            onProgress(SosProgress.SendingAlert(pending.size))

            session = sendAlert(session, pending, alertText)

            val failed = session.alertStatus.filterValues { it == ContactStatus.FAILED }.keys
            if (failed.isNotEmpty()) {
                delay(config.smsRetryDelayMillis)
                session = sendAlert(session, failed, alertText)
            }
            session = session.copy(
                phase = SosPhase.LOCATING,
                alertIncludedLocation = session.alertIncludedLocation || freshFix != null,
            )
            store.save(session)
        }

        onProgress(
            SosProgress.AlertDispatched(
                sent = session.count(ContactStatus.SENT),
                unconfirmed = session.count(ContactStatus.UNCONFIRMED),
                failed = session.count(ContactStatus.FAILED) + invalidCount,
            )
        )

        val report: LocationReport = when {
            session.alertIncludedLocation -> LocationReport.CURRENT
            access == LocationAccess.NONE -> LocationReport.NO_PERMISSION
            session.followUpSent -> session.locationReport ?: LocationReport.UNAVAILABLE
            session.reachedNumbers.isEmpty() -> {
                currentFix?.cancel()
                LocationReport.UNAVAILABLE
            }
            else -> {
                onProgress(SosProgress.WaitingForLocation)
                // currentFix is null only when resuming a session whose alert was already sent.
                val fix = if (currentFix != null) currentFix.await()
                else location.safeCurrent(config.currentLocationTimeoutMillis)
                val (text, followUpReport) = followUpFor(fix)
                onProgress(SosProgress.SendingLocation)
                sendToAll(session.reachedNumbers, text)
                session = session.copy(followUpSent = true, locationReport = followUpReport)
                store.save(session)
                followUpReport
            }
        }

        session = session.copy(phase = SosPhase.COMPLETED, locationReport = report)
        store.save(session)

        SosOutcome.Dispatched(
            sent = session.count(ContactStatus.SENT),
            unconfirmed = session.count(ContactStatus.UNCONFIRMED),
            failed = session.count(ContactStatus.FAILED) + invalidCount,
            location = report,
        )
    }

    /**
     * "I'm safe": tells every contact who may have received the alert to stand down.
     * Safe to call while [run] is in progress (the caller should cancel [run] first).
     */
    suspend fun sendSafeMessage(): SafeOutcome {
        val session = store.load() ?: return SafeOutcome.NoSession
        if (session.phase == SosPhase.SAFE) return SafeOutcome.AlreadySent

        val recipients = session.reachedNumbers
        if (recipients.isEmpty()) {
            store.save(session.copy(phase = SosPhase.SAFE))
            return SafeOutcome.NoOneAlerted
        }

        val availability = sms.availability()
        if (availability != SmsAvailability.READY) return SafeOutcome.SmsUnavailable(availability)

        val results = sendToAll(recipients, messages.safe())
        store.save(session.copy(phase = SosPhase.SAFE))
        return SafeOutcome.Sent(
            sent = results.count { it == SmsSendResult.Sent },
            unconfirmed = results.count { it == SmsSendResult.Unconfirmed },
            failed = results.count { it is SmsSendResult.Failed },
        )
    }

    private fun resumeOrStart(source: SosSource, numbers: List<String>): SosSession {
        val now = clock.nowMillis()
        val existing = store.load()
            ?.takeIf { it.isActive && now - it.startedAtMillis in 0..config.resumeWindowMillis }

        if (existing != null) {
            // Contacts added since the interrupted session also get the alert.
            val merged = existing.alertStatus.toMutableMap()
            numbers.forEach { merged.putIfAbsent(it, ContactStatus.PENDING) }
            return existing.copy(alertStatus = merged)
        }
        return SosSession(
            id = newId(),
            source = source,
            startedAtMillis = now,
            phase = SosPhase.ALERTING,
            alertStatus = numbers.associateWith { ContactStatus.PENDING },
        )
    }

    private suspend fun sendAlert(session: SosSession, targets: Collection<String>, text: String): SosSession {
        val results = targets.zip(sendToAll(targets.toList(), text))
        val updated = session.alertStatus.toMutableMap()
        results.forEach { (number, result) ->
            updated[number] = when (result) {
                SmsSendResult.Sent -> ContactStatus.SENT
                SmsSendResult.Unconfirmed -> ContactStatus.UNCONFIRMED
                is SmsSendResult.Failed -> ContactStatus.FAILED
            }
        }
        return session.copy(alertStatus = updated).also { store.save(it) }
    }

    private suspend fun sendToAll(numbers: List<String>, text: String): List<SmsSendResult> = coroutineScope {
        numbers.map { number ->
            async {
                try {
                    sms.send(number, text)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SmsSendResult.Failed(e.javaClass.simpleName)
                }
            }
        }.awaitAll()
    }

    private suspend fun followUpFor(fix: GeoFix?): Pair<String, LocationReport> {
        if (fix != null) return messages.locationUpdate(fix) to LocationReport.CURRENT
        val lastKnown = location.safeLastKnown()?.takeIf { it.ageMillis <= config.staleLocationMaxAgeMillis }
        return if (lastKnown != null) {
            messages.staleLocationUpdate(lastKnown) to LocationReport.LAST_KNOWN_STALE
        } else {
            messages.locationUnavailableUpdate() to LocationReport.UNAVAILABLE
        }
    }

    private suspend fun LocationSource.safeLastKnown(): GeoFix? = try {
        lastKnown()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun LocationSource.safeCurrent(timeoutMillis: Long): GeoFix? = try {
        // Extra margin so a provider that ignores its own timeout can't stall the SOS.
        withTimeoutOrNull(timeoutMillis + 5_000L) { current(timeoutMillis) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
