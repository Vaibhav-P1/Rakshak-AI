package com.safety.rakshak.sos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SosSessionCodecTest {

    private val session = SosSession(
        id = "abc-123",
        source = SosSource.VOLUME_KEYS,
        startedAtMillis = 1_700_000_000_000L,
        phase = SosPhase.LOCATING,
        alertStatus = mapOf(
            "+919876543210" to ContactStatus.SENT,
            "9876500000" to ContactStatus.FAILED,
            "112" to ContactStatus.PENDING,
        ),
        alertIncludedLocation = false,
        followUpSent = true,
        locationReport = LocationReport.LAST_KNOWN_STALE,
    )

    @Test
    fun `round trip preserves every field`() {
        assertEquals(session, SosSessionCodec.decode(SosSessionCodec.encode(session)))
    }

    @Test
    fun `round trip without optional fields`() {
        val minimal = session.copy(locationReport = null, alertStatus = emptyMap())
        assertEquals(minimal, SosSessionCodec.decode(SosSessionCodec.encode(minimal)))
    }

    @Test
    fun `a session written by an older version with a removed source still decodes`() {
        val old = SosSessionCodec.encode(session) + ("source" to "VOICE")
        assertEquals(session.copy(source = SosSource.APP_BUTTON), SosSessionCodec.decode(old))
    }

    @Test
    fun `empty or corrupt data decodes to null instead of crashing`() {
        assertNull(SosSessionCodec.decode(emptyMap()))
        val corrupt = SosSessionCodec.encode(session) + ("phase" to "NOT_A_PHASE")
        assertNull(SosSessionCodec.decode(corrupt))
        val badNumber = SosSessionCodec.encode(session) + ("startedAt" to "yesterday")
        assertNull(SosSessionCodec.decode(badNumber))
    }
}
