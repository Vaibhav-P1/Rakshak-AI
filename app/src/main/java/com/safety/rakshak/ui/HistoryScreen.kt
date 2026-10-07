package com.safety.rakshak.ui

import android.text.format.DateUtils
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safety.rakshak.R
import com.safety.rakshak.sos.HistoryResult
import com.safety.rakshak.sos.LocationReport
import com.safety.rakshak.sos.SosHistoryEntry
import com.safety.rakshak.sos.SosHistoryLog
import com.safety.rakshak.sos.SosSource
import com.safety.rakshak.sos.platform.SosPlatform
import com.safety.rakshak.ui.theme.RakshakColors as C

/** The last real SOS events, kept only on this phone. Test alerts and cancelled countdowns never appear. */
@Composable
fun HistoryScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val log = remember { SosPlatform.history(context) }
    var entries by remember { mutableStateOf(log.list()) }
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = C.Surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.history_clear_title), color = C.TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.history_clear_body), color = C.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = { log.clear(); entries = log.list(); confirmClear = false },
                    colors = ButtonDefaults.buttonColors(containerColor = C.Primary, contentColor = C.OnPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 4.dp)
                ) { Text(stringResource(R.string.history_clear_confirm), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmClear = false },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp)
                ) { Text(stringResource(R.string.action_cancel), color = C.TextSecondary) }
            }
        )
    }

    Box(Modifier.fillMaxSize().background(C.Background)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.padding(top = 8.dp, start = 8.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.about_back), tint = C.TextPrimary)
                }
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.history_title), color = C.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            Text(
                stringResource(R.string.history_subtitle, SosHistoryLog.MAX_ENTRIES),
                color = C.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            if (entries.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Box(Modifier.size(80.dp).clip(CircleShape).background(C.Surface), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.History, null, tint = C.Outline, modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.history_empty_title), color = C.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.history_empty_body), color = C.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(entries) { entry -> HistoryCard(entry) }
                }
                OutlinedButton(
                    onClick = { confirmClear = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.history_clear), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: SosHistoryEntry) {
    val context = LocalContext.current
    val whenText = DateUtils.formatDateTime(
        context, entry.timeMillis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
    )
    val good = entry.result == HistoryResult.ALERT_SENT
    Column(
        Modifier.fillMaxWidth().cardSurface(16.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(entry.result.labelRes()),
            color = if (good) C.SuccessText else C.WarningText,
            fontSize = 16.sp, fontWeight = FontWeight.Bold,
        )
        Text("$whenText  ·  ${stringResource(entry.source.labelRes())}", color = C.TextSecondary, fontSize = 13.sp)
        if (entry.result == HistoryResult.ALERT_SENT || entry.result == HistoryResult.PARTLY_SENT || entry.result == HistoryResult.FAILED) {
            Text(
                stringResource(R.string.history_counts, entry.sent, entry.unconfirmed, entry.failed),
                color = C.TextPrimary, fontSize = 14.sp,
            )
            entry.location?.let { Text(stringResource(it.labelRes()), color = C.TextSecondary, fontSize = 13.sp) }
        }
        if (entry.markedSafe) Text(stringResource(R.string.history_marked_safe), color = C.SuccessText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

internal fun HistoryResult.labelRes(): Int = when (this) {
    HistoryResult.ALERT_SENT -> R.string.history_result_sent
    HistoryResult.PARTLY_SENT -> R.string.history_result_partly
    HistoryResult.FAILED -> R.string.history_result_failed
    HistoryResult.NOT_SENT_NO_CONTACTS -> R.string.history_result_no_contacts
    HistoryResult.NOT_SENT_SMS_UNAVAILABLE -> R.string.history_result_sms_unavailable
}

private fun SosSource.labelRes(): Int = when (this) {
    SosSource.APP_BUTTON -> R.string.history_source_button
    SosSource.WIDGET -> R.string.history_source_widget
    SosSource.VOLUME_KEYS -> R.string.history_source_volume
    SosSource.TILE -> R.string.history_source_tile
}

private fun LocationReport.labelRes(): Int = when (this) {
    LocationReport.CURRENT -> R.string.history_location_current
    LocationReport.LAST_KNOWN_STALE -> R.string.history_location_stale
    LocationReport.NO_PERMISSION -> R.string.history_location_no_permission
    LocationReport.UNAVAILABLE -> R.string.history_location_unavailable
}
