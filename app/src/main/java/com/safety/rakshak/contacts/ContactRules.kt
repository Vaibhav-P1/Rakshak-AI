package com.safety.rakshak.contacts

/** A contact as the rules see it. Pure Kotlin, independent of Room, so the rules are JVM-tested. */
data class ContactInfo(val id: Int, val name: String, val phone: String, val isPrimary: Boolean)

/**
 * Validation, de-duplication, the contact cap and primary-contact rules. Enforced in
 * `MainViewModel` (not only in the UI), so manual entry, the phone-contact picker and edits
 * all go through the same checks. Nothing here needs a permission.
 *
 * Not checked (documented limitation): the user's own number. Android only exposes it
 * through READ_PHONE_STATE / READ_PHONE_NUMBERS, which the app deliberately does not hold,
 * and many SIMs do not store it anyway.
 */
object ContactRules {

    const val MAX_CONTACTS = 5
    const val MIN_DIGITS = 7
    const val MAX_DIGITS = 15
    const val MAX_NAME_LENGTH = 40

    /** Reasons a contact cannot be saved. */
    sealed interface Issue {
        data object NameRequired : Issue
        data object NameTooLong : Issue
        data object NumberInvalid : Issue
        data object LimitReached : Issue
        data class Duplicate(val existingName: String) : Issue
    }

    /** Things worth telling the user that do not block saving. */
    sealed interface Warning {
        data object NoCountryCode : Warning
        data class PossibleDuplicate(val existingName: String) : Warning
    }

    data class Validation(val issues: List<Issue>, val warnings: List<Warning>) {
        val isValid: Boolean get() = issues.isEmpty()
    }

    /** Result of deleting a contact: who, if anyone, becomes primary instead. */
    data class DeleteResult(val promotedId: Int?, val promotedName: String?)

    /** Digits and a leading '+'; a leading "00" becomes '+'. Empty if there are no digits. */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        return when {
            trimmed.startsWith("+") -> "+$digits"
            digits.startsWith("00") -> "+" + digits.drop(2)
            else -> digits
        }
    }

    fun canAdd(existing: List<ContactInfo>): Boolean = existing.size < MAX_CONTACTS

    /**
     * @param editingId the contact being edited, or null when adding. It is excluded from
     * duplicate checks and from the cap.
     */
    fun validate(name: String, phone: String, existing: List<ContactInfo>, editingId: Int?): Validation {
        val issues = mutableListOf<Issue>()
        val warnings = mutableListOf<Warning>()

        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) issues += Issue.NameRequired
        else if (trimmedName.length > MAX_NAME_LENGTH) issues += Issue.NameTooLong

        val normalized = normalize(phone)
        val digitCount = normalized.count { it.isDigit() }
        val numberOk = digitCount in MIN_DIGITS..MAX_DIGITS
        if (!numberOk) issues += Issue.NumberInvalid

        if (editingId == null && !canAdd(existing)) issues += Issue.LimitReached

        if (numberOk) {
            val others = existing.filter { it.id != editingId }
            val exact = others.firstOrNull { normalize(it.phone) == normalized }
            if (exact != null) {
                issues += Issue.Duplicate(exact.name)
            } else {
                if (!normalized.startsWith("+")) warnings += Warning.NoCountryCode
                others.firstOrNull { likelySameNumber(normalized, normalize(it.phone)) }
                    ?.let { warnings += Warning.PossibleDuplicate(it.name) }
            }
        }
        return Validation(issues, warnings)
    }

    /** One number has a country code and the other does not, and the shorter is the tail of the longer. */
    private fun likelySameNumber(a: String, b: String): Boolean {
        if (a.startsWith("+") == b.startsWith("+")) return false
        val da = a.filter { it.isDigit() }
        val db = b.filter { it.isDigit() }
        val (short, long) = if (da.length <= db.length) da to db else db to da
        return short.length >= 10 && long.endsWith(short)
    }

    // ── Primary contact ─────────────────────────────────────────

    /** Only the very first contact becomes primary on its own. */
    fun newContactBecomesPrimary(existing: List<ContactInfo>): Boolean = existing.isEmpty()

    /** If the primary is deleted and others remain, the first of them by name takes over. */
    fun afterDelete(existing: List<ContactInfo>, deletedId: Int): DeleteResult {
        val deleted = existing.firstOrNull { it.id == deletedId }
        if (deleted == null || !deleted.isPrimary) return DeleteResult(null, null)
        val next = existing.filter { it.id != deletedId }.minWithOrNull(compareBy({ it.name.lowercase() }, { it.id }))
            ?: return DeleteResult(null, null)
        return DeleteResult(next.id, next.name)
    }

    /** Ids whose primary flag must be cleared so exactly one remains (the first in order). */
    fun primariesToClear(inDisplayOrder: List<ContactInfo>): List<Int> {
        val primaries = inDisplayOrder.filter { it.isPrimary }
        return if (primaries.size <= 1) emptyList() else primaries.drop(1).map { it.id }
    }

    /** The list with [primaryId] as the only primary (or none when null). */
    fun withPrimary(contacts: List<ContactInfo>, primaryId: Int?): List<ContactInfo> =
        contacts.map { it.copy(isPrimary = it.id == primaryId) }
}
