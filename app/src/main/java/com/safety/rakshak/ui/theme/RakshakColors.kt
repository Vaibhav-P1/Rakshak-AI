package com.safety.rakshak.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The palette as Compose colours for the current mode. Values come only from [RakshakPalette]. */
@Immutable
class RakshakColorRoles(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val primary: Color,
    val onPrimary: Color,
    val dangerText: Color,
    val successText: Color,
    val warningText: Color,
    val outline: Color,
)

fun RakshakPalette.Scheme.toRoles() = RakshakColorRoles(
    background = Color(background),
    surface = Color(surface),
    surfaceVariant = Color(surfaceVariant),
    textPrimary = Color(textPrimary),
    textSecondary = Color(textSecondary),
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    dangerText = Color(dangerText),
    successText = Color(successText),
    warningText = Color(warningText),
    outline = Color(outline),
)

val LocalRakshakColors = staticCompositionLocalOf { RakshakPalette.Dark.toRoles() }

/**
 * Use these in composables instead of colour literals (`UiGuardsTest` enforces it).
 * Text roles are for text and icons; [Primary] is the SOS red fill and the brand mark;
 * use [DangerText] for red text. Each role is contrast-checked in `ContrastTest`.
 */
object RakshakColors {
    val Background: Color @Composable get() = LocalRakshakColors.current.background
    val Surface: Color @Composable get() = LocalRakshakColors.current.surface
    val SurfaceVariant: Color @Composable get() = LocalRakshakColors.current.surfaceVariant
    val TextPrimary: Color @Composable get() = LocalRakshakColors.current.textPrimary
    val TextSecondary: Color @Composable get() = LocalRakshakColors.current.textSecondary
    val Primary: Color @Composable get() = LocalRakshakColors.current.primary
    val OnPrimary: Color @Composable get() = LocalRakshakColors.current.onPrimary
    val DangerText: Color @Composable get() = LocalRakshakColors.current.dangerText
    val SuccessText: Color @Composable get() = LocalRakshakColors.current.successText
    val WarningText: Color @Composable get() = LocalRakshakColors.current.warningText
    val Outline: Color @Composable get() = LocalRakshakColors.current.outline
}
