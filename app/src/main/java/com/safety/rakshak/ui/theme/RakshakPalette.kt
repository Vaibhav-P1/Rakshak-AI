package com.safety.rakshak.ui.theme

import kotlin.math.pow

/**
 * The single semantic colour system, one definition per role per mode. Pure Kotlin
 * (ARGB ints) so [ContrastTest] can verify it on the JVM. The UI takes every colour from
 * these roles through [RakshakColors]; `UiGuardsTest` fails on colour literals elsewhere.
 *
 * Rules (enforced by [checks]): all text is at least 4.5:1 against every background it is
 * allowed on, with no large-text exemption; meaningful non-text UI (icons, borders, the
 * brand mark) is at least 3:1. There is deliberately no "muted text" role.
 */
object RakshakPalette {

    /** Alpha of the tinted banner backgrounds (text in the same hue is drawn on top). */
    const val BANNER_TINT = 0.08f

    class Scheme(
        val background: Int,
        val surface: Int,
        /** Subtle fill for chips, icon buttons and inputs. */
        val surfaceVariant: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        /** SOS red as a fill (buttons, the brand mark). */
        val primary: Int,
        /** Text and icons drawn on [primary]. */
        val onPrimary: Int,
        /** Red used as text or as an icon on [background] / [surface]. */
        val dangerText: Int,
        val successText: Int,
        val warningText: Int,
        /** Borders and separators that carry meaning. */
        val outline: Int,
    ) {
        /** Every pair the UI may draw, with its minimum contrast. */
        fun checks(): List<ContrastCheck> = buildList {
            fun text(name: String, fg: Int, bg: Int, bgName: String) =
                add(ContrastCheck("$name on $bgName", fg, bg, 4.5, isText = true))
            fun graphic(name: String, fg: Int, bg: Int, bgName: String) =
                add(ContrastCheck("$name on $bgName", fg, bg, 3.0, isText = false))

            for ((bgName, bg) in listOf("background" to background, "surface" to surface)) {
                text("textPrimary", textPrimary, bg, bgName)
                text("textSecondary", textSecondary, bg, bgName)
                text("dangerText", dangerText, bg, bgName)
                text("successText", successText, bg, bgName)
                text("warningText", warningText, bg, bgName)
                graphic("primary (brand mark, fills)", primary, bg, bgName)
                graphic("outline", outline, bg, bgName)
                graphic("dangerText (icons)", dangerText, bg, bgName)
                graphic("successText (icons)", successText, bg, bgName)
                graphic("warningText (icons)", warningText, bg, bgName)
                // Banners: the same hue as text over its own 8% tint.
                for ((tintName, hue) in listOf("dangerText" to dangerText, "successText" to successText, "warningText" to warningText)) {
                    text("$tintName on tinted banner", hue, blend(hue, bg, BANNER_TINT), "$bgName tinted")
                }
            }
            text("textPrimary", textPrimary, surfaceVariant, "surfaceVariant")
            text("textSecondary", textSecondary, surfaceVariant, "surfaceVariant")
            text("onPrimary", onPrimary, primary, "primary")
            // Material's default "primary" is the text-safe red (dangerText), so default-coloured text and
            // outlined buttons are readable; content drawn on that red uses the background colour.
            text("background (Material onPrimary)", background, dangerText, "dangerText fill")
        }
    }

    val Dark = Scheme(
        background = 0xFF0A0C10.toInt(),
        surface = 0xFF13161E.toInt(),
        surfaceVariant = 0xFF1F2433.toInt(),
        textPrimary = 0xFFF0F2F8.toInt(),
        textSecondary = 0xFF9AA1B2.toInt(),
        primary = 0xFFD92336.toInt(),
        onPrimary = 0xFFFFFFFF.toInt(),
        dangerText = 0xFFFF6B6B.toInt(),
        successText = 0xFF2ECC8A.toInt(),
        warningText = 0xFFFF9500.toInt(),
        outline = 0xFF6B7388.toInt(),
    )

    val Light = Scheme(
        background = 0xFFFAFAFC.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        surfaceVariant = 0xFFEEF0F5.toInt(),
        textPrimary = 0xFF14161C.toInt(),
        textSecondary = 0xFF4B5262.toInt(),
        primary = 0xFFC81E2E.toInt(),
        onPrimary = 0xFFFFFFFF.toInt(),
        dangerText = 0xFFC81E2E.toInt(),
        successText = 0xFF0B7A4B.toInt(),
        warningText = 0xFF9A5B00.toInt(),
        outline = 0xFF858D9F.toInt(),
    )

    /** [fg] drawn over [bg] at [alpha], as an opaque colour. */
    fun blend(fg: Int, bg: Int, alpha: Float): Int {
        fun ch(shift: Int): Int {
            val f = (fg shr shift) and 0xFF
            val b = (bg shr shift) and 0xFF
            return Math.round(alpha * f + (1 - alpha) * b)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

data class ContrastCheck(val name: String, val fg: Int, val bg: Int, val min: Double, val isText: Boolean) {
    val ratio: Double get() = WcagContrast.ratio(fg, bg)
}

object WcagContrast {
    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(argb: Int): Double =
        0.2126 * channel((argb shr 16) and 0xFF) +
            0.7152 * channel((argb shr 8) and 0xFF) +
            0.0722 * channel(argb and 0xFF)

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
