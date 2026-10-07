package com.safety.rakshak.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * The permissions Rakshak asks for, each requested in context with an explanation
 * first. Nothing here blocks the app: the user can use everything that does not
 * need a missing permission.
 */
enum class SetupItem(
    val permissions: List<String>,
    /** True if SOS cannot work without it. */
    val required: Boolean,
) {
    SMS(listOf(Manifest.permission.SEND_SMS), required = true),

    // Fine and coarse are requested together so Android 12+ shows the precise/approximate
    // choice. Either one is enough for SOS.
    LOCATION(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        required = false,
    ),
    NOTIFICATIONS(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else emptyList(),
        required = false,
    );

    fun isGranted(context: Context): Boolean = when {
        permissions.isEmpty() -> true // POST_NOTIFICATIONS before Android 13
        this == LOCATION -> permissions.any { has(context, it) }
        else -> permissions.all { has(context, it) }
    }

    private fun has(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

fun locationPrecision(context: Context): LocationPrecision = locationPrecision(
    fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED,
    coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED,
)

/** Remembers which permissions the app has already asked for (see [grantState]). */
class PermissionTracker(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("permission_requests", Context.MODE_PRIVATE)

    fun markAsked(permissions: List<String>) = prefs.edit { permissions.forEach { putBoolean(it, true) } }

    fun wasAsked(permissions: List<String>) = permissions.any { prefs.getBoolean(it, false) }
}

fun SetupItem.state(context: Context, tracker: PermissionTracker): GrantState {
    val activity = context.findActivity()
    return grantState(
        granted = isGranted(context),
        askedBefore = tracker.wasAsked(permissions),
        shouldShowRationale = activity != null && permissions.any {
            activity.shouldShowRequestPermissionRationale(it)
        },
    )
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

class PermissionRequester(
    val request: (SetupItem) -> Unit,
    val openSettings: () -> Unit,
)

/** [onResult] runs after the system dialog closes, whether or not anything was granted. */
@Composable
fun rememberPermissionRequester(onResult: () -> Unit): PermissionRequester {
    val context = LocalContext.current
    val tracker = remember { PermissionTracker(context) }
    val latestOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        latestOnResult()
    }
    return remember {
        PermissionRequester(
            request = { item ->
                if (item.permissions.isEmpty()) {
                    latestOnResult()
                } else {
                    tracker.markAsked(item.permissions)
                    launcher.launch(item.permissions.toTypedArray())
                }
            },
            openSettings = { openAppSettings(context) },
        )
    }
}
