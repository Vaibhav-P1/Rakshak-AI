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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

class MainActivity : ComponentActivity() {
    
    private val viewModel: MainViewModel by viewModels()
    
    private val sosReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VoiceGuardService.ACTION_TRIGGER_SOS) {
                viewModel.triggerSOS()
            }
        }
    }

    //onCreate
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter(VoiceGuardService.ACTION_TRIGGER_SOS)

        registerReceiver(
            sosReceiver,
            filter,
            Context.RECEIVER_NOT_EXPORTED
        )

        setContent {
            RakshakTheme {
                RakshakApp(viewModel = viewModel)
            }
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(sosReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    val permissionsState = rememberMultiplePermissionsState(permissions = permissions)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (!permissionsState.allPermissionsGranted) {
            PermissionScreen(permissionsState = permissionsState)
        } else {
            NavHost(
                navController = navController,
                startDestination = "home"
            ) {
                composable("home") {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToContacts = {
                            navController.navigate("contacts")
                        }
                    )
                }
                
                composable("contacts") {
                    ContactsScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(permissionsState: MultiplePermissionsState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Rakshak needs the following permissions to protect you:",
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        PermissionItem(
            icon = "📍",
            title = "Location",
            description = "To share your location during emergencies"
        )
        
        PermissionItem(
            icon = "💬",
            title = "SMS",
            description = "To send emergency alerts to your contacts"
        )
        
        PermissionItem(
            icon = "🎤",
            title = "Microphone",
            description = "To detect voice commands like 'Help Rakshak'"
        )
        
        PermissionItem(
            icon = "📱",
            title = "Contacts",
            description = "To select emergency contacts easily"
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionItem(
                icon = "🔔",
                title = "Notifications",
                description = "To alert you when services are active"
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { permissionsState.launchMultiplePermissionRequest() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Permissions")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (permissionsState.shouldShowRationale) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "These permissions are essential for Rakshak to function properly and keep you safe.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun PermissionItem(
    icon: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
