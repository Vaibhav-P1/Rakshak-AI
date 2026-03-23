package com.safety.rakshak.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.safety.rakshak.service.SOSService
import com.safety.rakshak.service.VoiceGuardService
import com.safety.rakshak.viewmodel.MainViewModel

// ── Design tokens ────────────────────────────────────────────────
private val BgDark        = Color(0xFF0A0C10)
private val SurfaceCard   = Color(0xFF13161E)
private val StrokeColor   = Color(0xFF1F2433)
private val SOSRed        = Color(0xFFE8293A)
private val SOSRedGlow    = Color(0x44E8293A)
private val SOSRedGlow2   = Color(0x18E8293A)
private val AccentGreen   = Color(0xFF2ECC8A)
private val AccentOrange  = Color(0xFFFF9500)
private val TextPrimary   = Color(0xFFF0F2F8)
private val TextSecondary = Color(0xFF6B7280)
private val TextMuted     = Color(0xFF3D4455)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToContacts: () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val contacts       by viewModel.contacts.collectAsState()
    val isVoiceActive  by viewModel.isVoiceGuardActive.collectAsState()

    var showSOSDialog      by remember { mutableStateOf(false) }
    var sosCountdown       by remember { mutableIntStateOf(3) }
    var isBatteryOptimized by remember { mutableStateOf(false) }
    var isAccessibilityOn  by remember { mutableStateOf(false) }

    // ── Check battery + accessibility state ───────────────────────
    val checkStates = {
        val pm = context.getSystemService(PowerManager::class.java)
        isBatteryOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)
        isAccessibilityOn  = isAccessibilityServiceEnabled(context)
    }

    LaunchedEffect(Unit) { checkStates() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkStates()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Pulse rings ───────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "r1s"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "r1a"
    )
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1400, 300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "r2s"
    )
    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400, 300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "r2a"
    )

    // ── SOS Countdown dialog ──────────────────────────────────────
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
            onDismissRequest = { showSOSDialog = false; sosCountdown = 3 },
            containerColor   = SurfaceCard,
            shape            = RoundedCornerShape(28.dp),
            title = {
                Text("Emergency Alert", color = TextPrimary,
                    fontWeight = FontWeight.Bold, fontSize = 20.sp)
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text("$sosCountdown", fontSize = 80.sp,
                        fontWeight = FontWeight.Black, color = SOSRed, lineHeight = 80.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Sending alert in $sosCountdown second${if (sosCountdown != 1) "s" else ""}",
                        color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = { triggerSOS(context); showSOSDialog = false; sosCountdown = 3 },
                    colors  = ButtonDefaults.buttonColors(containerColor = SOSRed),
                    shape   = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("Send Now", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            },
            dismissButton = {
                TextButton(
                    onClick  = { showSOSDialog = false; sosCountdown = 3 },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) { Text("Cancel", color = TextSecondary, fontSize = 15.sp) }
            }
        )
    }

    // ── Screen ────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(BgDark)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 56.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text("Rakshak", color = TextPrimary, fontSize = 26.sp,
                        fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
                    Text("Your personal safety shield", color = TextSecondary, fontSize = 13.sp)
                }
                Box(modifier = Modifier.size(10.dp).clip(CircleShape)
                    .background(if (contacts.isNotEmpty()) AccentGreen else TextMuted))
            }

            Spacer(Modifier.height(8.dp))

            // ── Battery optimization warning ──────────────────────
            if (isBatteryOptimized && isVoiceActive) {
                WarningBanner(
                    icon    = Icons.Default.BatteryAlert,
                    message = "Disable battery optimization\nfor background detection",
                    buttonText = "Fix",
                    color   = AccentOrange,
                    onClick = {
                        context.startActivity(Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        ))
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            // ── Accessibility warning ─────────────────────────────
            if (!isAccessibilityOn) {
                WarningBanner(
                    icon       = Icons.Default.VolumeUp,
                    message    = "Enable Accessibility Service\nfor Volume key SOS trigger",
                    buttonText = "Enable",
                    color      = AccentGreen,
                    onClick    = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        )
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            // ── Voice Guard card ──────────────────────────────────
            GuardToggleCard(
                icon     = Icons.Default.Mic,
                title    = "Voice Guard",
                subtitle = if (isVoiceActive) "Say 'Help Rakshak'" else "Tap to enable",
                isActive = isVoiceActive,
                onToggle = { checked ->
                    viewModel.setVoiceGuardActive(checked)
                    val intent = Intent(context, VoiceGuardService::class.java).apply {
                        action = if (checked) VoiceGuardService.ACTION_START_VOICE_GUARD
                        else VoiceGuardService.ACTION_STOP_VOICE_GUARD
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        context.startForegroundService(intent)
                    else
                        context.startService(intent)
                }
            )

            Spacer(Modifier.height(10.dp))

            // ── Volume Guard card (status only — no toggle needed) ─
            // Accessibility service runs system-wide once enabled
            // No need to start/stop a service — it's always active
            GuardStatusCard(
                icon      = Icons.Default.VolumeUp,
                title     = "Volume Guard",
                subtitle  = if (isAccessibilityOn)
                    "Press Vol Up + Down to trigger SOS"
                else
                    "Enable accessibility service to activate",
                isActive  = isAccessibilityOn
            )

            // ── SOS Button area ───────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier.weight(1f).fillMaxWidth()
            ) {
                if (contacts.isNotEmpty()) {
                    Box(modifier = Modifier.size(220.dp)
                        .graphicsLayer { scaleX = ring2Scale; scaleY = ring2Scale; alpha = ring2Alpha }
                        .clip(CircleShape).background(SOSRedGlow2))
                    Box(modifier = Modifier.size(220.dp)
                        .graphicsLayer { scaleX = ring1Scale; scaleY = ring1Scale; alpha = ring1Alpha }
                        .clip(CircleShape).background(SOSRedGlow))
                }
                Button(
                    onClick   = { showSOSDialog = true },
                    modifier  = Modifier.size(220.dp),
                    shape     = CircleShape,
                    enabled   = contacts.isNotEmpty(),
                    colors    = ButtonDefaults.buttonColors(
                        containerColor         = SOSRed,
                        disabledContainerColor = SurfaceCard
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (contacts.isEmpty()) {
                            Icon(Icons.Default.Lock, null, tint = TextMuted,
                                modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("SOS", fontSize = 36.sp, fontWeight = FontWeight.Black,
                                color = TextMuted, letterSpacing = 4.sp)
                        } else {
                            Text("SOS", fontSize = 52.sp, fontWeight = FontWeight.Black,
                                color = Color.White, letterSpacing = 4.sp)
                            Text("TAP TO SEND", fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.7f), letterSpacing = 2.sp)
                        }
                    }
                }
            }

            // No contacts warning
            if (contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(SOSRed.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = SOSRed,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Add emergency contacts to enable SOS",
                            color = SOSRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Emergency Contacts card
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                .clip(RoundedCornerShape(18.dp)).background(SurfaceCard)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Emergency Contacts", color = TextPrimary,
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        IconButton(
                            onClick  = onNavigateToContacts,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                .background(StrokeColor)
                        ) {
                            Icon(Icons.Default.Edit, "Manage", tint = TextSecondary,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (contacts.isEmpty()) {
                        Text("No contacts added yet", color = TextMuted, fontSize = 14.sp)
                    } else {
                        Text("${contacts.size} contact${if (contacts.size > 1) "s" else ""} will receive alerts",
                            color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        contacts.take(3).forEach { contact ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)) {
                                Box(modifier = Modifier.size(30.dp).clip(CircleShape)
                                    .background(SOSRed.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center) {
                                    Text(contact.name.first().uppercaseChar().toString(),
                                        color = SOSRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(contact.name, color = TextPrimary, fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                        if (contacts.size > 3) {
                            Text("+${contacts.size - 3} more", color = TextSecondary,
                                fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, start = 40.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Warning banner ────────────────────────────────────────────────
@Composable
private fun WarningBanner(
    icon       : androidx.compose.ui.graphics.vector.ImageVector,
    message    : String,
    buttonText : String,
    color      : Color,
    onClick    : () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(color.copy(alpha = 0.10f)).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(message, color = color, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(buttonText, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// ── Guard toggle card (for Voice Guard) ───────────────────────────
@Composable
private fun GuardToggleCard(
    icon     : ImageVector,
    title    : String,
    subtitle : String,
    isActive : Boolean,
    onToggle : (Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SurfaceCard)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) AccentGreen.copy(0.15f) else StrokeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null,
                        tint     = if (isActive) AccentGreen else TextSecondary,
                        modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(subtitle, color = if (isActive) AccentGreen else TextSecondary, fontSize = 12.sp)
                }
            }
            Switch(
                checked         = isActive,
                onCheckedChange = onToggle,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor   = Color.White,
                    checkedTrackColor   = AccentGreen,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = StrokeColor
                )
            )
        }
    }
}

// ── Guard status card (for Volume Guard — no toggle needed) ───────
@Composable
private fun GuardStatusCard(
    icon     : ImageVector,
    title    : String,
    subtitle : String,
    isActive : Boolean
) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SurfaceCard)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) AccentGreen.copy(0.15f) else StrokeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null,
                        tint     = if (isActive) AccentGreen else TextSecondary,
                        modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(subtitle, color = if (isActive) AccentGreen else TextSecondary, fontSize = 12.sp)
                }
            }
            // Status indicator instead of switch
            Box(
                modifier = Modifier.size(10.dp).clip(CircleShape)
                    .background(if (isActive) AccentGreen else TextMuted)
            )
        }
    }
}

// ── Check if accessibility service is enabled ─────────────────────
fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val expectedService = "${context.packageName}/${context.packageName}.service.RakshakAccessibilityService"
    val expectedShort   = "${context.packageName}/.service.RakshakAccessibilityService"

    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    // Check both full and short package name formats
    return enabledServices.split(":")
        .map { it.trim() }
        .any { service ->
            service.equals(expectedService, ignoreCase = true) ||
                    service.equals(expectedShort,   ignoreCase = true)
        }
}

private fun triggerSOS(context: android.content.Context) {
    val intent = Intent(context, SOSService::class.java).apply {
        action = SOSService.ACTION_TRIGGER_SOS
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        context.startForegroundService(intent)
    else
        context.startService(intent)
}