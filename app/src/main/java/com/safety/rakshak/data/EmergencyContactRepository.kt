package com.safety.rakshak.data

import kotlinx.coroutines.flow.Flow

class EmergencyContactRepository(private val dao: EmergencyContactDao) {

    val allContacts: Flow<List<EmergencyContact>> = dao.getAllContacts()

    suspend fun contactsList(): List<EmergencyContact> = dao.getAllContactsList()

    suspend fun insertWithPrimary(contact: EmergencyContact, makePrimary: Boolean) {
        dao.insertWithPrimary(contact, makePrimary)
    }

    suspend fun updateContact(contact: EmergencyContact) {
        dao.updateContact(contact)
    }

    suspend fun setPrimary(id: Int?) {
        dao.setPrimary(id)
    }

    suspend fun deleteAndPromote(contact: EmergencyContact, promoteId: Int?) {
        dao.deleteAndPromote(contact, promoteId)
    }

    suspend fun getContactById(id: Int): EmergencyContact? {
        return dao.getContactById(id)
    }
}
