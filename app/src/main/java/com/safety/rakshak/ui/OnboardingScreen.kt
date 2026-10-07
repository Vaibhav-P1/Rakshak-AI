package com.safety.rakshak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RakshakLockup()
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.onboarding_title), color = C.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
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
            InfoCard(title = stringResource(R.string.disclaimer_title), accent = C.WarningText) {
                Body(stringResource(R.string.disclaimer_body))
            }

            Spacer(Modifier.height(14.dp))
            InfoCard(title = stringResource(R.string.privacy_short_title), accent = C.SuccessText) {
                Body(stringResource(R.string.privacy_short_body))
                TextButton(onClick = onOpenPrivacy, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(stringResource(R.string.onboarding_details), color = C.SuccessText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = C.Primary, contentColor = C.OnPrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(stringResource(R.string.onboarding_continue), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
internal fun InfoCard(title: String, accent: androidx.compose.ui.graphics.Color = C.TextPrimary, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().cardSurface(18.dp).padding(18.dp),
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
        Text("$number", color = C.DangerText, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(20.dp))
        Text(text, color = C.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
