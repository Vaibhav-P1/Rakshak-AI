package com.safety.rakshak.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Shared dark-UI colors for new screens. The older screens still carry private copies
 * of these; they move here during the Phase 4 design-system pass.
 * TextMuted is for decoration only: it is below WCAG AA contrast on [Background].
 */
object RakshakColors {
    val Background = Color(0xFF0A0C10)
    val Surface = Color(0xFF13161E)
    val Stroke = Color(0xFF1F2433)
    val Red = Color(0xFFE8293A)
    val Green = Color(0xFF2ECC8A)
    val Orange = Color(0xFFFF9500)
    val TextPrimary = Color(0xFFF0F2F8)
    val TextSecondary = Color(0xFF9AA1B2)
    val TextMuted = Color(0xFF3D4455)
}
