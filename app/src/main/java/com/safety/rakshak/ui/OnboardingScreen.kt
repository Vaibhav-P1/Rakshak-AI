package com.safety.rakshak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safety.rakshak.R
import com.safety.rakshak.ui.theme.RakshakColors as C

/**
 * Shown once on first launch: what the app does, the emergency-services disclaimer
 * and a privacy summary. It requests no permissions; those are asked in context later.
 */
@Composable
fun OnboardingScreen(onContinue: () -> Unit, onOpenPrivacy: () -> Unit) {
    Box(Modifier.fillMaxSize().background(C.Background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(C.Red.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = C.Red, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.onboarding_title), color = C.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_subtitle),
                color = C.TextSecondary, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))
            InfoCard(title = stringResource(R.string.onboarding_how_title)) {
                Numbered(1, stringResource(R.string.onboarding_how_1))
                Numbered(2, stringResource(R.string.onboarding_how_2))
                Numbered(3, stringResource(R.string.onboarding_how_3))
            }

            Spacer(Modifier.height(14.dp))
            InfoCard(title = stringResource(R.string.disclaimer_title), accent = C.Orange) {
                Body(stringResource(R.string.disclaimer_body))
            }

            Spacer(Modifier.height(14.dp))
            InfoCard(title = stringResource(R.string.privacy_short_title), accent = C.Green) {
                Body(stringResource(R.string.privacy_short_body))
                TextButton(onClick = onOpenPrivacy, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(stringResource(R.string.onboarding_details), color = C.Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = C.Red),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(stringResource(R.string.onboarding_continue), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
internal fun InfoCard(title: String, accent: androidx.compose.ui.graphics.Color = C.TextPrimary, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        content()
    }
}

@Composable
internal fun Body(text: String) {
    Text(text, color = C.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun Numbered(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("$number", color = C.Red, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(20.dp))
        Text(text, color = C.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
