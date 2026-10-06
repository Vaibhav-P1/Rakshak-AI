package com.safety.rakshak.sos.platform

import android.content.Context
import androidx.core.content.edit
import com.safety.rakshak.data.EmergencyContactDao
import com.safety.rakshak.data.RakshakDatabase
import com.safety.rakshak.sos.ContactsSource
import com.safety.rakshak.sos.SessionStore
import com.safety.rakshak.sos.SosClock
import com.safety.rakshak.sos.SosContact
import com.safety.rakshak.sos.SosMessages
import com.safety.rakshak.sos.SosOrchestrator
import com.safety.rakshak.sos.SosSession
import com.safety.rakshak.sos.SosSessionCodec
import java.time.ZoneId

class RoomContactsSource(private val dao: EmergencyContactDao) : ContactsSource {
    override suspend fun load(): List<SosContact> =
        dao.getAllContactsList().map { SosContact(name = it.name, phoneNumber = it.phoneNumber) }
}

/**
 * Persists the current SOS session in a private SharedPreferences file.
 * This file is deliberately excluded from backup (the backup rules include only
 * the database), so an old SOS session is never restored onto a new device.
 */
class PrefsSessionStore(context: Context) : SessionStore {

    private val prefs = context.applicationContext
        .getSharedPreferences("sos_session", Context.MODE_PRIVATE)

    override fun load(): SosSession? {
        val values = prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
        if (values.isEmpty()) return null
        return SosSessionCodec.decode(values)
    }

    override fun save(session: SosSession) {
        // Synchronous commit: state must survive if the process dies right after.
        prefs.edit(commit = true) {
            clear()
            SosSessionCodec.encode(session).forEach { (key, value) -> putString(key, value) }
        }
    }
}

object SosPlatform {
    fun orchestrator(context: Context): SosOrchestrator {
        val app = context.applicationContext
        return SosOrchestrator(
            contacts = RoomContactsSource(RakshakDatabase.getDatabase(app).emergencyContactDao()),
            location = FusedLocationSource(app),
            sms = AndroidSmsGateway(app),
            store = PrefsSessionStore(app),
            clock = SosClock { System.currentTimeMillis() },
            messages = SosMessages(ZoneId.systemDefault()),
        )
    }
}
