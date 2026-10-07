package com.safety.rakshak.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * An abstract class (not an interface) so the multi-step changes below can be real Room
 * transactions: a crash halfway can never leave two primary contacts.
 */
@Dao
abstract class EmergencyContactDao {
    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, name ASC")
    abstract fun getAllContacts(): Flow<List<EmergencyContact>>

    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, name ASC")
    abstract suspend fun getAllContactsList(): List<EmergencyContact>

    @Query("SELECT * FROM emergency_contacts WHERE id = :id")
    abstract suspend fun getContactById(id: Int): EmergencyContact?

    @Query("SELECT * FROM emergency_contacts WHERE isPrimary = 1 ORDER BY name ASC LIMIT 1")
    abstract suspend fun getPrimary(): EmergencyContact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertContact(contact: EmergencyContact): Long

    @Update
    abstract suspend fun updateContact(contact: EmergencyContact)

    @Delete
    abstract suspend fun deleteContact(contact: EmergencyContact)

    @Query("DELETE FROM emergency_contacts")
    abstract suspend fun deleteAllContacts()

    @Query("SELECT COUNT(*) FROM emergency_contacts")
    abstract suspend fun getContactCount(): Int

    @Query("UPDATE emergency_contacts SET isPrimary = 0")
    protected abstract suspend fun clearPrimary()

    @Query("UPDATE emergency_contacts SET isPrimary = 1 WHERE id = :id")
    protected abstract suspend fun markPrimary(id: Int)

    /** Makes [id] the only primary contact, or clears the primary when null. Atomic. */
    @Transaction
    open suspend fun setPrimary(id: Int?) {
        clearPrimary()
        if (id != null) markPrimary(id)
    }

    /** Inserts a contact (never primary by itself) and, if asked, makes it the only primary. Atomic. */
    @Transaction
    open suspend fun insertWithPrimary(contact: EmergencyContact, makePrimary: Boolean): Long {
        val id = insertContact(contact.copy(isPrimary = false))
        if (makePrimary) setPrimary(id.toInt())
        return id
    }

    /** Deletes a contact and, if given, promotes another to primary. Atomic. */
    @Transaction
    open suspend fun deleteAndPromote(contact: EmergencyContact, promoteId: Int?) {
        deleteContact(contact)
        if (promoteId != null) setPrimary(promoteId)
    }
}
