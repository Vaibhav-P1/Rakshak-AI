package com.safety.rakshak.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safety.rakshak.R
import com.safety.rakshak.ui.theme.RakshakColors as C

@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember { appVersion(context) }

    Box(Modifier.fillMaxSize().background(C.Background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.padding(top = 52.dp, start = 8.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.about_back),
                        tint = C.TextPrimary,
                    )
                }
                Column {
                    Text(stringResource(R.string.about_title), color = C.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.about_version, version), color = C.TextSecondary, fontSize = 13.sp)
                }
            }

            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Section(R.string.about_section_disclaimer, R.string.about_disclaimer_body, C.Orange)
                Section(R.string.about_section_none, R.string.about_none_body, C.Green)
                Section(R.string.about_section_data, R.string.about_data_body)
                Section(R.string.about_section_location, R.string.about_location_body)
                Section(R.string.about_section_sms, R.string.about_sms_body)
                Section(R.string.about_section_triggers, R.string.about_triggers_body)
                Section(R.string.about_section_access, R.string.about_access_body)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Section(titleRes: Int, bodyRes: Int, accent: androidx.compose.ui.graphics.Color = C.TextPrimary) {
    InfoCard(title = stringResource(titleRes), accent = accent) { Body(stringResource(bodyRes)) }
}

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
} catch (e: Exception) {
    ""
}
