package com.safety.rakshak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.safety.rakshak.ui.theme.RakshakColors as C

/** The standard card: surface fill, rounded corners and a subtle border (visible on the light background too). */
@Composable
fun Modifier.cardSurface(radius: Dp = 18.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(C.Surface).border(1.dp, C.SurfaceVariant, shape)
}
