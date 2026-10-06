package com.safety.rakshak.sos

import kotlinx.coroutines.delay

class FakeContacts(var contacts: List<SosContact>) : ContactsSource {
    override suspend fun load() = contacts
}

class FakeLocation(
    var access: LocationAccess = LocationAccess.PRECISE,
    var lastKnown: GeoFix? = null,
    var current: GeoFix? = null,
    var currentDelayMillis: Long = 1_000L,
) : LocationSource {
    var currentCalls = 0
    override fun access() = access
    override suspend fun lastKnown() = lastKnown
    override suspend fun current(timeoutMillis: Long): GeoFix? {
        currentCalls++
        delay(minOf(currentDelayMillis, timeoutMillis))
        return if (currentDelayMillis <= timeoutMillis) current else null
    }
}

data class SentSms(val number: String, val text: String)

class FakeSms(
    var availability: SmsAvailability = SmsAvailability.READY,
    /** Results returned per number, in order; the last one repeats. Default: Sent. */
    val scripted: MutableMap<String, ArrayDeque<SmsSendResult>> = mutableMapOf(),
) : SmsGateway {
    val sent = mutableListOf<SentSms>()
    override fun availability() = availability
    override suspend fun send(phoneNumber: String, text: String): SmsSendResult {
        sent += SentSms(phoneNumber, text)
        val queue = scripted[phoneNumber] ?: return SmsSendResult.Sent
        return if (queue.size > 1) queue.removeFirst() else queue.first()
    }

    fun script(number: String, vararg results: SmsSendResult) {
        scripted[number] = ArrayDeque(results.toList())
    }

    fun textsTo(number: String) = sent.filter { it.number == number }.map { it.text }
}

class FakeStore(var session: SosSession? = null) : SessionStore {
    val history = mutableListOf<SosSession>()
    override fun load() = session
    override fun save(session: SosSession) {
        this.session = session
        history += session
    }
}

fun fix(
    lat: Double = 12.9716,
    lon: Double = 77.5946,
    accuracy: Float? = 15f,
    ageMillis: Long = 10_000L,
    timeMillis: Long = 1_700_000_000_000L,
) = GeoFix(lat, lon, accuracy, timeMillis, ageMillis)
