package com.safety.rakshak.ui

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safety.rakshak.R
import com.safety.rakshak.contacts.ContactRules
import com.safety.rakshak.data.EmergencyContact
import com.safety.rakshak.ui.theme.RakshakColors as C
import com.safety.rakshak.ui.theme.RakshakPalette
import com.safety.rakshak.sos.SmsAvailability
import com.safety.rakshak.sos.TestAlertOutcome
import com.safety.rakshak.viewmodel.MainViewModel
import com.safety.rakshak.viewmodel.TestAlertUi
import kotlinx.coroutines.delay

/**
 * Manage emergency contacts. All rules (validation, duplicates, the cap of 5, the single
 * primary contact) live in ContactRules and are enforced by MainViewModel; this screen only
 * shows them and asks the ViewModel to apply changes.
 */
@Composable
fun ContactsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context  = LocalContext.current
    val contacts by viewModel.contacts.collectAsState()
    val testAlert by viewModel.testAlert.collectAsState()

    var showDialog      by rememberSaveable { mutableStateOf(false) }
    var editingId       by rememberSaveable { mutableStateOf<Int?>(null) }
    var contactName     by rememberSaveable { mutableStateOf("") }
    var contactPhone    by rememberSaveable { mutableStateOf("") }
    var attempted       by rememberSaveable { mutableStateOf(false) }
    var deleteTarget    by remember { mutableStateOf<EmergencyContact?>(null) }
    var notice          by remember { mutableStateOf<String?>(null) }

    val canAdd = ContactRules.canAdd(contacts.map { com.safety.rakshak.contacts.ContactInfo(it.id, it.name, it.phoneNumber, it.isPrimary) })

    fun openDialog(id: Int?, name: String, phone: String) {
        editingId = id; contactName = name; contactPhone = phone; attempted = false; showDialog = true
    }

    // Contact picker: the system picker returns only the chosen contact (no READ_CONTACTS needed).
    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ), null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numIdx  = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (nameIdx >= 0 && numIdx >= 0) {
                            openDialog(null, (it.getString(nameIdx) ?: "").take(ContactRules.MAX_NAME_LENGTH), it.getString(numIdx) ?: "")
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(notice) {
        if (notice != null) { delay(6000); notice = null }
    }

    if (showDialog) {
        ContactDialog(
            editing = editingId != null,
            name = contactName,
            phone = contactPhone,
            attempted = attempted,
            validation = viewModel.validateContact(contactName, contactPhone, editingId),
            onName = { contactName = it.take(ContactRules.MAX_NAME_LENGTH + 5) },
            onPhone = { contactPhone = it },
            onSave = {
                attempted = true
                viewModel.saveContact(editingId, contactName, contactPhone) { result ->
                    if (result.isValid) showDialog = false
                }
            },
            onDismiss = { showDialog = false },
        )
    }

    deleteTarget?.let { contact ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = C.Surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.contact_delete_title), color = C.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(
                        if (contact.isPrimary && contacts.size > 1) R.string.contact_delete_body_primary else R.string.contact_delete_body,
                        contact.name
                    ),
                    color = C.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteContact(contact) { promoted ->
                            if (promoted != null) notice = context.getString(R.string.contact_notice_promoted, promoted)
                        }
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = C.Primary, contentColor = C.OnPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 4.dp)
                ) { Text(stringResource(R.string.contact_delete_confirm), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTarget = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp)
                ) { Text(stringResource(R.string.action_cancel), color = C.TextSecondary) }
            }
        )
    }

    TestAlertDialogs(testAlert, onConfirm = viewModel::confirmTestAlert, onDismiss = viewModel::dismissTestAlert)

    Box(modifier = Modifier.fillMaxSize().background(C.Background)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {

            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.about_back), tint = C.TextPrimary)
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(stringResource(R.string.emergency_contacts), color = C.TextPrimary, fontSize = 22.sp,
                        fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
                    Text(
                        context.resources.getQuantityString(
                            R.plurals.contacts_count_of_max, ContactRules.MAX_CONTACTS, contacts.size, ContactRules.MAX_CONTACTS
                        ),
                        color = C.TextSecondary, fontSize = 13.sp
                    )
                }
            }

            notice?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.SuccessText.copy(alpha = RakshakPalette.BANNER_TINT))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = C.SuccessText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(message, color = C.SuccessText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (contacts.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Box(
                            modifier = Modifier.size(80.dp).clip(CircleShape).background(C.Surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, null, tint = C.Outline, modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.contacts_empty_title), color = C.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.contacts_empty_body),
                            color = C.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(C.SuccessText.copy(alpha = RakshakPalette.BANNER_TINT))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null, tint = C.SuccessText, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.contacts_banner), color = C.SuccessText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    items(contacts, key = { it.id }) { contact ->
                        ContactCard(
                            contact = contact,
                            onEdit = { openDialog(contact.id, contact.name, contact.phoneNumber) },
                            onTogglePrimary = { viewModel.setPrimary(if (contact.isPrimary) null else contact.id) },
                            onTestAlert = { viewModel.requestTestAlert(contact) },
                            onDelete = { deleteTarget = contact },
                        )
                    }
                }
            }

            // Labelled actions instead of unlabelled floating buttons.
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!canAdd) {
                    Text(
                        context.resources.getQuantityString(R.plurals.contacts_limit_note, ContactRules.MAX_CONTACTS, ContactRules.MAX_CONTACTS),
                        color = C.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(
                    onClick = { openDialog(null, "", "") },
                    enabled = canAdd,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = C.Primary, contentColor = C.OnPrimary,
                        disabledContainerColor = C.SurfaceVariant, disabledContentColor = C.TextSecondary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.contacts_add_manual), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        contactPickerLauncher.launch(
                            Intent(Intent.ACTION_PICK).apply { type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE }
                        )
                    },
                    enabled = canAdd,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.contacts_add_from_phone), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ContactDialog(
    editing: Boolean,
    name: String,
    phone: String,
    attempted: Boolean,
    validation: ContactRules.Validation,
    onName: (String) -> Unit,
    onPhone: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = C.TextPrimary,
        unfocusedTextColor = C.TextPrimary,
        focusedBorderColor = C.Primary,
        unfocusedBorderColor = C.Outline,
        errorBorderColor = C.DangerText,
        cursorColor = C.Primary,
        focusedContainerColor = C.Background,
        unfocusedContainerColor = C.Background,
        errorContainerColor = C.Background,
    )
    val issues = validation.issues
    val nameIssue = issues.firstOrNull { it is ContactRules.Issue.NameRequired || it is ContactRules.Issue.NameTooLong }
    val numberIssue = issues.firstOrNull { it is ContactRules.Issue.NumberInvalid || it is ContactRules.Issue.Duplicate }
    val limitIssue = issues.firstOrNull { it is ContactRules.Issue.LimitReached }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Surface,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                stringResource(if (editing) R.string.contact_dialog_edit_title else R.string.contact_dialog_add_title),
                color = C.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.contact_hint_not_own), color = C.TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = onName,
                    label = { Text(stringResource(R.string.contact_field_name), color = C.TextSecondary) },
                    isError = attempted && nameIssue != null,
                    supportingText = if (attempted && nameIssue != null) {
                        { Text(issueText(nameIssue), color = C.DangerText, fontSize = 12.sp) }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhone,
                    label = { Text(stringResource(R.string.contact_field_phone), color = C.TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = (attempted && numberIssue != null) || numberIssue is ContactRules.Issue.Duplicate,
                    supportingText = if ((attempted && numberIssue != null) || numberIssue is ContactRules.Issue.Duplicate) {
                        { Text(issueText(numberIssue!!), color = C.DangerText, fontSize = 12.sp) }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                validation.warnings.forEach { warning ->
                    Spacer(Modifier.height(8.dp))
                    Text(warningText(warning), color = C.WarningText, fontSize = 13.sp, lineHeight = 18.sp)
                }
                if (limitIssue != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(issueText(limitIssue), color = C.DangerText, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = C.Primary, contentColor = C.OnPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    stringResource(if (editing) R.string.contact_save_edit else R.string.contact_save_add),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp)
            ) { Text(stringResource(R.string.action_cancel), color = C.TextSecondary) }
        }
    )
}

@Composable
private fun issueText(issue: ContactRules.Issue): String = when (issue) {
    ContactRules.Issue.NameRequired -> stringResource(R.string.contact_error_name_required)
    ContactRules.Issue.NameTooLong -> LocalContext.current.resources.getQuantityString(
        R.plurals.contact_error_name_too_long, ContactRules.MAX_NAME_LENGTH, ContactRules.MAX_NAME_LENGTH
    )
    ContactRules.Issue.NumberInvalid -> stringResource(R.string.contact_error_number_invalid)
    ContactRules.Issue.LimitReached -> LocalContext.current.resources.getQuantityString(
        R.plurals.contact_error_limit, ContactRules.MAX_CONTACTS, ContactRules.MAX_CONTACTS
    )
    is ContactRules.Issue.Duplicate -> stringResource(R.string.contact_error_duplicate, issue.existingName)
}

@Composable
private fun warningText(warning: ContactRules.Warning): String = when (warning) {
    ContactRules.Warning.NoCountryCode -> stringResource(R.string.contact_warning_no_country_code)
    is ContactRules.Warning.PossibleDuplicate -> stringResource(R.string.contact_warning_possible_duplicate, warning.existingName)
}

@Composable
fun ContactCard(
    contact: EmergencyContact,
    onEdit: () -> Unit,
    onTogglePrimary: () -> Unit,
    onTestAlert: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth().cardSurface(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(C.DangerText.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = C.DangerText, fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, color = C.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(contact.phoneNumber, color = C.TextSecondary, fontSize = 13.sp)
                if (contact.isPrimary) {
                    val primaryDescription = stringResource(R.string.contact_primary_cd)
                    Row(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(C.SurfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .semantics { contentDescription = primaryDescription },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, null, tint = C.TextPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        // Text, not colour alone, says which contact is primary.
                        Text(stringResource(R.string.contact_primary_badge), color = C.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.contact_menu_more_cd, contact.name),
                        tint = C.TextSecondary
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.contact_menu_edit)) },
                        onClick = { menuOpen = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (contact.isPrimary) R.string.contact_menu_remove_primary else R.string.contact_menu_set_primary))
                        },
                        onClick = { menuOpen = false; onTogglePrimary() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.contact_menu_test_alert)) },
                        onClick = { menuOpen = false; onTestAlert() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.contact_menu_delete), color = C.DangerText) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

