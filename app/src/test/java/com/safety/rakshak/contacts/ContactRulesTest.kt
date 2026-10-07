package com.safety.rakshak.contacts

import com.safety.rakshak.contacts.ContactRules.Issue
import com.safety.rakshak.contacts.ContactRules.Warning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactRulesTest {

    private fun c(id: Int, name: String, phone: String, primary: Boolean = false) = ContactInfo(id, name, phone, primary)
    private val alice = c(1, "Alice", "+919876543210", primary = true)
    private val bob = c(2, "Bob", "+14155550123")

    // ── Normalisation ───────────────────────────────────────────

    @Test
    fun `normalize keeps digits and a leading plus and turns 00 into plus`() {
        assertEquals("+919876543210", ContactRules.normalize(" +91 98765-43210 "))
        assertEquals("9876543210", ContactRules.normalize("(98765) 43210"))
        assertEquals("+919876543210", ContactRules.normalize("00 91 98765 43210"))
        assertEquals("", ContactRules.normalize("call me"))
    }

    // ── Validation ──────────────────────────────────────────────

    @Test
    fun `a valid new contact has no issues`() {
        val v = ContactRules.validate("Carol", "+442071838750", listOf(alice, bob), editingId = null)
        assertTrue(v.isValid)
        assertTrue(v.warnings.isEmpty())
    }

    @Test
    fun `blank and over-long names are rejected`() {
        assertTrue(Issue.NameRequired in ContactRules.validate("   ", "+919876543210", emptyList(), null).issues)
        assertTrue(Issue.NameTooLong in ContactRules.validate("x".repeat(41), "+919876543210", emptyList(), null).issues)
        assertTrue(ContactRules.validate("x".repeat(40), "+919876543210", emptyList(), null).isValid)
    }

    @Test
    fun `numbers need 7 to 15 digits`() {
        assertTrue(Issue.NumberInvalid in ContactRules.validate("A", "123456", emptyList(), null).issues)
        assertTrue(Issue.NumberInvalid in ContactRules.validate("A", "abc", emptyList(), null).issues)
        assertTrue(Issue.NumberInvalid in ContactRules.validate("A", "+1234567890123456", emptyList(), null).issues)
        assertTrue(ContactRules.validate("A", "1234567", emptyList(), null).isValid)
        assertTrue(ContactRules.validate("A", "+123456789012345", emptyList(), null).isValid)
    }

    @Test
    fun `an exact duplicate is blocked and names the existing contact`() {
        val v = ContactRules.validate("Alice 2", "+91 98765 43210", listOf(alice, bob), null)
        assertFalse(v.isValid)
        assertEquals(Issue.Duplicate("Alice"), v.issues.single())
    }

    @Test
    fun `a likely duplicate (same last 10 digits, one without country code) only warns`() {
        val v = ContactRules.validate("Alice", "98765 43210", listOf(alice), null)
        assertTrue(v.isValid)
        assertTrue(Warning.PossibleDuplicate("Alice") in v.warnings)
        assertTrue(Warning.NoCountryCode in v.warnings)
    }

    @Test
    fun `different numbers with the same last digits in different countries are not flagged`() {
        // Both have a country code and differ: not a likely duplicate.
        val v = ContactRules.validate("D", "+4412345678901", listOf(c(3, "E", "+9112345678901")), null)
        assertTrue(v.warnings.none { it is Warning.PossibleDuplicate })
    }

    @Test
    fun `a missing country code only warns`() {
        val v = ContactRules.validate("A", "9876543210", emptyList(), null)
        assertTrue(v.isValid)
        assertEquals(listOf(Warning.NoCountryCode), v.warnings)
    }

    @Test
    fun `editing a contact does not conflict with itself`() {
        val v = ContactRules.validate("Alice B", "+919876543210", listOf(alice, bob), editingId = 1)
        assertTrue(v.isValid)
        assertTrue(v.warnings.isEmpty())
    }

    @Test
    fun `editing into another contacts number is blocked`() {
        val v = ContactRules.validate("Alice", "+14155550123", listOf(alice, bob), editingId = 1)
        assertEquals(Issue.Duplicate("Bob"), v.issues.single())
    }

    // ── Cap of 5 ────────────────────────────────────────────────

    private val five = (1..5).map { c(it, "N$it", "+9190000000$it") }

    @Test
    fun `the sixth contact is refused but editing at the cap is allowed`() {
        assertTrue(Issue.LimitReached in ContactRules.validate("New", "+919111111111", five, null).issues)
        assertTrue(ContactRules.validate("N1 renamed", "+919000000001", five, editingId = 1).isValid)
        assertTrue(ContactRules.canAdd(five.take(4)))
        assertFalse(ContactRules.canAdd(five))
    }

    @Test
    fun `existing contacts above the cap are left alone, only adding is refused`() {
        val seven = (1..7).map { c(it, "N$it", "+9190000000$it") }
        assertFalse(ContactRules.canAdd(seven))
        assertTrue(ContactRules.validate("N7 renamed", "+919000000007", seven, editingId = 7).isValid)
    }

    // ── Primary contact ─────────────────────────────────────────

    @Test
    fun `only the very first contact becomes primary automatically`() {
        assertTrue(ContactRules.newContactBecomesPrimary(emptyList()))
        assertFalse(ContactRules.newContactBecomesPrimary(listOf(alice)))
        assertFalse(ContactRules.newContactBecomesPrimary(listOf(bob))) // none primary, but not the first contact
    }

    @Test
    fun `deleting the primary promotes the first remaining contact by name`() {
        val zed = c(3, "Zed", "+10000000001")
        val result = ContactRules.afterDelete(listOf(alice, zed, bob), deletedId = 1)
        assertEquals(2, result.promotedId) // Bob
        assertEquals("Bob", result.promotedName)
    }

    @Test
    fun `deleting a non-primary or the last contact promotes nobody`() {
        assertNull(ContactRules.afterDelete(listOf(alice, bob), deletedId = 2).promotedId)
        assertNull(ContactRules.afterDelete(listOf(alice), deletedId = 1).promotedId)
    }

    @Test
    fun `deleting when no contact is primary promotes nobody`() {
        assertNull(ContactRules.afterDelete(listOf(bob, c(3, "C", "+10000000001")), deletedId = 2).promotedId)
    }

    @Test
    fun `multiple primaries are repaired to exactly one, keeping the first in order`() {
        val broken = listOf(c(1, "A", "+10000000001", true), c(2, "B", "+10000000002", true), c(3, "C", "+10000000003"))
        assertEquals(listOf(2), ContactRules.primariesToClear(broken))
        assertTrue(ContactRules.primariesToClear(listOf(alice, bob)).isEmpty())
        assertTrue(ContactRules.primariesToClear(listOf(bob)).isEmpty())
    }

    @Test
    fun `after setting a primary exactly one contact is primary`() {
        val updated = ContactRules.withPrimary(listOf(alice, bob), 2)
        assertEquals(listOf(2), updated.filter { it.isPrimary }.map { it.id })
        assertEquals(emptyList<Int>(), ContactRules.withPrimary(listOf(alice, bob), null).filter { it.isPrimary }.map { it.id })
    }
}
