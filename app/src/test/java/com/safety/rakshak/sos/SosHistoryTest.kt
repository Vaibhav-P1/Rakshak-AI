package com.safety.rakshak.sos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SosHistoryTest {

    private val now = 1_700_000_000_000L

    private fun entry(time: Long, result: HistoryResult = HistoryResult.ALERT_SENT) =
        SosHistoryEntry(time, SosSource.WIDGET, result, sent = 2, unconfirmed = 0, failed = 0, location = LocationReport.CURRENT)

    /** In-memory stand-in for the private preferences file. */
    private class MemoryStorage(var text: String = "") : HistoryStorage {
        override fun read() = text
        override fun write(text: String) { this.text = text }
    }

    // ── From an SOS outcome ─────────────────────────────────────

    @Test
    fun `a fully sent alert is recorded as sent`() {
        val e = SosOutcome.Dispatched(sent = 3, unconfirmed = 0, failed = 0, location = LocationReport.CURRENT)
            .toHistoryEntry(SosSource.VOLUME_KEYS, now)
        assertEquals(HistoryResult.ALERT_SENT, e.result)
        assertEquals(listOf(3, 0, 0), listOf(e.sent, e.unconfirmed, e.failed))
        assertEquals(LocationReport.CURRENT, e.location)
        assertEquals(SosSource.VOLUME_KEYS, e.source)
        assertEquals(now, e.timeMillis)
    }

    @Test
    fun `failures or unconfirmed results make it partly sent, and nothing reached makes it failed`() {
        assertEquals(HistoryResult.PARTLY_SENT,
            SosOutcome.Dispatched(2, 0, 1, LocationReport.UNAVAILABLE).toHistoryEntry(SosSource.TILE, now).result)
        assertEquals(HistoryResult.PARTLY_SENT,
            SosOutcome.Dispatched(2, 1, 0, LocationReport.CURRENT).toHistoryEntry(SosSource.TILE, now).result)
        assertEquals(HistoryResult.FAILED,
            SosOutcome.Dispatched(0, 0, 3, LocationReport.UNAVAILABLE).toHistoryEntry(SosSource.TILE, now).result)
    }

    @Test
    fun `no contacts and unavailable SMS are recorded as not sent`() {
        assertEquals(HistoryResult.NOT_SENT_NO_CONTACTS, SosOutcome.NoContacts.toHistoryEntry(SosSource.APP_BUTTON, now).result)
        val e = SosOutcome.SmsUnavailable(SmsAvailability.NO_SIM).toHistoryEntry(SosSource.APP_BUTTON, now)
        assertEquals(HistoryResult.NOT_SENT_SMS_UNAVAILABLE, e.result)
        assertEquals(listOf(0, 0, 0), listOf(e.sent, e.unconfirmed, e.failed))
        assertEquals(null, e.location)
    }

    // ── Storage format ──────────────────────────────────────────

    @Test
    fun `entries round trip through the codec`() {
        val list = listOf(
            entry(now),
            SosHistoryEntry(now - 1, SosSource.TILE, HistoryResult.NOT_SENT_SMS_UNAVAILABLE, 0, 0, 0, null, markedSafe = true),
            SosHistoryEntry(now - 2, SosSource.APP_BUTTON, HistoryResult.PARTLY_SENT, 1, 2, 3, LocationReport.LAST_KNOWN_STALE),
        )
        assertEquals(list, SosHistoryCodec.decode(SosHistoryCodec.encode(list)))
    }

    @Test
    fun `corrupt or unknown lines are skipped without losing the good ones`() {
        val good = SosHistoryCodec.encode(listOf(entry(now)))
        val text = "garbage\n$good\n1,NOPE,ALERT_SENT,1,0,0,-,0\nx,y\n"
        assertEquals(listOf(entry(now)), SosHistoryCodec.decode(text))
        assertTrue(SosHistoryCodec.decode("").isEmpty())
    }

    @Test
    fun `the encoded history holds no phone numbers or coordinates`() {
        val text = SosHistoryCodec.encode(listOf(entry(now)))
        assertFalse(text.contains("+"))
        assertFalse(text.contains("."))
        assertFalse(Regex("""\d{7,}""").containsMatchIn(text.replace(now.toString(), "")))
    }

    // ── The log ─────────────────────────────────────────────────

    @Test
    fun `newest first and at most 20 entries, oldest dropped`() {
        val log = SosHistoryLog(MemoryStorage())
        (1..25).forEach { log.add(entry(it.toLong())) }
        val list = log.list()
        assertEquals(SosHistoryLog.MAX_ENTRIES, list.size)
        assertEquals(25L, list.first().timeMillis)
        assertEquals(6L, list.last().timeMillis)
    }

    @Test
    fun `marking safe only touches the latest entry`() {
        val log = SosHistoryLog(MemoryStorage())
        log.add(entry(1)); log.add(entry(2))
        log.markLatestSafe()
        assertEquals(listOf(true, false), log.list().map { it.markedSafe })
    }

    @Test
    fun `marking safe on an empty log does nothing`() {
        val log = SosHistoryLog(MemoryStorage())
        log.markLatestSafe()
        assertTrue(log.list().isEmpty())
    }

    @Test
    fun `clear empties the history`() {
        val storage = MemoryStorage()
        val log = SosHistoryLog(storage)
        log.add(entry(1))
        log.clear()
        assertTrue(log.list().isEmpty())
        assertEquals("", storage.text)
    }

    @Test
    fun `history survives a new log instance over the same storage`() {
        val storage = MemoryStorage()
        SosHistoryLog(storage).add(entry(7))
        assertEquals(listOf(7L), SosHistoryLog(storage).list().map { it.timeMillis })
    }
}
