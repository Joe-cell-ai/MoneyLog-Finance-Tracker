package com.openfinanceai

/**
 * Scores selectable PDF text for loan-document usefulness. A long page containing only
 * headers/footers is not considered successfully extracted; OCR should still be attempted.
 */
internal object LoanPdfTextQuality {
    private val loanLabels = Regex(
        """\b(?:loan\s+amount|amount\s+financed|principal\s+amount|emi|epi|equated\s+monthly|interest\s+rate|tenure|loan\s+term|borrower|applicant|repayment\s+schedule|install?ment\s+no|opening\s+principal|closing\s+principal|agreement\s+number|outstanding\s+principal)\b""",
        RegexOption.IGNORE_CASE
    )
    private val numberedFinancialRow = Regex(
        """^\s*\d{1,3}\s+.*\d[0-9,]*(?:\.[0-9]{1,2})?.*\d[0-9,]*(?:\.[0-9]{1,2})?.*$"""
    )

    fun score(text: String): Int {
        if (text.isBlank()) return 0
        val labels = loanLabels.findAll(text).count()
        val rows = text.lineSequence().count { numberedFinancialRow.matches(it) }
        return labels * 10 + rows * 4 + (text.length / 500).coerceAtMost(4)
    }

    fun needsOcr(text: String): Boolean = text.trim().length < 24 || score(text) < 20

    /** Prefer the source with stronger financial structure; ties retain selectable PDF text. */
    fun chooseBest(selectableText: String, recognizedText: String): String =
        if (score(recognizedText) > score(selectableText)) recognizedText else selectableText
}
