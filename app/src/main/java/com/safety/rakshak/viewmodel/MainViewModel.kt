package com.safety.rakshak.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safety.rakshak.contacts.ContactInfo
import com.safety.rakshak.contacts.ContactRules
import com.safety.rakshak.data.EmergencyContact
import com.safety.rakshak.data.EmergencyContactRepository
import com.safety.rakshak.data.RakshakDatabase
import com.safety.rakshak.sos.TestAlertOutcome
import com.safety.rakshak.sos.platform.SosPlatform
import com.safety.rakshak.widget.SOSWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** State of the test-alert flow shown by the Contacts screen. Not part of the SOS pipeline. */
sealed interface TestAlertUi {
    data object Idle : TestAlertUi
    data class Confirm(val contact: EmergencyContact) : TestAlertUi
    data class Sending(val contact: EmergencyContact) : TestAlertUi
    data class Result(val contact: EmergencyContact, val outcome: TestAlertOutcome) : TestAlertUi
}

/**
 * Holds the contact list and applies [ContactRules] to every change, so the rules (validation,
 * duplicates, the cap, primary contact) hold no matter which screen or picker the change came from.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: EmergencyContactRepository

    private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()

    private val _testAlert = MutableStateFlow<TestAlertUi>(TestAlertUi.Idle)
    val testAlert: StateFlow<TestAlertUi> = _testAlert.asStateFlow()

    init {
        val database = RakshakDatabase.getDatabase(application)
        repository = EmergencyContactRepository(database.emergencyContactDao())
        viewModelScope.launch {
            repository.allContacts.collect { contactList ->
                _contacts.value = contactList
                // Keep the home screen widget's contact count current.
                SOSWidget.refreshAll(application)
                // Defensive: if more than one contact is marked primary, keep the first.
                if (ContactRules.primariesToClear(contactList.map { it.toInfo() }).isNotEmpty()) {
                    repository.setPrimary(contactList.first { c -> c.isPrimary }.id)
                }
            }
        }
    }

    private fun EmergencyContact.toInfo() = ContactInfo(id, name, phoneNumber, isPrimary)

    /** Live validation for the add/edit dialog (uses the list currently shown). */
    fun validateContact(name: String, phone: String, editingId: Int?): ContactRules.Validation =
        ContactRules.validate(name, phone, _contacts.value.map { it.toInfo() }, editingId)

    /**
     * Adds ([editingId] null) or edits a contact. The rules are re-checked against the database
     * here, so a rejected contact is never saved. [onDone] runs on the main thread.
     */
    fun saveContact(editingId: Int?, name: String, phone: String, onDone: (ContactRules.Validation) -> Unit) {
        viewModelScope.launch {
            val existing = repository.contactsList().map { it.toInfo() }
            val validation = ContactRules.validate(name, phone, existing, editingId)
            if (validation.isValid) {
                val cleanName = name.trim()
                val number = ContactRules.normalize(phone)
                if (editingId == null) {
                    repository.insertWithPrimary(
                        EmergencyContact(name = cleanName, phoneNumber = number),
                        makePrimary = ContactRules.newContactBecomesPrimary(existing),
                    )
                } else {
                    repository.getContactById(editingId)?.let {
                        repository.updateContact(it.copy(name = cleanName, phoneNumber = number))
                    }
                }
            }
            onDone(validation)
        }
    }

    /** Makes [id] the only primary contact, or removes the primary when null. */
    fun setPrimary(id: Int?) {
        viewModelScope.launch { repository.setPrimary(id) }
    }

    /** Deletes a contact. If it was the primary, another contact takes over; [onPromoted] gets its name. */
    fun deleteContact(contact: EmergencyContact, onPromoted: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val result = ContactRules.afterDelete(repository.contactsList().map { it.toInfo() }, contact.id)
            repository.deleteAndPromote(contact, result.promotedId)
            onPromoted(result.promotedName)
        }
    }

    // ── Test alert: one labelled SMS to one chosen contact, after explicit confirmation ──

    fun requestTestAlert(contact: EmergencyContact) {
        if (_testAlert.value is TestAlertUi.Idle || _testAlert.value is TestAlertUi.Result) {
            _testAlert.value = TestAlertUi.Confirm(contact)
        }
    }

    fun confirmTestAlert() {
        val contact = (_testAlert.value as? TestAlertUi.Confirm)?.contact ?: return
        _testAlert.value = TestAlertUi.Sending(contact)
        viewModelScope.launch {
            val outcome = SosPlatform.testAlertSender(getApplication()).send(contact.phoneNumber)
            _testAlert.value = TestAlertUi.Result(contact, outcome)
        }
    }

    fun dismissTestAlert() {
        if (_testAlert.value !is TestAlertUi.Sending) _testAlert.value = TestAlertUi.Idle
    }
}
