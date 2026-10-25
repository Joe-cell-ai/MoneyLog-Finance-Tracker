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
}
