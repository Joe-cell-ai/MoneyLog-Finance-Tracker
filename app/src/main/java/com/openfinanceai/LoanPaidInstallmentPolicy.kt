package com.openfinanceai

import java.time.LocalDate

/**
 * Conservative identity cleanup for OCR/PDF labels. Legal boilerplate is never a person's
 * name or lender name; ambiguous candidates remain blank for manual entry.
 */
internal object LoanIdentitySanitizer {
    private val boilerplate = Regex(
        """(?i)\b(?:party|parties|hereunder|herein|breach|default|contravention|lenders?\s+hereunder|bank\s+name|borrower\s+name|name\s+of\s+the\s+borrower|in\s+relation\s+to)\b"""
    )

    private fun clean(candidate: String?, maxLength: Int): String? {
        val value = candidate?.replace(Regex("""\s+"""), " ")?.trim()?.trim(',', ':', ';', '-', ' ')
            ?: return null
        if (value.length !in 3..maxLength || !value.any(Char::isLetter)) return null
        if (boilerplate.containsMatchIn(value)) return null
        if (value.count { it in ".!?;" } > 1 || value.contains('/') || value.contains('\n')) return null
        if (Regex("""(?i)^(?:name|bank|lender|borrower|party|parties|unknown|not identified|n/?a)$""").matches(value)) return null
        return value
    }

    fun borrower(candidate: String?): String? = clean(candidate, 80)
    fun lender(candidate: String?): String? = clean(candidate, 80)
}

/** Number of consecutive schedule instalments whose due dates are strictly before today. */
internal object LoanPaidInstallmentPolicy {
    fun countPastDue(rows: List<LoanDatedInstallment>, today: LocalDate): Int {
        var count = 0
        for (row in rows) {
            val dueDate = runCatching { LocalDate.parse(row.date) }.getOrNull() ?: break
            if (!dueDate.isBefore(today)) break
            count++
        }
        return count
    }
}
