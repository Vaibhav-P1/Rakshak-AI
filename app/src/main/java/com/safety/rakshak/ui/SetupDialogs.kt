package com.safety.rakshak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safety.rakshak.R
import com.safety.rakshak.permissions.GrantState
import com.safety.rakshak.permissions.LocationPrecision
import com.safety.rakshak.permissions.SetupItem
import com.safety.rakshak.ui.theme.RakshakColors as C

private fun SetupItem.titleRes() = when (this) {
    SetupItem.SMS -> R.string.perm_sms_title
    SetupItem.LOCATION -> R.string.perm_location_title
    SetupItem.NOTIFICATIONS -> R.string.perm_notifications_title
}

private fun SetupItem.whyRes() = when (this) {
    SetupItem.SMS -> R.string.perm_sms_why
    SetupItem.LOCATION -> R.string.perm_location_why
    SetupItem.NOTIFICATIONS -> R.string.perm_notifications_why
}

/**
 * Lists each permission with a plain explanation BEFORE the system dialog appears.
 * Used both as the first-run setup and as the place to come back to later.
 */
@Composable
fun SetupDialog(
    states: Map<SetupItem, GrantState>,
    locationPrecision: LocationPrecision,
    onAllow: (SetupItem) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Surface,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(stringResource(R.string.setup_title), color = C.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.setup_intro), color = C.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                SetupItem.entries.forEach { item ->
                    val state = states[item] ?: GrantState.CAN_ASK
                    // Fine location missing but coarse granted: offer the upgrade.
                    val approxOnly = item == SetupItem.LOCATION && state == GrantState.GRANTED &&
                        locationPrecision == LocationPrecision.APPROXIMATE
                    PermissionRow(item, state, approxOnly, onAllow, onOpenSettings)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = C.Red),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            ) { Text(stringResource(R.string.action_done), fontWeight = FontWeight.Bold) }
        },
    )
}

@Composable
private fun PermissionRow(
    item: SetupItem,
    state: GrantState,
    approxOnly: Boolean,
    onAllow: (SetupItem) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Background).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(item.titleRes()), color = C.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (item.required) R.string.setup_required_tag else R.string.setup_recommended_tag),
                color = if (item.required) C.Red else C.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(stringResource(item.whyRes()), color = C.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)

        when {
            state == GrantState.GRANTED && !approxOnly -> StatusLine(R.string.perm_status_allowed)
            state == GrantState.GRANTED && approxOnly -> {
                Text(stringResource(R.string.perm_location_approx), color = C.Orange, fontSize = 12.sp, lineHeight = 17.sp)
                OutlinedButton(onClick = { onAllow(item) }, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.action_allow_precise), color = C.Orange)
                }
            }
            state == GrantState.NEEDS_SETTINGS -> {
                Text(stringResource(R.string.perm_status_blocked), color = C.Orange, fontSize = 12.sp)
                OutlinedButton(onClick = onOpenSettings, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.action_open_settings), color = C.TextPrimary)
                }
            }
            else -> Button(
                onClick = { onAllow(item) },
                colors = ButtonDefaults.buttonColors(containerColor = C.Red),
                shape = RoundedCornerShape(12.dp),
            ) { Text(stringResource(R.string.action_allow), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun StatusLine(textRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = C.Green, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(textRes), color = C.Green, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Prominent disclosure for the accessibility service. Always shown before the user is
 * sent to Android's Accessibility settings, with an explicit confirmation.
 */
@Composable
fun VolumeGuardDisclosureDialog(
    onAgree: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Surface,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(stringResource(R.string.volume_disclosure_title), color = C.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.volume_disclosure_body),
                    color = C.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAgree,
                colors = ButtonDefaults.buttonColors(containerColor = C.Red),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            ) { Text(stringResource(R.string.volume_disclosure_agree), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.action_not_now), color = C.TextSecondary)
            }
        },
    )
}
