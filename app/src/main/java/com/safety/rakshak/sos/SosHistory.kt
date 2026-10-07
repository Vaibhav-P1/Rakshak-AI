package com.safety.rakshak.sos

/**
 * A short, private record of past SOS events. Pure Kotlin. It deliberately stores no phone numbers
 * and no coordinates: only when, what triggered it, how many contacts were reached, and the
 * location status. Only real SOS runs are recorded: a cancelled countdown records nothing, and
 * test alerts never appear here.
 */
enum class HistoryResult { ALERT_SENT, PARTLY_SENT, FAILED, NOT_SENT_NO_CONTACTS, NOT_SENT_SMS_UNAVAILABLE }

data class SosHistoryEntry(
    val timeMillis: Long,
    val source: SosSource,
    val result: HistoryResult,
    val sent: Int,
    val unconfirmed: Int,
    val failed: Int,
    val location: LocationReport?,
    val markedSafe: Boolean = false,
)

fun SosOutcome.toHistoryEntry(source: SosSource, nowMillis: Long): SosHistoryEntry = when (this) {
    SosOutcome.NoContacts ->
        SosHistoryEntry(nowMillis, source, HistoryResult.NOT_SENT_NO_CONTACTS, 0, 0, 0, null)
    is SosOutcome.SmsUnavailable ->
        SosHistoryEntry(nowMillis, source, HistoryResult.NOT_SENT_SMS_UNAVAILABLE, 0, 0, 0, null)
    is SosOutcome.Dispatched -> SosHistoryEntry(
        timeMillis = nowMillis,
        source = source,
        result = when {
            !anyReached -> HistoryResult.FAILED
            failed == 0 && unconfirmed == 0 -> HistoryResult.ALERT_SENT
            else -> HistoryResult.PARTLY_SENT
        },
        sent = sent, unconfirmed = unconfirmed, failed = failed, location = location,
    )
}

/** Where the history text lives (a private, backup-excluded preferences file on Android). */
interface HistoryStorage {
    fun read(): String
    fun write(text: String)
}

/** One entry per line: time,SOURCE,RESULT,sent,unconfirmed,failed,LOCATION or -,safe(0/1). */
object SosHistoryCodec {

    fun encode(entries: List<SosHistoryEntry>): String = entries.joinToString("\n") {
        listOf(
            it.timeMillis, it.source.name, it.result.name, it.sent, it.unconfirmed, it.failed,
            it.location?.name ?: "-", if (it.markedSafe) 1 else 0,
        ).joinToString(",")
    }

    /** Skips lines that are malformed or use values this version does not know. */
    fun decode(text: String): List<SosHistoryEntry> = text.lineSequence().mapNotNull { line ->
        val f = line.split(",")
        if (f.size != 8) return@mapNotNull null
        try {
            SosHistoryEntry(
                timeMillis = f[0].toLong(),
                source = SosSource.valueOf(f[1]),
                result = HistoryResult.valueOf(f[2]),
                sent = f[3].toInt(), unconfirmed = f[4].toInt(), failed = f[5].toInt(),
                location = if (f[6] == "-") null else LocationReport.valueOf(f[6]),
                markedSafe = f[7] == "1",
            )
        } catch (e: IllegalArgumentException) {
            null
        }
    }.toList()
}

/** Newest first, at most [MAX_ENTRIES]; the oldest is dropped. */
class SosHistoryLog(private val storage: HistoryStorage) {

    fun list(): List<SosHistoryEntry> = SosHistoryCodec.decode(storage.read())

    fun add(entry: SosHistoryEntry) {
        storage.write(SosHistoryCodec.encode((listOf(entry) + list()).take(MAX_ENTRIES)))
    }

    /** "I'm safe" was sent: mark the most recent entry. */
    fun markLatestSafe() {
        val entries = list()
        if (entries.isEmpty()) return
        storage.write(SosHistoryCodec.encode(listOf(entries.first().copy(markedSafe = true)) + entries.drop(1)))
    }

    fun clear() = storage.write("")

    companion object {
        const val MAX_ENTRIES = 20
    }
}
