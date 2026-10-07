package com.safety.rakshak.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * English (values/) and Hindi (values-hi/) must stay in step: the same keys, the same format
 * placeholders, plurals with an "other" form, and real Devanagari text, not leftover English.
 * Strings marked translatable="false" (the brand name in Devanagari, the SMS fallback text) are exempt.
 */
class StringsParityTest {

    private val res = File("src/main/res")

    private data class Entry(val text: String, val items: Map<String, String>?)

    private fun parse(file: File): Map<String, Entry> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = linkedMapOf<String, Entry>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            if (node.getAttribute("translatable") == "false") continue
            val name = node.getAttribute("name")
            when (node.tagName) {
                "string" -> out[name] = Entry(node.textContent, null)
                "plurals" -> {
                    val items = linkedMapOf<String, String>()
                    val children = node.getElementsByTagName("item")
                    for (j in 0 until children.length) {
                        val item = children.item(j) as Element
                        items[item.getAttribute("quantity")] = item.textContent
                    }
                    out[name] = Entry("", items)
                }
            }
        }
        return out
    }

    private val english = parse(File(res, "values/strings.xml"))
    private val hindi = parse(File(res, "values-hi/strings.xml"))

    private fun placeholders(text: String): List<String> =
        Regex("""%(\d+\$)?[sd]""").findAll(text).map { it.value }.sorted().toList()

    /** Brand and acronym strings that are the same in every language. */
    private val sameInEveryLanguage = setOf("app_name", "sos_label", "widget_app_name", "widget_sos_label", "tile_label", "about_section_sms")

    private val devanagari = Regex("[\\u0900-\\u097F]")

    @Test
    fun `hindi has exactly the same keys as english`() {
        val missing = english.keys - hindi.keys
        val extra = hindi.keys - english.keys
        assertTrue("Missing in values-hi: $missing", missing.isEmpty())
        assertTrue("In values-hi but not in values: $extra", extra.isEmpty())
    }

    @Test
    fun `placeholders match in every string and plural item`() {
        val problems = mutableListOf<String>()
        for ((name, en) in english) {
            val hi = hindi[name] ?: continue
            if (en.items == null) {
                if (placeholders(en.text) != placeholders(hi.text)) problems += "$name: ${placeholders(en.text)} vs ${placeholders(hi.text)}"
            } else {
                val expected = placeholders(en.items.getValue("other"))
                for ((quantity, text) in hi.items.orEmpty()) {
                    if (placeholders(text) != expected) problems += "$name[$quantity]: $expected vs ${placeholders(text)}"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `every hindi plural has an other form`() {
        for ((name, hi) in hindi) {
            val items = hi.items ?: continue
            assertTrue("$name needs an 'other' item", "other" in items.keys)
        }
    }

    @Test
    fun `hindi values are real translations`() {
        val problems = mutableListOf<String>()
        for ((name, hi) in hindi) {
            if (name in sameInEveryLanguage) continue
            val texts = hi.items?.values ?: listOf(hi.text)
            for (text in texts) {
                if (text.isBlank()) problems += "$name is empty"
                else if (!devanagari.containsMatchIn(text)) problems += "$name has no Devanagari text: $text"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `english strings are never blank`() {
        english.forEach { (name, en) ->
            assertTrue("$name is empty", (en.items?.values ?: listOf(en.text)).none { it.isBlank() })
        }
    }

    @Test
    fun `per-app language is declared for English and Hindi`() {
        val locales = File(res, "xml/locales_config.xml").readText()
        assertTrue(locales.contains("android:name=\"en\""))
        assertTrue(locales.contains("android:name=\"hi\""))
        assertTrue(File("src/main/AndroidManifest.xml").readText().contains("android:localeConfig=\"@xml/locales_config\""))
    }

    @Test
    fun `the key count is not accidentally tiny`() {
        // Guards against parsing returning an empty map and every check passing vacuously.
        assertTrue("only ${english.size} english keys parsed", english.size > 150)
        assertEquals(english.size, hindi.size)
    }
}
