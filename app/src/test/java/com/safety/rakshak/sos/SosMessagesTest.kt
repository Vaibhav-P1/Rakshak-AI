package com.safety.rakshak.sos

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class SosMessagesTest {

    private val messages = SosMessages(ZoneOffset.UTC)
    private val originalLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(originalLocale)

    /** GSM 03.38 basic character set (characters that cost one septet). */
    private val gsm7Basic: Set<Char> = (
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
        ).toSet()

    /** Worst case: long negative coordinates, km accuracy, long age. */
    private val worstFix = fix(lat = -33.868820, lon = -151.209296, accuracy = 12_345f, ageMillis = 47 * 3_600_000L)

    private fun allMessages(): List<String> = listOf(
        messages.alert(worstFix, LocationAccess.PRECISE),
        messages.alert(null, LocationAccess.NONE),
        messages.alert(null, LocationAccess.APPROXIMATE),
        messages.locationUpdate(worstFix),
        messages.staleLocationUpdate(worstFix),
        messages.locationUnavailableUpdate(),
        messages.safe(),
        messages.test(),
    )

    @Test
    fun `every message uses only GSM-7 basic characters`() {
        allMessages().forEach { text ->
            val bad = text.filterNot { it in gsm7Basic }
            assertTrue("Non-GSM-7 chars '$bad' in: $text", bad.isEmpty())
        }
    }

    @Test
    fun `every message fits in a single SMS part`() {
        allMessages().forEach { text ->
            assertTrue("${text.length} chars: $text", text.length <= 160)
        }
    }

    @Test
    fun `coordinates use a dot decimal separator whatever the device locale`() {
        Locale.setDefault(Locale.GERMANY)
        val url = SosMessages.mapsUrl(fix(lat = 12.5, lon = 77.25))
        assertEquals("https://maps.google.com/?q=12.50000,77.25000", url)
        assertTrue(messages.alert(fix(accuracy = 2_500f), LocationAccess.PRECISE).contains("+-2.5km"))
    }

    @Test
    fun `alert with fix includes accuracy and fix time`() {
        val text = messages.alert(
            fix(accuracy = 14.6f, timeMillis = 1_700_000_000_000L), LocationAccess.PRECISE
        )
        // 1_700_000_000_000 ms = 2023-11-14 22:13:20 UTC
        assertTrue(text, text.contains("(+-15m, 22:13)"))
    }

    @Test
    fun `fix time is shown in the configured time zone`() {
        val ist = SosMessages(ZoneId.of("Asia/Kolkata"))
        val text = ist.locationUpdate(fix(timeMillis = 1_700_000_000_000L))
        assertTrue(text, text.contains("03:43"))
    }

    @Test
    fun `missing accuracy is omitted`() {
        val text = messages.locationUpdate(fix(accuracy = null, timeMillis = 1_700_000_000_000L))
        assertTrue(text, text.contains("(22:13)"))
    }

    @Test
    fun `age formatting`() {
        assertEquals("1 min", SosMessages.formatAge(5_000L))
        assertEquals("25 min", SosMessages.formatAge(25 * 60_000L))
        assertEquals("3 h", SosMessages.formatAge(3 * 3_600_000L + 59 * 60_000L))
        assertEquals("2 days", SosMessages.formatAge(50 * 3_600_000L))
    }

    @Test
    fun `phone number normalization`() {
        assertEquals("+919876543210", normalizePhoneNumber(" +91 98765-43210 "))
        assertEquals("09876543210", normalizePhoneNumber("(098) 765 43210"))
        assertEquals("112", normalizePhoneNumber("112"))
        assertNull(normalizePhoneNumber(""))
        assertNull(normalizePhoneNumber("+1"))
    }
}
