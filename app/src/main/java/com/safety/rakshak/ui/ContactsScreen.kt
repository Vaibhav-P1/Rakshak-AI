package com.safety.rakshak.ui

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safety.rakshak.data.EmergencyContact
import com.safety.rakshak.viewmodel.MainViewModel

// ── Design tokens (same as HomeScreen) ───────────────────────────
private val BgDark        = Color(0xFF0A0C10)
private val SurfaceCard   = Color(0xFF13161E)
private val StrokeColor   = Color(0xFF1F2433)
private val SOSRed        = Color(0xFFE8293A)
private val AccentGreen   = Color(0xFF2ECC8A)
private val TextPrimary   = Color(0xFFF0F2F8)
private val TextSecondary = Color(0xFF6B7280)
private val TextMuted     = Color(0xFF3D4455)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context  = LocalContext.current
    val contacts by viewModel.contacts.collectAsState()

    var showAddDialog    by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<EmergencyContact?>(null) }
    var contactName      by remember { mutableStateOf("") }
    var contactPhone     by remember { mutableStateOf("") }
    var nameError        by remember { mutableStateOf(false) }
    var phoneError       by remember { mutableStateOf(false) }

    // Contact picker
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
                            contactName  = it.getString(nameIdx) ?: ""
                            contactPhone = it.getString(numIdx)?.replace("[^0-9+]".toRegex(), "") ?: ""
                            showAddDialog = true
                        }
                    }
                }
            }
        }
    }

    // ── Add contact dialog ────────────────────────────────────────
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                contactName = ""; contactPhone = ""
                nameError = false; phoneError = false
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    "Add Contact",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it; nameError = false },
                        label = { Text("Name", color = TextSecondary) },
                        isError = nameError,
                        supportingText = if (nameError) {{ Text("Name is required", color = SOSRed, fontSize = 12.sp) }} else null,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = SOSRed,
                            unfocusedBorderColor = StrokeColor,
                            cursorColor = SOSRed,
                            focusedContainerColor = BgDark,
                            unfocusedContainerColor = BgDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it; phoneError = false },
                        label = { Text("Phone Number", color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = phoneError,
                        supportingText = if (phoneError) {{ Text("Enter a valid number", color = SOSRed, fontSize = 12.sp) }} else null,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = SOSRed,
                            unfocusedBorderColor = StrokeColor,
                            cursorColor = SOSRed,
                            focusedContainerColor = BgDark,
                            unfocusedContainerColor = BgDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        var valid = true
                        if (contactName.isBlank()) { nameError = true; valid = false }
                        if (contactPhone.isBlank() || contactPhone.replace("[^0-9]".toRegex(), "").length < 7) {
                            phoneError = true; valid = false
                        }
                        if (valid) {
                            viewModel.addContact(contactName.trim(), contactPhone.trim())
                            showAddDialog = false
                            contactName = ""; contactPhone = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SOSRed),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Add Contact", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        contactName = ""; contactPhone = ""
                        nameError = false; phoneError = false
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // ── Delete dialog ─────────────────────────────────────────────
    showDeleteDialog?.let { contact ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Remove Contact", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Remove ${contact.name} from your emergency contacts?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteContact(contact); showDeleteDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = SOSRed),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("Remove", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = null },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    // ── Screen ────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 52.dp, start = 8.dp, end = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        "Emergency Contacts",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        "${contacts.size} contact${if (contacts.size != 1) "s" else ""} added",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            if (contacts.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(SurfaceCard),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "No contacts yet",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Add people who will receive emergency alerts with your location",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                // Info banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentGreen.copy(alpha = 0.08f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "These contacts receive SMS + location during SOS",
                            color = AccentGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(contacts, key = { it.id }) { contact ->
                        ContactCard(
                            contact = contact,
                            onDelete = { showDeleteDialog = contact }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // FAB column
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Pick from contacts
            SmallFloatingActionButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_PICK).apply {
                        type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
                    }
                    contactPickerLauncher.launch(intent)
                },
                containerColor = SurfaceCard,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Person, "Pick from contacts", modifier = Modifier.size(20.dp))
            }

            // Manual add
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = SOSRed,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Add, "Add contact")
            }
        }
    }
}

@Composable
fun ContactCard(
    contact: EmergencyContact,
    onDelete: () -> Unit
) {
    val SOSRed        = Color(0xFFE8293A)
    val SurfaceCard   = Color(0xFF13161E)
    val TextPrimary   = Color(0xFFF0F2F8)
    val TextSecondary = Color(0xFF6B7280)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SOSRed.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    contact.name.first().uppercaseChar().toString(),
                    color = SOSRed,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    contact.phoneNumber,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SOSRed.copy(alpha = 0.08f))
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = SOSRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}