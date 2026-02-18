package com.safety.rakshak.ui

import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safety.rakshak.service.SOSService
import com.safety.rakshak.service.VoiceGuardService
import com.safety.rakshak.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToContacts: () -> Unit
) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsState()
    val isVoiceGuardActive by viewModel.isVoiceGuardActive.collectAsState()
    
    var showSOSDialog by remember { mutableStateOf(false) }
    var sosCountdown by remember { mutableIntStateOf(3) }

    // Pulsing animation for SOS button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    if (showSOSDialog) {
        LaunchedEffect(sosCountdown) {
            if (sosCountdown > 0) {
                kotlinx.coroutines.delay(1000)
                sosCountdown--
            } else {
                triggerSOS(context)
                showSOSDialog = false
                sosCountdown = 3
            }
        }

        AlertDialog(
            onDismissRequest = {
                showSOSDialog = false
                sosCountdown = 3
            },
            title = { Text("Triggering SOS Alert") },
            text = { 
                Text(
                    "Emergency alert will be sent in $sosCountdown seconds...",
                    fontSize = 18.sp
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        triggerSOS(context)
                        showSOSDialog = false
                        sosCountdown = 3
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Text("Send Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSOSDialog = false
                        sosCountdown = 3
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rakshak - Safety First") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Voice Guard Toggle
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isVoiceGuardActive) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voice Guard",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isVoiceGuardActive) 
                                "Say 'Help Rakshak' to trigger SOS" 
                            else 
                                "Enable to activate voice commands",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isVoiceGuardActive,
                        onCheckedChange = { checked ->
                            viewModel.setVoiceGuardActive(checked)
                            val intent = Intent(
                                context,
                                VoiceGuardService::class.java
                            ).apply {
                                action = if (checked) {
                                    VoiceGuardService.ACTION_START_VOICE_GUARD
                                } else {
                                    VoiceGuardService.ACTION_STOP_VOICE_GUARD
                                }
                            }
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        }
                    )
                }
            }

            // SOS Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(280.dp)
                ) {
                    Button(
                        onClick = { showSOSDialog = true },
                        modifier = Modifier
                            .size(250.dp)
                            .then(
                                if (contacts.isEmpty()) Modifier
                                else Modifier.graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                            ),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red,
                            disabledContainerColor = Color.Gray
                        ),
                        enabled = contacts.isNotEmpty()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "SOS",
                                modifier = Modifier.size(80.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "SOS",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (contacts.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Add emergency contacts to enable SOS",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Emergency Contacts Summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Emergency Contacts",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onNavigateToContacts) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Manage Contacts"
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (contacts.isEmpty()) {
                        Text(
                            text = "No contacts added yet",
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    } else {
                        Text(
                            text = "${contacts.size} contact(s) configured",
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        contacts.take(3).forEach { contact ->
                            Text(
                                text = "• ${contact.name}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        if (contacts.size > 3) {
                            Text(
                                text = "• +${contacts.size - 3} more",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun triggerSOS(context: android.content.Context) {
    val intent = Intent(context, SOSService::class.java).apply {
        action = SOSService.ACTION_TRIGGER_SOS
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}
