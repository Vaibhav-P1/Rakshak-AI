package com.safety.rakshak.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room transactions behind the primary-contact rules, on a real SQLite in memory. */
@RunWith(AndroidJUnit4::class)
class ContactDaoTest {

    private lateinit var db: RakshakDatabase
    private lateinit var dao: EmergencyContactDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RakshakDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.emergencyContactDao()
    }

    @After
    fun tearDown() = db.close()

    private fun contact(name: String, number: String, primary: Boolean = false) =
        EmergencyContact(name = name, phoneNumber = number, isPrimary = primary)

    private suspend fun primaries() = dao.getAllContactsList().filter { it.isPrimary }.map { it.name }

    @Test
    fun setPrimary_leavesExactlyOnePrimary() = runBlocking {
        val a = dao.insertWithPrimary(contact("A", "+10000000001"), makePrimary = true).toInt()
        val b = dao.insertWithPrimary(contact("B", "+10000000002"), makePrimary = false).toInt()
        assertEquals(listOf("A"), primaries())
        dao.setPrimary(b)
        assertEquals(listOf("B"), primaries())
        dao.setPrimary(a)
        assertEquals(listOf("A"), primaries())
    }

    @Test
    fun setPrimary_nullClearsThePrimary() = runBlocking {
        dao.insertWithPrimary(contact("A", "+10000000001"), makePrimary = true)
        dao.setPrimary(null)
        assertEquals(emptyList<String>(), primaries())
        assertNull(dao.getPrimary())
    }

    @Test
    fun insertWithPrimary_neverCreatesTwoPrimaries_evenIfTheInputIsMarkedPrimary() = runBlocking {
        dao.insertWithPrimary(contact("A", "+10000000001"), makePrimary = true)
        // A caller that wrongly passes isPrimary = true must not create a second primary.
        dao.insertWithPrimary(contact("B", "+10000000002", primary = true), makePrimary = false)
        assertEquals(listOf("A"), primaries())
    }

    @Test
    fun deleteAndPromote_removesThePrimaryAndPromotesTheNext() = runBlocking {
        val a = dao.insertWithPrimary(contact("A", "+10000000001"), makePrimary = true).toInt()
        val b = dao.insertWithPrimary(contact("B", "+10000000002"), makePrimary = false).toInt()
        dao.deleteAndPromote(dao.getContactById(a)!!, promoteId = b)
        assertEquals(listOf("B"), dao.getAllContactsList().map { it.name })
        assertEquals(listOf("B"), primaries())
    }

    @Test
    fun deleteAndPromote_withNobodyToPromoteLeavesNoPrimary() = runBlocking {
        val a = dao.insertWithPrimary(contact("A", "+10000000001"), makePrimary = true).toInt()
        dao.deleteAndPromote(dao.getContactById(a)!!, promoteId = null)
        assertEquals(0, dao.getContactCount())
        assertEquals(emptyList<String>(), primaries())
    }

    @Test
    fun primaryIsListedFirst() = runBlocking {
        dao.insertWithPrimary(contact("Alice", "+10000000001"), makePrimary = false)
        dao.insertWithPrimary(contact("Zed", "+10000000002"), makePrimary = true)
        assertEquals(listOf("Zed", "Alice"), dao.getAllContactsList().map { it.name })
    }
}
