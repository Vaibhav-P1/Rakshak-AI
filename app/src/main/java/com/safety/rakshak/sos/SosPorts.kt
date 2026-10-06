package com.safety.rakshak.sos

// ── Boundaries between the SOS pipeline and the Android platform ──
// Android implementations live in sos/platform; tests use fakes.

fun interface ContactsSource {
    suspend fun load(): List<SosContact>
}

interface LocationSource {
    fun access(): LocationAccess

    /** Cached fix, possibly old. Must not throw. */
    suspend fun lastKnown(): GeoFix?

    /** Fresh fix, or null if none could be obtained within [timeoutMillis]. Must not throw. */
    suspend fun current(timeoutMillis: Long): GeoFix?
}

interface SmsGateway {
    fun availability(): SmsAvailability

    /** Sends [text] and waits for the radio's sent-result. Must not throw. */
    suspend fun send(phoneNumber: String, text: String): SmsSendResult
}

interface SessionStore {
    fun load(): SosSession?
    fun save(session: SosSession)
}

fun interface SosClock {
    fun nowMillis(): Long
}
