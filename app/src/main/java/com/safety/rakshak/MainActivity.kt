package com.safety.rakshak

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.safety.rakshak.ui.AboutScreen
import com.safety.rakshak.ui.ContactsScreen
import com.safety.rakshak.ui.HistoryScreen
import com.safety.rakshak.ui.HomeScreen
import com.safety.rakshak.ui.OnboardingScreen
import com.safety.rakshak.ui.theme.RakshakColors
import com.safety.rakshak.ui.theme.RakshakTheme
import com.safety.rakshak.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the system bars; screens apply window insets themselves.
        enableEdgeToEdge()
        setContent {
            RakshakTheme {
                RakshakApp(viewModel = viewModel)
            }
        }
    }
}

private const val APP_PREFS = "app_prefs"
private const val KEY_ONBOARDED = "onboarded"

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_HOME = "home"
private const val ROUTE_CONTACTS = "contacts"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_HISTORY = "history"

/**
 * No permission gate: the app opens straight away and each permission is requested
 * in context (see HomeScreen and permissions/). The first launch shows a short
 * introduction with the emergency-services disclaimer and a privacy summary.
 */
@Composable
fun RakshakApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val startDestination = remember {
        val onboarded = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDED, false)
        if (onboarded) ROUTE_HOME else ROUTE_ONBOARDING
    }

    Box(Modifier.fillMaxSize().background(RakshakColors.Background)) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable(ROUTE_ONBOARDING) {
                OnboardingScreen(
                    onContinue = {
                        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
                            .edit { putBoolean(KEY_ONBOARDED, true) }
                        navController.navigate(ROUTE_HOME) { popUpTo(ROUTE_ONBOARDING) { inclusive = true } }
                    },
                    onOpenPrivacy = { navController.navigate(ROUTE_ABOUT) },
                )
            }
            composable(ROUTE_HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToContacts = { navController.navigate(ROUTE_CONTACTS) },
                    onNavigateToAbout = { navController.navigate(ROUTE_ABOUT) },
                    onNavigateToHistory = { navController.navigate(ROUTE_HISTORY) },
                )
            }
            composable(ROUTE_CONTACTS) {
                ContactsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_HISTORY) {
                HistoryScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(ROUTE_ABOUT) {
                AboutScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
