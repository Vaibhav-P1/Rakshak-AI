package com.safety.rakshak.sos

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.safety.rakshak.data.EmergencyContact
import com.safety.rakshak.data.RakshakDatabase
import com.safety.rakshak.service.SOSService
import com.safety.rakshak.service.SosNotifications
import com.safety.rakshak.sos.platform.AndroidSmsGateway
import com.safety.rakshak.sos.platform.FusedLocationSource
import com.safety.rakshak.sos.platform.PrefsSessionStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device checks of the Android side of the SOS pipeline.
 * Intended for an emulator: the emulator modem accepts SMS without sending
 * anything to a real phone. Uses a reserved fictional number.
 * Set a location first: `adb emu geo fix 77.5946 12.9716`.
 */
@RunWith(AndroidJUnit4::class)
class SosDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testNumber = "+15555550100"
    private val dao = RakshakDatabase.getDatabase(context).emergencyContactDao()

    @Before
    fun clearSession() = clearSessionPrefs()

    @After
    fun cleanUp() = runBlocking {
        dao.getAllContactsList().filter { it.phoneNumber == testNumber }.forEach { dao.deleteContact(it) }
        clearSessionPrefs()
    }

    @Test
    fun smsGateway_waitsForTheRadioSentResult() = runBlocking {
        assumeTrue("SEND_SMS not granted", hasSmsPermission())
        val gateway = AndroidSmsGateway(context)
        assertEquals(SmsAvailability.READY, gateway.availability())
        assertEquals(SmsSendResult.Sent, gateway.send(testNumber, "Rakshak device test - please ignore"))
    }

    // Note: FusedLocationSource is exercised through the service test below. Called
    // from a background process (like this instrumentation thread), fused location
    // returns no fix. It only works while SOSService runs as a location FGS.

    @Test
    fun prefsSessionStore_roundTrip() {
        val store = PrefsSessionStore(context)
        val session = SosSession(
            id = "device-test", source = SosSource.WIDGET, startedAtMillis = 1L,
            phase = SosPhase.COMPLETED, alertStatus = mapOf(testNumber to ContactStatus.SENT),
            locationReport = LocationReport.CURRENT,
        )
        store.save(session)
        assertEquals(session, PrefsSessionStore(context).load())
    }

    /** Full pipeline through the real service: trigger → SMS → persisted result → result notification. */
    @Test
    fun sosService_sendsAlertAndPersistsCompletedSession() {
        assumeTrue("SEND_SMS not granted", hasSmsPermission())
        val session = triggerAndAwait(SosPhase.COMPLETED)
        assertEquals(ContactStatus.SENT, session.alertStatus[testNumber])

        val access = FusedLocationSource(context).access()
        if (access == LocationAccess.NONE) {
            // The key Phase 1 fix: no location permission must not block the SMS.
            assertEquals(LocationReport.NO_PERMISSION, session.locationReport)
        } else {
            assertTrue(
                "Unexpected location report ${session.locationReport}",
                session.locationReport in setOf(
                    LocationReport.CURRENT, LocationReport.LAST_KNOWN_STALE, LocationReport.UNAVAILABLE
                )
            )
        }
        val title = awaitResultNotificationTitle()
        assertTrue("Result notification: $title", title == "SOS alert sent" || title == "SOS partly sent")
    }

    @Test
    fun safeAction_tellsAlertedContactsAndMarksSessionSafe() {
        assumeTrue("SEND_SMS not granted", hasSmsPermission())
        triggerAndAwait(SosPhase.COMPLETED)

        context.startForegroundService(
            Intent(context, SOSService::class.java).setAction(SOSService.ACTION_SAFE)
        )
        val safe = awaitPhase(SosPhase.SAFE)
        assertEquals(SosPhase.SAFE, safe.phase)
        assertEquals("\"I'm safe\" sent", awaitResultNotificationTitle("\"I'm safe\" sent"))
    }

    @Test
    fun withoutSmsPermission_userIsToldSosWasNotSent() {
        assumeFalse("Run with SEND_SMS revoked", hasSmsPermission())
        runBlocking { dao.insertContact(EmergencyContact(name = "Device Test", phoneNumber = testNumber)) }
        assertTrue(SOSService.trigger(context, SosSource.APP_BUTTON))
        assertEquals("SOS NOT SENT", awaitResultNotificationTitle("SOS NOT SENT"))
    }

    private fun triggerAndAwait(phase: SosPhase): SosSession {
        runBlocking { dao.insertContact(EmergencyContact(name = "Device Test", phoneNumber = testNumber)) }
        val contactCount = runBlocking { dao.getAllContactsList().size }
        assertTrue(SOSService.trigger(context, SosSource.APP_BUTTON))
        val session = awaitPhase(phase)
        assertEquals(contactCount, session.alertStatus.size)
        return session
    }

    private fun awaitPhase(phase: SosPhase): SosSession {
        val store = PrefsSessionStore(context)
        val deadline = System.currentTimeMillis() + 120_000L
        var session = store.load()
        while (session?.phase != phase && System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
            session = store.load()
        }
        assertNotNull("No session persisted", session)
        assertEquals(phase, session!!.phase)
        return session
    }

    private fun awaitResultNotificationTitle(expected: String? = null): String? {
        val manager = context.getSystemService(NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + 10_000L
        var title: String? = null
        while (System.currentTimeMillis() < deadline) {
            title = manager.activeNotifications
                .firstOrNull { it.id == SosNotifications.RESULT_ID }
                ?.notification?.extras?.getString(Notification.EXTRA_TITLE)
            if (title != null && (expected == null || title == expected)) return title
            Thread.sleep(250)
        }
        return title
    }

    private fun hasSmsPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun clearSessionPrefs() {
        context.getSharedPreferences("sos_session", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSystemService(NotificationManager::class.java).cancel(SosNotifications.RESULT_ID)
    }
}
