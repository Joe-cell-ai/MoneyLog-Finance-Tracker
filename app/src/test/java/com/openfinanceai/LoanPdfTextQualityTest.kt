package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanPdfTextQualityTest {
    @Test fun longFooterOnlyTextStillRequestsOcr() {
        val text = "Page 1 of 10\\nCustomer support and registered office details\\nwww.example-lender.in"
        assertTrue(text.length > 24)
        assertTrue(LoanPdfTextQuality.needsOcr(text))
    }

    @Test fun structuredRepaymentRowsDoNotRequestOcrAgain() {
        val text = """
            Repayment Schedule
            Installment No Due Date Opening Principal Installment Principal Interest Closing
            1 04-Nov-2026 10000 3500 3000 500 7000
            2 04-Dec-2026 7000 3500 3200 300 3800
        """.trimIndent()
        assertFalse(LoanPdfTextQuality.needsOcr(text))
    }

    @Test fun betterOcrTextWinsButSelectableTextWinsOnTie() {
        val sparse = "Page 2 of 10\\nLender footer and contact information"
        val ocr = """
            Loan Amount: 10000
            Monthly EMI: 3500
            Repayment Schedule
            1 04-Nov-2026 10000 3500 3000 500 7000
            2 04-Dec-2026 7000 3500 3200 300 3800
        """.trimIndent()
        assertEquals(ocr, LoanPdfTextQuality.chooseBest(sparse, ocr))
        assertEquals(sparse, LoanPdfTextQuality.chooseBest(sparse, sparse))
    }
}
