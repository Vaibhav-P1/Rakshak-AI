package com.safety.rakshak.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safety.rakshak.data.EmergencyContact
import com.safety.rakshak.data.EmergencyContactRepository
import com.safety.rakshak.data.RakshakDatabase
import com.safety.rakshak.widget.SOSWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: EmergencyContactRepository

    private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()

    init {
        val database = RakshakDatabase.getDatabase(application)
        repository = EmergencyContactRepository(database.emergencyContactDao())
        viewModelScope.launch {
            repository.allContacts.collect { contactList ->
                _contacts.value = contactList
                // Keep the home screen widget's contact count current.
                SOSWidget.refreshAll(application)
            }
        }
    }

    fun addContact(name: String, phoneNumber: String, isPrimary: Boolean = false) {
        viewModelScope.launch {
            repository.insertContact(
                EmergencyContact(name = name, phoneNumber = phoneNumber, isPrimary = isPrimary)
            )
        }
    }

    fun updateContact(contact: EmergencyContact) {
        viewModelScope.launch { repository.updateContact(contact) }
    }

    fun deleteContact(contact: EmergencyContact) {
        viewModelScope.launch { repository.deleteContact(contact) }
    }
}