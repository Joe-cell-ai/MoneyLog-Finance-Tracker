package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanPdfParsingHelpersTest {
    @Test
    fun stripsSerialNumbersBeforeKnownFinancialLabels() {
        assertEquals("Loan Amount (INR)", cleanLoanPdfLineFromIndices("5 Loan Amount (INR)"))
        assertEquals("Interest Rate", cleanLoanPdfLineFromIndices("6 Interest Rate"))
        assertEquals("Loan Tenure", cleanLoanPdfLineFromIndices("3.a Loan Tenure"))
    }

    @Test
    fun preservesNumbersThatAreNotTableRowPrefixes() {
        assertEquals("Up to 30 days overdue", cleanLoanPdfLineFromIndices("Up to 30 days overdue"))
        assertEquals("₹ 5,250", cleanLoanPdfLineFromIndices("₹ 5,250"))
    }

    @Test
    fun detectsWhenCandidateDuplicatesLeadingRowIndex() {
        assertTrue(loanPdfValueMatchesRowIndex("5 Loan Amount", "5"))
        assertTrue(loanPdfValueMatchesRowIndex("6 Interest Rate", "6"))
        assertFalse(loanPdfValueMatchesRowIndex("6 Interest Rate", "12.5"))
        assertFalse(loanPdfValueMatchesRowIndex("Interest Rate", "6"))
    }

    @Test
    fun extractsInstallmentAmountFromDatedRepaymentScheduleRows() {
        val fixture = """
            Repayment Schedule
            Serial No Due Date Opening Principal Installment Amount Principal Amount Interest Amount Closing Principal
            1 04-FEB-26 569,000.00 12,657.00 7,536.00 5,121.00 561,464.00
            2 04-MAR-26 561,464.00 12,657.00 7,042.00 5,615.00 554,422.00
            3 04-APR-26 554,422.00 12,657.00 7,113.00 5,544.00 547,309.00
        """.trimIndent()
        val rows = parseLoanPdfScheduleRows(fixture)
        assertEquals(3, rows.size)
        assertEquals(1, rows.first().first)
        assertEquals(12657.0, rows.first().second, 0.0)
        assertEquals(3, rows.last().first)
        assertEquals(12657.0, rows.last().second, 0.0)
    }

    @Test
    fun extractsInstallmentAmountFromCompactScheduleRows() {
        val fixture = """
            1 569000.00 12657.00 7536.00 5121.00
            2 561464.00 12657.00 7042.00 5615.00
            3 554422.00 12657.00 7113.00 5544.00
        """.trimIndent()
        val rows = parseLoanPdfScheduleRows(fixture)
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.second == 12657.0 })
    }

    @Test
    fun ignoresHeadersAndRowsWithUnrecognisedDateOrColumns() {
        val fixture = """
            Serial No Due Date Opening Principal Installment Amount
            1 unknown 569000.00 12657.00
            Repayment total 759420.00
        """.trimIndent()
        assertTrue(parseLoanPdfScheduleRows(fixture).isEmpty())
    }
}
