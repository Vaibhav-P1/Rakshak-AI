package com.safety.rakshak.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Light and dark share one semantic palette ([RakshakPalette]). No dynamic (wallpaper)
 * colour: the brand and the safety-critical red must stay consistent and contrast-verified.
 * System bars are handled by edge-to-edge in MainActivity, not here.
 */
@Composable
fun RakshakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) RakshakPalette.Dark else RakshakPalette.Light
    val roles = remember(darkTheme) { scheme.toRoles() }

    val materialColors = if (darkTheme) {
        darkColorScheme(
            // Material's default primary is the text-safe red; fills use RakshakColors.Primary explicitly.
            primary = roles.dangerText, onPrimary = roles.background,
            secondary = roles.textSecondary, onSecondary = roles.background,
            tertiary = roles.successText, onTertiary = roles.background,
            background = roles.background, onBackground = roles.textPrimary,
            surface = roles.surface, onSurface = roles.textPrimary,
            surfaceVariant = roles.surfaceVariant, onSurfaceVariant = roles.textSecondary,
            outline = roles.outline, outlineVariant = roles.surfaceVariant,
            // No tonal tint: Material would otherwise mix the red primary into dialogs and menus.
            surfaceTint = roles.surface,
            error = roles.dangerText, onError = Color(RakshakPalette.Dark.background),
        )
    } else {
        lightColorScheme(
            // Material's default primary is the text-safe red; fills use RakshakColors.Primary explicitly.
            primary = roles.dangerText, onPrimary = roles.background,
            secondary = roles.textSecondary, onSecondary = roles.background,
            tertiary = roles.successText, onTertiary = roles.background,
            background = roles.background, onBackground = roles.textPrimary,
            surface = roles.surface, onSurface = roles.textPrimary,
            surfaceVariant = roles.surfaceVariant, onSurfaceVariant = roles.textSecondary,
            outline = roles.outline, outlineVariant = roles.surfaceVariant,
            // No tonal tint: Material would otherwise mix the red primary into dialogs and menus.
            surfaceTint = roles.surface,
            error = roles.dangerText, onError = Color(RakshakPalette.Light.surface),
        )
    }

    CompositionLocalProvider(LocalRakshakColors provides roles) {
        MaterialTheme(colorScheme = materialColors, typography = Typography, content = content)
    }
}
