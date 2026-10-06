package com.safety.rakshak.sos

/**
 * Flat key/value encoding of [SosSession], used by the SharedPreferences
 * store. Kept free of Android/org.json types so it is unit-testable.
 */
object SosSessionCodec {

    private const val KEY_ID = "id"
    private const val KEY_SOURCE = "source"
    private const val KEY_STARTED_AT = "startedAt"
    private const val KEY_PHASE = "phase"
    private const val KEY_ALERT_HAD_LOCATION = "alertIncludedLocation"
    private const val KEY_FOLLOW_UP_SENT = "followUpSent"
    private const val KEY_LOCATION_REPORT = "locationReport"
    private const val STATUS_PREFIX = "status:"

    fun encode(session: SosSession): Map<String, String> = buildMap {
        put(KEY_ID, session.id)
        put(KEY_SOURCE, session.source.name)
        put(KEY_STARTED_AT, session.startedAtMillis.toString())
        put(KEY_PHASE, session.phase.name)
        put(KEY_ALERT_HAD_LOCATION, session.alertIncludedLocation.toString())
        put(KEY_FOLLOW_UP_SENT, session.followUpSent.toString())
        session.locationReport?.let { put(KEY_LOCATION_REPORT, it.name) }
        session.alertStatus.forEach { (number, status) -> put(STATUS_PREFIX + number, status.name) }
    }

    /** Returns null if [values] is empty or malformed (e.g. written by an older version). */
    fun decode(values: Map<String, String>): SosSession? = try {
        SosSession(
            id = values.getValue(KEY_ID),
            source = SosSource.valueOf(values.getValue(KEY_SOURCE)),
            startedAtMillis = values.getValue(KEY_STARTED_AT).toLong(),
            phase = SosPhase.valueOf(values.getValue(KEY_PHASE)),
            alertStatus = values
                .filterKeys { it.startsWith(STATUS_PREFIX) }
                .map { (key, value) -> key.removePrefix(STATUS_PREFIX) to ContactStatus.valueOf(value) }
                .toMap(),
            alertIncludedLocation = values[KEY_ALERT_HAD_LOCATION].toBoolean(),
            followUpSent = values[KEY_FOLLOW_UP_SENT].toBoolean(),
            locationReport = values[KEY_LOCATION_REPORT]?.let { LocationReport.valueOf(it) },
        )
    } catch (e: NoSuchElementException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