/**
 * Dialogs for the test alert. It is a separate flow from SOS: it reports its own result here and
 * never uses the SOS result notification, session, history or countdown.
 */
@Composable
private fun TestAlertDialogs(state: TestAlertUi, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    when (state) {
        TestAlertUi.Idle -> Unit
        is TestAlertUi.Confirm -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = C.Surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.test_alert_confirm_title), color = C.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.test_alert_confirm_body, state.contact.name, state.contact.phoneNumber),
                    color = C.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = C.Primary, contentColor = C.OnPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 4.dp)
                ) { Text(stringResource(R.string.test_alert_confirm_send), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.action_cancel), color = C.TextSecondary)
                }
            }
        )
        is TestAlertUi.Sending -> AlertDialog(
            onDismissRequest = {},
            containerColor = C.Surface,
            shape = RoundedCornerShape(28.dp),
            title = null,
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = C.DangerText, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.test_alert_sending, state.contact.name), color = C.TextPrimary, fontSize = 15.sp)
                }
            },
            confirmButton = {}
        )
        is TestAlertUi.Result -> {
            val name = state.contact.name
            val (title, body) = when (val outcome = state.outcome) {
                TestAlertOutcome.Sent ->
                    stringResource(R.string.test_alert_sent_title) to stringResource(R.string.test_alert_sent_body, name)
                TestAlertOutcome.Unconfirmed ->
                    stringResource(R.string.test_alert_unconfirmed_title) to stringResource(R.string.test_alert_unconfirmed_body, name)
                is TestAlertOutcome.Failed ->
                    stringResource(R.string.test_alert_failed_title) to stringResource(R.string.test_alert_failed_body, name, outcome.reason)
                is TestAlertOutcome.Unavailable ->
                    stringResource(R.string.test_alert_failed_title) to stringResource(
                        when (outcome.reason) {
                            SmsAvailability.NO_PERMISSION -> R.string.test_alert_unavailable_permission
                            SmsAvailability.NO_SIM -> R.string.test_alert_unavailable_sim
                            else -> R.string.test_alert_unavailable_telephony
                        }
                    )
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = C.Surface,
                shape = RoundedCornerShape(28.dp),
                title = { Text(title, color = C.TextPrimary, fontWeight = FontWeight.Bold) },
                text = { Text(body, color = C.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp) },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = C.Primary, contentColor = C.OnPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text(stringResource(R.string.test_alert_done), fontWeight = FontWeight.Bold) }
                }
            )
        }
    }
}
