package com.safety.rakshak.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safety.rakshak.data.EmergencyContact
import com.safety.rakshak.data.EmergencyContactRepository
import com.safety.rakshak.data.RakshakDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: EmergencyContactRepository
    
    private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()
    
    private val _isVoiceGuardActive = MutableStateFlow(false)
    val isVoiceGuardActive: StateFlow<Boolean> = _isVoiceGuardActive.asStateFlow()
    
    private val _sosTriggered = MutableStateFlow(false)
    val sosTriggered: StateFlow<Boolean> = _sosTriggered.asStateFlow()

    init {
        val database = RakshakDatabase.getDatabase(application)
        repository = EmergencyContactRepository(database.emergencyContactDao())
        
        viewModelScope.launch {
            repository.allContacts.collect { contactList ->
                _contacts.value = contactList
            }
        }
    }

    fun addContact(name: String, phoneNumber: String, isPrimary: Boolean = false) {
        viewModelScope.launch {
            val contact = EmergencyContact(
                name = name,
                phoneNumber = phoneNumber,
                isPrimary = isPrimary
            )
            repository.insertContact(contact)
        }
    }

    fun updateContact(contact: EmergencyContact) {
        viewModelScope.launch {
            repository.updateContact(contact)
        }
    }

    fun deleteContact(contact: EmergencyContact) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }

    fun setVoiceGuardActive(active: Boolean) {
        _isVoiceGuardActive.value = active
    }

    fun triggerSOS() {
        _sosTriggered.value = true
    }

    fun resetSOSState() {
        _sosTriggered.value = false
    }
}
