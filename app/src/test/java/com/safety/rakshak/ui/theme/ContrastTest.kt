package com.safety.rakshak.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforces the Phase 4 contrast rules on the real palette, for both modes:
 *  - all text, at every size, is at least 4.5:1 (the large-text exemption is not used);
 *  - meaningful non-text UI (icons, borders, the brand mark) is at least 3:1.
 * Every palette role declares the backgrounds it may be drawn on (see [RakshakPalette.Scheme.checks]).
 */
class ContrastTest {

    private fun failures(name: String, scheme: RakshakPalette.Scheme) =
        scheme.checks().filter { it.ratio < it.min }.map {
            "$name: ${it.name} is %.2f:1, needs ${it.min}:1".format(it.ratio)
        }

    @Test
    fun `every dark palette pair meets its minimum`() {
        val failed = failures("dark", RakshakPalette.Dark)
        assertTrue(failed.joinToString("\n"), failed.isEmpty())
    }

    @Test
    fun `every light palette pair meets its minimum`() {
        val failed = failures("light", RakshakPalette.Light)
        assertTrue(failed.joinToString("\n"), failed.isEmpty())
    }

    @Test
    fun `all text pairs use the 4_5 threshold and all graphic pairs use 3`() {
        for (scheme in listOf(RakshakPalette.Dark, RakshakPalette.Light)) {
            scheme.checks().forEach {
                val expected = if (it.isText) 4.5 else 3.0
                assertEquals("${it.name} threshold", expected, it.min, 0.0)
            }
        }
    }

    @Test
    fun `white on the SOS red is readable text, not just a large label`() {
        // Regression: the old red #E8293A gave white text only 4.37:1.
        assertTrue(WcagContrast.ratio(0xFFFFFFFF.toInt(), RakshakPalette.Dark.primary) >= 4.5)
        assertTrue(WcagContrast.ratio(0xFFFFFFFF.toInt(), RakshakPalette.Light.primary) >= 4.5)
    }

    @Test
    fun `the contrast formula matches known WCAG values`() {
        assertEquals(21.0, WcagContrast.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, WcagContrast.ratio(0xFF777777.toInt(), 0xFF777777.toInt()), 0.001)
        // #767676 on white is the classic 4.54:1 boundary colour.
        assertEquals(4.54, WcagContrast.ratio(0xFF767676.toInt(), 0xFFFFFFFF.toInt()), 0.01)
    }

    @Test
    fun `there is no muted text role and every role used for text is checked`() {
        val names = RakshakPalette.Dark.checks().map { it.name }
        listOf("textPrimary", "textSecondary", "dangerText", "successText", "warningText", "onPrimary")
            .forEach { role -> assertTrue("$role must be checked", names.any { it.startsWith(role) }) }
        assertTrue("no muted text", names.none { it.contains("muted", ignoreCase = true) })
    }
}
