package com.safety.rakshak.sos

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Builds the SMS texts sent to emergency contacts.
 *
 * Every message uses only GSM-7 characters (no emoji) and fits in one
 * 160-character SMS part. Emoji would force UCS-2 encoding (70 chars per
 * part) and roughly quadruple the number of SMS sent per contact.
 */
class SosMessages(private val zone: ZoneId) {

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun alert(fix: GeoFix?, access: LocationAccess): String = when {
        fix != null ->
            "SOS! I need help. My location: ${mapsUrl(fix)} (${fixDetails(fix)}). Please call me or 112. -Rakshak"
        access == LocationAccess.NONE ->
            "SOS! I need help. My location is not available. Please call me or 112. -Rakshak"
        else ->
            "SOS! I need help. Finding my location, will send it next. Please call me or 112. -Rakshak"
    }

    fun locationUpdate(fix: GeoFix): String =
        "SOS update - my location: ${mapsUrl(fix)} (${fixDetails(fix)}). -Rakshak"

    fun staleLocationUpdate(fix: GeoFix): String =
        "SOS update - could not get current location. Last known (${formatAge(fix.ageMillis)} old, " +
            "may be wrong): ${mapsUrl(fix)} -Rakshak"

    fun locationUnavailableUpdate(): String =
        "SOS update - could not get my location. Please call me or 112. -Rakshak"

    fun safe(): String = "I am safe now. Please ignore my SOS alert. -Rakshak"

    private fun fixDetails(fix: GeoFix): String {
        val time = timeFormat.format(Instant.ofEpochMilli(fix.fixTimeMillis).atZone(zone))
        val accuracy = fix.accuracyMeters?.let { formatAccuracy(it) } ?: return time
        return "$accuracy, $time"
    }

    companion object {
        /** Locale.US so the decimal separator is always '.', whatever the device language. */
        fun mapsUrl(fix: GeoFix): String =
            String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", fix.latitude, fix.longitude)

        fun formatAccuracy(meters: Float): String =
            if (meters < 1000f) "+-${meters.roundToInt()}m"
            else String.format(Locale.US, "+-%.1fkm", meters / 1000f)

        fun formatAge(ageMillis: Long): String {
            val minutes = (ageMillis / 60_000L).coerceAtLeast(1L)
            return when {
                minutes < 60 -> "$minutes min"
                minutes < 48 * 60 -> "${minutes / 60} h"
                else -> "${minutes / (24 * 60)} days"
            }
        }
    }
}

/**
 * Normalizes a stored phone number for sending and de-duplication:
 * keeps digits and a leading '+'. Returns null if too short to be dialable.
 */
fun normalizePhoneNumber(raw: String): String? {
    val trimmed = raw.trim()
    val digits = trimmed.filter { it.isDigit() }
    if (digits.length < 3) return null
    return if (trimmed.startsWith("+")) "+$digits" else digits
}
