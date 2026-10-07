package com.safety.rakshak.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.safety.rakshak.R
import com.safety.rakshak.permissions.GrantState
import com.safety.rakshak.permissions.PermissionTracker
import com.safety.rakshak.permissions.SetupItem
import com.safety.rakshak.permissions.locationPrecision
import com.safety.rakshak.permissions.rememberPermissionRequester
import com.safety.rakshak.permissions.state
import com.safety.rakshak.service.SOSService
import com.safety.rakshak.service.SosTileService
import com.safety.rakshak.sos.CountdownState
import com.safety.rakshak.sos.SosRuntime
import com.safety.rakshak.sos.SosSource
import com.safety.rakshak.sos.platform.SosPlatform
import com.safety.rakshak.ui.theme.RakshakColors as C
import com.safety.rakshak.ui.theme.RakshakPalette
import com.safety.rakshak.viewmodel.MainViewModel

/**
 * Home. It never runs SOS logic: the SOS button calls SOSService.trigger() and the dialog
 * only observes the shared countdown (SosRuntime). The whole screen scrolls, with the SOS
 * button first, so it stays reachable on small screens, large fonts and in landscape.
 */
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToContacts: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToHistory: () -> Unit,
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val contacts       by viewModel.contacts.collectAsState()
    val countdownState by SosRuntime.countdown.state.collectAsState()

    var isAccessibilityOn    by remember { mutableStateOf(false) }
    var showVolumeDisclosure by rememberSaveable { mutableStateOf(false) }

    // ── Permissions: asked in context, never blocking the app ─────
    val tracker  = remember { PermissionTracker(context) }
    val appPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var permRefresh by remember { mutableIntStateOf(0) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var tilePrompted by remember { mutableStateOf(appPrefs.getBoolean("tile_prompted", false)) }

    val permStates = remember(permRefresh) { SetupItem.entries.associateWith { it.state(context, tracker) } }
    val precision = remember(permRefresh) { locationPrecision(context) }
    val smsGranted = permStates[SetupItem.SMS] == GrantState.GRANTED
    val needsSetup = SetupItem.entries.any { permStates[it] != GrantState.GRANTED }

    val requester = rememberPermissionRequester { permRefresh++ }
    // Refreshed with the permission state, which changes every time the screen resumes.
    // The history also changes while Home stays open (an SOS finishing), so watch the file itself.
    var historyRefresh by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val historyPrefs = context.getSharedPreferences("sos_history", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> historyRefresh++ }
        historyPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { historyPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val lastSos = remember(permRefresh, historyRefresh) { SosPlatform.history(context).list().firstOrNull() }

    val checkStates = {
        isAccessibilityOn = isAccessibilityServiceEnabled(context)
        permRefresh++ // permissions can change in system Settings while we are away
    }

    LaunchedEffect(Unit) {
        checkStates()
        // After the introduction, offer setup once so the user sees what is needed.
        if (!appPrefs.getBoolean("setup_prompted", false)) {
            appPrefs.edit { putBoolean("setup_prompted", true) }
            if (needsSetup) showSetup = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkStates()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val animationsOn = rememberAnimationsEnabled()
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring1Scale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "r1s"
    )
    val ring1Alpha by pulse.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "r1a"
    )
    val ring2Scale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1400, 300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "r2s"
    )
    val ring2Alpha by pulse.animateFloat(
        initialValue = 0.3f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400, 300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "r2a"
    )

    if (showSetup) {
        SetupDialog(
            states = permStates,
            locationPrecision = precision,
            onAllow = { requester.request(it) },
            onOpenSettings = requester.openSettings,
            onDismiss = { showSetup = false },
        )
    }

    if (showVolumeDisclosure) {
        VolumeGuardDisclosureDialog(
            onAgree = {
                showVolumeDisclosure = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismiss = { showVolumeDisclosure = false },
        )
    }

    // The countdown itself lives in SOSService / SosCountdown (the same one every trigger uses).
    // This dialog only shows it, so it also appears when the SOS was started by the widget, tile or volume keys.
    (countdownState as? CountdownState.Counting)?.let { counting ->
        AlertDialog(
            onDismissRequest = { SosRuntime.countdown.cancel() },
            containerColor   = C.Surface,
            shape            = RoundedCornerShape(28.dp),
            title = {
                Text(stringResource(R.string.countdown_dialog_title), color = C.TextPrimary,
                    fontWeight = FontWeight.Bold, fontSize = 20.sp)
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text("${counting.secondsLeft}", fontSize = 80.sp,
                        fontWeight = FontWeight.Black, color = C.DangerText, lineHeight = 80.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        context.resources.getQuantityString(
                            R.plurals.countdown_dialog_seconds, counting.secondsLeft, counting.secondsLeft
                        ),
                        color = C.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = { SosRuntime.countdown.sendNow() },
                    colors  = ButtonDefaults.buttonColors(containerColor = C.Primary, contentColor = C.OnPrimary),
                    shape   = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 4.dp)
                ) { Text(stringResource(R.string.countdown_send_now), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            },
            dismissButton = {
                TextButton(
                    onClick  = { SosRuntime.countdown.cancel() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp)
                ) { Text(stringResource(R.string.countdown_cancel), color = C.TextSecondary, fontSize = 15.sp) }
            }
        )
    }

    val config = LocalConfiguration.current
    // Shrink on short or narrow screens instead of squashing; leave room for the pulse rings.
    val sosSize: Dp = minOf(220.dp, (config.screenWidthDp - 96).dp, (config.screenHeightDp * 0.42f).dp).coerceAtLeast(140.dp)

    Box(modifier = Modifier.fillMaxSize().background(C.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: brand mark and wordmark, About
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RakshakMark(Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.app_name), color = C.TextPrimary, fontSize = 26.sp,
                            fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
                        Text(stringResource(R.string.home_tagline), color = C.TextSecondary, fontSize = 13.sp)
                    }
                }
                IconButton(onClick = onNavigateToAbout, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Info, stringResource(R.string.about_open), tint = C.TextSecondary)
                }
            }

            // SOS button first: it is never pushed off screen by anything below.
            val sosDescription = stringResource(
                if (contacts.isNotEmpty()) R.string.sos_button_description else R.string.sos_button_disabled_description
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(sosSize * 1.3f)
            ) {
                if (contacts.isNotEmpty() && animationsOn) {
                    Box(modifier = Modifier.size(sosSize)
                        .graphicsLayer { scaleX = ring2Scale; scaleY = ring2Scale; alpha = ring2Alpha }
                        .clip(CircleShape).background(C.Primary.copy(alpha = 0.18f)))
                    Box(modifier = Modifier.size(sosSize)
                        .graphicsLayer { scaleX = ring1Scale; scaleY = ring1Scale; alpha = ring1Alpha }
                        .clip(CircleShape).background(C.Primary.copy(alpha = 0.30f)))
                }
                Button(
                    // Without SMS permission SOS cannot send anything: guide the user instead.
                    onClick   = { if (smsGranted) SOSService.trigger(context, SosSource.APP_BUTTON) else showSetup = true },
                    modifier  = Modifier
                        .size(sosSize)
                        .semantics { contentDescription = sosDescription; role = Role.Button },
                    shape     = CircleShape,
                    enabled   = contacts.isNotEmpty(),
                    colors    = ButtonDefaults.buttonColors(
                        containerColor         = C.Primary,
                        contentColor           = C.OnPrimary,
                        disabledContainerColor = C.SurfaceVariant,
                        disabledContentColor   = C.TextSecondary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // The label scales with the button, not with the font scale, and never wraps.
                        val labelSize = with(LocalDensity.current) { (sosSize * 0.24f).toSp() }
                        if (contacts.isEmpty()) {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(sosSize * 0.16f))
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.sos_label), fontSize = labelSize * 0.7f, fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp, maxLines = 1, softWrap = false)
                        } else {
                            Text(stringResource(R.string.sos_label), fontSize = labelSize, fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp, maxLines = 1, softWrap = false)
                            Text(stringResource(R.string.home_sos_tap), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                letterSpacing = 2.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // Always-available way to reach emergency services (opens the dialer; no permission needed)
            OutlinedButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Default.Call, null, tint = C.TextPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.call_112), color = C.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            // The primary contact is one tap away from the dialer (no CALL_PHONE permission: ACTION_DIAL).
            contacts.firstOrNull { it.isPrimary }?.let { primary ->
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(primary.phoneNumber)))) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Call, null, tint = C.TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_call_primary, primary.name), color = C.TextPrimary,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                }
            }
            Text(stringResource(R.string.home_disclaimer), color = C.TextSecondary, fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))

            // Permissions still missing (SMS is required for SOS to work)
            if (needsSetup) {
                WarningBanner(
                    icon       = Icons.Default.Warning,
                    message    = stringResource(R.string.setup_banner),
                    buttonText = stringResource(R.string.setup_banner_action),
                    color      = C.WarningText,
                    onClick    = { showSetup = true }
                )
                Spacer(Modifier.height(10.dp))
            }

            // Volume Guard: explain what the accessibility service does BEFORE opening Settings.
            if (!isAccessibilityOn) {
                WarningBanner(
                    icon       = Icons.AutoMirrored.Filled.VolumeUp,
                    message    = stringResource(R.string.volume_banner),
                    buttonText = stringResource(R.string.volume_banner_action),
                    color      = C.SuccessText,
                    onClick    = { showVolumeDisclosure = true }
                )
                Spacer(Modifier.height(10.dp))
            }

            // Quick Settings tile: Android 13+ can add it with one tap.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !tilePrompted) {
                WarningBanner(
                    icon       = Icons.Default.Add,
                    message    = stringResource(R.string.tile_banner),
                    buttonText = stringResource(R.string.tile_banner_action),
                    color      = C.SuccessText,
                    onClick    = {
                        requestSosTile(context) {
                            appPrefs.edit { putBoolean("tile_prompted", true) }
                            tilePrompted = true
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            // Volume Guard card
            GuardStatusCard(
                icon     = Icons.AutoMirrored.Filled.VolumeUp,
                title    = stringResource(R.string.volume_card_title),
                subtitle = stringResource(if (isAccessibilityOn) R.string.volume_card_on else R.string.volume_card_off),
                isActive = isAccessibilityOn
            )
            Spacer(Modifier.height(12.dp))

            // Last real SOS (never a test alert), opening the history
            lastSos?.let { last ->
                val whenText = android.text.format.DateUtils.getRelativeTimeSpanString(
                    last.timeMillis, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
                ).toString()
                val openDescription = stringResource(R.string.home_last_sos_open)
                Row(
                    modifier = Modifier.fillMaxWidth().cardSurface(18.dp)
                        .clickable(role = Role.Button, onClickLabel = openDescription, onClick = onNavigateToHistory)
                        .heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_last_sos), color = C.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("${stringResource(last.result.labelRes())}  ·  $whenText", color = C.TextSecondary, fontSize = 13.sp)
                    }
                    Icon(Icons.Default.History, null, tint = C.TextSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(12.dp))
            }

            // No contacts warning
            if (contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(C.DangerText.copy(alpha = RakshakPalette.BANNER_TINT))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = C.DangerText, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.home_add_contacts_warning),
                            color = C.DangerText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Emergency Contacts card
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                .cardSurface(18.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.emergency_contacts), color = C.TextPrimary,
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        IconButton(
                            onClick  = onNavigateToContacts,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(
                                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(C.SurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Edit, stringResource(R.string.home_manage_contacts_cd),
                                    tint = C.TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (contacts.isEmpty()) {
                        Text(stringResource(R.string.home_contacts_none), color = C.TextSecondary, fontSize = 14.sp)
                    } else {
                        Text(
                            context.resources.getQuantityString(R.plurals.home_contacts_count, contacts.size, contacts.size),
                            color = C.TextSecondary, fontSize = 13.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        contacts.take(3).forEach { contact ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)) {
                                Box(modifier = Modifier.size(30.dp).clip(CircleShape)
                                    .background(C.DangerText.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center) {
                                    Text(contact.name.first().uppercaseChar().toString(),
                                        color = C.DangerText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(contact.name, color = C.TextPrimary, fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium)
                                if (contact.isPrimary) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.Star, null, tint = C.TextSecondary, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text(stringResource(R.string.contact_primary_badge), color = C.TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                        if (contacts.size > 3) {
                            Text(context.resources.getQuantityString(R.plurals.home_more_contacts, contacts.size - 3, contacts.size - 3), color = C.TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp, start = 40.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    // The system "remove animations" setting sets the animator scale to 0.
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

@Composable
private fun WarningBanner(
    icon       : ImageVector,
    message    : String,
    buttonText : String,
    color      : Color,
    onClick    : () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(color.copy(alpha = RakshakPalette.BANNER_TINT)).padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(message, color = color, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, lineHeight = 17.sp)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onClick,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Text(buttonText, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun GuardStatusCard(
    icon     : ImageVector,
    title    : String,
    subtitle : String,
    isActive : Boolean
) {
    Box(modifier = Modifier.fillMaxWidth().cardSurface(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) C.SuccessText.copy(alpha = 0.15f) else C.SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null,
                        tint     = if (isActive) C.SuccessText else C.TextSecondary,
                        modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, color = C.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(subtitle, color = if (isActive) C.SuccessText else C.TextSecondary, fontSize = 13.sp)
                }
            }
            Box(modifier = Modifier.size(10.dp).clip(CircleShape)
                .background(if (isActive) C.SuccessText else C.Outline))
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedFull  = "${context.packageName}/${context.packageName}.service.RakshakAccessibilityService"
    val expectedShort = "${context.packageName}/.service.RakshakAccessibilityService"
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(":").map { it.trim() }.any { service ->
        service.equals(expectedFull, ignoreCase = true) ||
                service.equals(expectedShort, ignoreCase = true)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestSosTile(context: Context, onDone: () -> Unit) {
    val statusBar = context.getSystemService(StatusBarManager::class.java)
    statusBar.requestAddTileService(
        ComponentName(context, SosTileService::class.java),
        context.getString(R.string.tile_label),
        android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_tile_sos),
        context.mainExecutor,
    ) { onDone() }
}
