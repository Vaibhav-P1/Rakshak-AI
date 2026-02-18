package com.safety.rakshak.data

import kotlinx.coroutines.flow.Flow

class EmergencyContactRepository(private val dao: EmergencyContactDao) {
    
    val allContacts: Flow<List<EmergencyContact>> = dao.getAllContacts()

    suspend fun insertContact(contact: EmergencyContact) {
        dao.insertContact(contact)
    }

    suspend fun updateContact(contact: EmergencyContact) {
        dao.updateContact(contact)
    }

    suspend fun deleteContact(contact: EmergencyContact) {
        dao.deleteContact(contact)
    }

    suspend fun getContactById(id: Int): EmergencyContact? {
        return dao.getContactById(id)
    }

    suspend fun getContactCount(): Int {
        return dao.getContactCount()
    }
}
