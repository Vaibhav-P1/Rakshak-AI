package com.safety.rakshak.ui

import com.safety.rakshak.ui.theme.RakshakPalette
import com.safety.rakshak.ui.theme.WcagContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the UI on what ContrastTest verified, and keeps template branding from coming back.
 * Gradle runs unit tests with the module directory (app/) as the working directory.
 */
class UiGuardsTest {

    private val main = File("src/main")
    private val kotlinSources = File(main, "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    private val res = File(main, "res")

    private fun hex(name: String, file: File): Int {
        val match = Regex("""<color name="$name">#([0-9A-Fa-f]{6})</color>""").find(file.readText())
            ?: error("color $name not found in ${file.path}")
        return (0xFF000000.toInt()) or match.groupValues[1].toInt(16)
    }

    // ── Colours come only from the palette ──────────────────────────

    @Test
    fun `no colour literals outside the theme package`() {
        val offenders = kotlinSources
            .filter { !it.path.replace('\\', '/').contains("/ui/theme/") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val bad = Regex("""Color\(\s*0x""").containsMatchIn(line) ||
                        Regex("""\bColor\.(White|Black|Red|Green|Blue|Gray|DarkGray|LightGray|Yellow|Cyan|Magenta)\b""").containsMatchIn(line)
                    if (bad) "${file.name}:${i + 1}: ${line.trim()}" else null
                }
            }
        assertTrue("Use RakshakColors roles instead:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `no text is smaller than 12sp`() {
        val offenders = kotlinSources
            .filter { it.path.replace('\\', '/').contains("/ui/") }
            .flatMap { file ->
                file.readLines().flatMapIndexed { i, line ->
                    Regex("""fontSize\s*=\s*(\d+(?:\.\d+)?)\.sp""").findAll(line)
                        .filter { it.groupValues[1].toDouble() < 12.0 }
                        .map { "${file.name}:${i + 1}: ${it.value}" }.toList()
                }
            }
        assertTrue("Minimum text size is 12sp:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    // ── XML colours equal the palette, and pass contrast ───────────

    @Test
    fun `launch window and widget colours equal the palette`() {
        val light = File(res, "values/colors.xml")
        val night = File(res, "values-night/colors.xml")
        assertEquals(RakshakPalette.Light.background, hex("launch_bg", light))
        assertEquals(RakshakPalette.Dark.background, hex("launch_bg", night))
        assertEquals(RakshakPalette.Dark.primary, hex("brand_red", light))
        assertEquals(RakshakPalette.Dark.surface, hex("widget_card", light))
        assertEquals(RakshakPalette.Dark.surfaceVariant, hex("widget_card_stroke", light))
        assertEquals(RakshakPalette.Dark.textPrimary, hex("widget_text_primary", light))
        assertEquals(RakshakPalette.Dark.textSecondary, hex("widget_text_secondary", light))
    }

    @Test
    fun `widget text and mark meet the contrast rules on the fixed dark card`() {
        val colors = File(res, "values/colors.xml")
        val card = hex("widget_card", colors)
        assertTrue(WcagContrast.ratio(hex("widget_text_primary", colors), card) >= 4.5)
        assertTrue(WcagContrast.ratio(hex("widget_text_secondary", colors), card) >= 4.5)
        // The SOS button label sits on the brand red.
        assertTrue(WcagContrast.ratio(hex("widget_button_text", colors), hex("brand_red", colors)) >= 4.5)
        // The mark on the card is a graphic: 3:1.
        assertTrue(WcagContrast.ratio(hex("brand_red", colors), card) >= 3.0)
        // The launch mark must be visible on both launch backgrounds.
        assertTrue(WcagContrast.ratio(hex("brand_red", colors), hex("launch_bg", colors)) >= 3.0)
        assertTrue(WcagContrast.ratio(hex("brand_red", colors), hex("launch_bg", File(res, "values-night/colors.xml"))) >= 3.0)
    }

    // ── Branding: no template robot / green grid / legacy rasters ────

    @Test
    fun `template launcher branding is gone`() {
        val legacy = res.listFiles().orEmpty().filter { it.isDirectory && Regex("mipmap-(m|h|x|xx|xxx)dpi").matches(it.name) }
        assertTrue("legacy raster launcher icons must not return: $legacy", legacy.isEmpty())

        val foreground = File(res, "drawable/ic_launcher_foreground.xml").readText()
        assertFalse("the Android robot must not return", foreground.contains("M31,63.928"))
        val background = File(res, "drawable/ic_launcher_background.xml").readText()
        assertFalse("the template green grid must not return", background.contains("#3DDC84") || background.contains("#008577"))

        val everything = res.walkTopDown().filter { it.isFile && it.extension == "xml" }.joinToString("\n") { it.readText() } +
            File(main, "AndroidManifest.xml").readText() + kotlinSources.joinToString("\n") { it.readText() }
        assertFalse("purple_700 template colour must not return", everything.contains("purple_700"))
    }

    @Test
    fun `adaptive icons reference existing layers including a monochrome one`() {
        for (name in listOf("ic_launcher", "ic_launcher_round")) {
            val xml = File(res, "mipmap-anydpi-v26/$name.xml").readText()
            assertTrue("$name needs a monochrome layer", xml.contains("<monochrome"))
            Regex("""@drawable/(\w+)""").findAll(xml).forEach {
                assertTrue("${it.value} must exist", File(res, "drawable/${it.groupValues[1]}.xml").exists())
            }
        }
        assertTrue(File(main, "ic_launcher-playstore.png").exists())
    }

    @Test
    fun `monochrome notification and tile glyphs are one colour, fill only`() {
        for (name in listOf("ic_launcher_monochrome", "ic_notification", "ic_tile_sos")) {
            val xml = File(res, "drawable/$name.xml").readText()
            assertFalse("$name must not use strokes", xml.contains("strokeColor") || xml.contains("strokeWidth"))
            assertFalse("$name must not use gradients", xml.contains("<gradient") || xml.contains("aapt:attr"))
            val fills = Regex("""fillColor="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toSet()
            assertEquals("$name must use exactly one colour, found $fills", 1, fills.size)
        }
    }

    @Test
    fun `notifications and the tile use the Rakshak glyphs`() {
        val notifications = File(main, "java/com/safety/rakshak/service/SosNotifications.kt").readText()
        assertFalse(notifications.contains("ic_launcher"))
        assertTrue(notifications.contains("R.drawable.ic_notification"))
        val manifest = File(main, "AndroidManifest.xml").readText()
        assertTrue(manifest.contains("@drawable/ic_tile_sos"))
        assertTrue(manifest.contains("@mipmap/ic_launcher"))
    }

    // ── Privacy: only the contacts database is backed up ───────────

    @Test
    fun `backup rules include only the contacts database, so SOS history and session stay on the phone`() {
        for (name in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val xml = File(res, "xml/$name").readText()
            val includes = Regex("""<include\s+domain="(\w+)"\s+path="([^"]+)"""").findAll(xml).map { it.groupValues[1] to it.groupValues[2] }.toList()
            assertEquals("$name must include only the database", listOf("database" to "rakshak_database"), includes)
            assertFalse("$name must not back up preferences", xml.contains("sharedpref"))
        }
    }

    @Test
    fun `the launch theme sets the window background and has a night variant`() {
        assertTrue(File(res, "values/themes.xml").readText().contains("@drawable/launch_background"))
        assertTrue(File(res, "values-night/themes.xml").readText().contains("@drawable/launch_background"))
        assertTrue(File(res, "values-v31/themes.xml").readText().contains("windowSplashScreenAnimatedIcon"))
        assertTrue(File(res, "values-night-v31/themes.xml").readText().contains("windowSplashScreenAnimatedIcon"))
    }
}
