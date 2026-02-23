package com.safety.rakshak

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.safety.rakshak.service.VoiceGuardService
import com.safety.rakshak.ui.ContactsScreen
import com.safety.rakshak.ui.HomeScreen
import com.safety.rakshak.ui.theme.RakshakTheme
import com.safety.rakshak.viewmodel.MainViewModel

// ── Design tokens ─────────────────────────────────────────────────
private val BgDark        = Color(0xFF0A0C10)
private val SurfaceCard   = Color(0xFF13161E)
private val StrokeColor   = Color(0xFF1F2433)
private val SOSRed        = Color(0xFFE8293A)
private val AccentGreen   = Color(0xFF2ECC8A)
private val TextPrimary   = Color(0xFFF0F2F8)
private val TextSecondary = Color(0xFF6B7280)
private val TextMuted     = Color(0xFF3D4455)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val sosReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VoiceGuardService.ACTION_TRIGGER_SOS) {
                viewModel.triggerSOS()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(VoiceGuardService.ACTION_TRIGGER_SOS)
        registerReceiver(sosReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        setContent {
            RakshakTheme {
                RakshakApp(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(sosReceiver) } catch (e: Exception) { }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RakshakApp(viewModel: MainViewModel) {
    val navController = rememberNavController()

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val permissionsState = rememberMultiplePermissionsState(permissions = permissions)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        if (!permissionsState.allPermissionsGranted) {
            PermissionScreen(permissionsState = permissionsState)
        } else {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToContacts = { navController.navigate("contacts") }
                    )
                }
                composable("contacts") {
                    ContactsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(permissionsState: MultiplePermissionsState) {

    // Subtle shield pulse
    val infiniteTransition = rememberInfiniteTransition(label = "shield")
    val shieldScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ss"
    )

    val permissionItems = buildList {
        add(Triple("Location", "Share your location during emergencies", Icons.Default.LocationOn))
        add(Triple("SMS", "Send emergency alerts to your contacts", Icons.Default.Message))
        add(Triple("Microphone", "Detect voice commands like 'Help Rakshak'", Icons.Default.Mic))
        add(Triple("Contacts", "Pick emergency contacts from your phone", Icons.Default.Person))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Triple("Notifications", "Alert you when services are active", Icons.Default.Notifications))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.5f))

            // Shield icon with pulse
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer { scaleX = shieldScale; scaleY = shieldScale; alpha = 0.15f }
                        .clip(CircleShape)
                        .background(SOSRed)
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(SOSRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = SOSRed,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Setup Rakshak",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Grant these permissions so Rakshak can protect you in emergencies",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(28.dp))

            // Permission list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceCard)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                permissionItems.forEachIndexed { index, (title, desc, icon) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SOSRed.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = SOSRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                title,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                desc,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                    if (index < permissionItems.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = StrokeColor,
                            thickness = 0.5.dp
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Rationale warning
            if (permissionsState.shouldShowRationale) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SOSRed.copy(alpha = 0.08f))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = SOSRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "All permissions are required for Rakshak to work properly",
                            color = SOSRed,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Grant button
            Button(
                onClick = { permissionsState.launchMultiplePermissionRequest() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SOSRed)
            ) {
                Text(
                    "Grant Permissions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}