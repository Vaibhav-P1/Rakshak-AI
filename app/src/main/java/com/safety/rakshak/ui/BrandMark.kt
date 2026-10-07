package com.safety.rakshak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safety.rakshak.R
import com.safety.rakshak.ui.theme.RakshakColors as C

/**
 * The Rakshak mark (Shield-R). One master vector (res/drawable/ic_logo_mark.xml, source in
 * docs/branding/), tinted per mode. Decorative: the app name next to it carries the meaning.
 */
@Composable
fun RakshakMark(modifier: Modifier = Modifier, tint: Color = C.Primary) {
    Icon(
        painter = painterResource(R.drawable.ic_logo_mark),
        contentDescription = null,
        tint = tint,
        modifier = modifier,
    )
}

/** Large lockup for onboarding: mark, "Rakshak" and the Devanagari name. */
@Composable
fun RakshakLockup(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RakshakMark(Modifier.size(88.dp))
        Text(stringResource(R.string.app_name), color = C.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
        Text(stringResource(R.string.brand_name_devanagari), color = C.TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
