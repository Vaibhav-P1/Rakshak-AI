package com.safety.rakshak.sos

// ── Domain models for the SOS pipeline ───────────────────────────
// Pure Kotlin: no Android types, so the pipeline is unit-testable.

/** What started the SOS. Recorded in the session for diagnostics. */
enum class SosSource { APP_BUTTON, WIDGET, VOLUME_KEYS, VOICE }

data class SosContact(val name: String, val phoneNumber: String)

/** A location fix. [ageMillis] is how old the fix was when it was read. */
data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val fixTimeMillis: Long,
    val ageMillis: Long,
)

enum class LocationAccess { NONE, APPROXIMATE, PRECISE }

enum class SmsAvailability { READY, NO_PERMISSION, NO_TELEPHONY, NO_SIM }

sealed interface SmsSendResult {
    /** The radio reported the message (all parts) as sent. Not a delivery receipt. */
    data object Sent : SmsSendResult

    /** Handed to the system, but no sent-result arrived in time. */
    data object Unconfirmed : SmsSendResult

    data class Failed(val reason: String) : SmsSendResult
}

/** Per-contact status of the initial SOS alert. */
enum class ContactStatus { PENDING, SENT, UNCONFIRMED, FAILED }

enum class SosPhase { ALERTING, LOCATING, COMPLETED, SAFE }

/** What the contacts were told about the location. */
enum class LocationReport {
    /** A fresh fix was included in the alert or sent as a follow-up. */
    CURRENT,
    /** Only an old fix was available; it was sent and labelled with its age. */
    LAST_KNOWN_STALE,
    /** Location permission not granted. */
    NO_PERMISSION,
    /** Permission granted but no fix could be obtained. */
    UNAVAILABLE,
}

/**
 * Persisted state of one SOS event. Survives process death so an
 * interrupted SOS can be resumed instead of silently lost.
 * [alertStatus] is keyed by normalized phone number.
 */
data class SosSession(
    val id: String,
    val source: SosSource,
    val startedAtMillis: Long,
    val phase: SosPhase,
    val alertStatus: Map<String, ContactStatus>,
    val alertIncludedLocation: Boolean = false,
    val followUpSent: Boolean = false,
    val locationReport: LocationReport? = null,
) {
    val isActive: Boolean get() = phase == SosPhase.ALERTING || phase == SosPhase.LOCATING

    fun count(status: ContactStatus): Int = alertStatus.values.count { it == status }

    /** Contacts that may have received the alert (or might have). */
    val reachedNumbers: List<String>
        get() = alertStatus.filterValues { it != ContactStatus.FAILED }.keys.toList()
}

sealed interface SosProgress {
    data class SendingAlert(val contactCount: Int) : SosProgress
    data class AlertDispatched(val sent: Int, val unconfirmed: Int, val failed: Int) : SosProgress
    data object WaitingForLocation : SosProgress
    data object SendingLocation : SosProgress
}

sealed interface SosOutcome {
    data object NoContacts : SosOutcome

    data class SmsUnavailable(val reason: SmsAvailability) : SosOutcome

    /**
     * The alert was attempted for every contact.
     * [failed] includes contacts whose saved number is unusable.
     */
    data class Dispatched(
        val sent: Int,
        val unconfirmed: Int,
        val failed: Int,
        val location: LocationReport,
    ) : SosOutcome {
        val anyReached: Boolean get() = sent + unconfirmed > 0
    }
}

sealed interface SafeOutcome {
    data object NoSession : SafeOutcome
    data object AlreadySent : SafeOutcome
    data object NoOneAlerted : SafeOutcome
    data class SmsUnavailable(val reason: SmsAvailability) : SafeOutcome
    data class Sent(val sent: Int, val unconfirmed: Int, val failed: Int) : SafeOutcome
}

/** Timing rules for the SOS pipeline. */
data class SosConfig(
    /** A cached fix younger than this is treated as current and goes in the first SMS. */
    val freshLocationMaxAgeMillis: Long = 2 * 60_000L,
    /** Older cached fixes are only sent as a clearly labelled "last known" fallback. */
    val staleLocationMaxAgeMillis: Long = 24 * 60 * 60_000L,
    val currentLocationTimeoutMillis: Long = 30_000L,
    /** Wait before retrying contacts whose SMS failed (e.g. momentary loss of signal). */
    val smsRetryDelayMillis: Long = 10_000L,
    /** An interrupted session younger than this is resumed instead of starting a new one. */
    val resumeWindowMillis: Long = 15 * 60_000L,
)
