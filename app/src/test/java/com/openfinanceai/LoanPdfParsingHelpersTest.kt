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

    @Test
    fun supportsDifferentDateSeparatorsAndIndianNumberFormatting() {
        val fixture = """
            4 04/05/2026 1,23,456.00 9,876.50 8,000.00 1,876.50 1,15,456.00
            5 2026-06-04 115456.00 9876.50 8120.00 1756.50 107336.00
        """.trimIndent()
        val rows = parseLoanPdfScheduleRows(fixture)
        assertEquals(listOf(4, 5), rows.map { it.first })
        assertEquals(listOf(9876.5, 9876.5), rows.map { it.second })
    }

    @Test
    fun supportsScheduleRowsAcrossPageBreaksAndIgnoresRepeatedHeaders() {
        val pageOne = """
            Repayment Schedule
            Serial No Due Date Opening Principal Installment Amount Principal Amount Interest Amount Closing Principal
            1 04-FEB-26 569000.00 12657.00 7536.00 5121.00 561464.00
            2 04-MAR-26 561464.00 12657.00 7042.00 5615.00 554422.00
        """.trimIndent()
        val pageTwo = """
            Repayment Schedule (continued)
            Serial No Due Date Opening Principal Installment Amount Principal Amount Interest Amount Closing Principal
            3 04-APR-26 554422.00 12657.00 7113.00 5544.00 547309.00
            4 04-MAY-26 547309.00 12657.00 7200.00 5457.00 540109.00
        """.trimIndent()
        val rows = parseLoanPdfScheduleRows("$pageOne\n$pageTwo")
        assertEquals(listOf(1, 2, 3, 4), rows.map { it.first })
        assertTrue(rows.all { it.second == 12657.0 })
    }

    @Test
    fun rejectsRowsWithMissingColumnsOrNonNumericEmi() {
        val fixture = """
            1 04-FEB-26 569000.00 7536.00 5121.00 561464.00
            2 04-MAR-26 561464.00 unknown 7042.00 5615.00 554422.00
            3 04-APR-26 554422.00 12657.00 7113.00 5544.00 547309.00 extra
        """.trimIndent()
        assertTrue(parseLoanPdfScheduleRows(fixture).isEmpty())
    }

    @Test
    fun doesNotTreatAprSummaryOrTotalsAsInstallmentRows() {
        val fixture = """
            APR 46.29%
            Amount of each EPI 12,657.00 nos. of EPIs 60
            Total repayment 759,420.00
            Total interest 190,420.00
            Loan Amount 569,000.00
        """.trimIndent()
        assertTrue(parseLoanPdfScheduleRows(fixture).isEmpty())
    }

    @Test
    fun preservesDifferentEmiValuesRatherThanAssumingEveryScheduleIsFlat() {
        val fixture = """
            1 04-FEB-26 100000.00 9500.00 8000.00 1500.00 92000.00
            2 04-MAR-26 92000.00 9600.00 8200.00 1400.00 83800.00
            3 04-APR-26 83800.00 9700.00 8400.00 1300.00 75400.00
        """.trimIndent()
        assertEquals(listOf(9500.0, 9600.0, 9700.0), parseLoanPdfScheduleRows(fixture).map { it.second })
    }

    @Test
    fun handlesTabsAndIrregularSpacingInExtractedScheduleText() {
        val fixture = listOf(
            "1\t04-FEB-26\t569,000.00\t12,657.00\t7,536.00\t5,121.00\t561,464.00",
            "2     04-MAR-26    561,464.00     12,657.00    7,042.00    5,615.00    554,422.00"
        ).joinToString("\n")
        val rows = parseLoanPdfScheduleRows(fixture)
        assertEquals(listOf(1, 2), rows.map { it.first })
        assertTrue(rows.all { it.second == 12657.0 })
    }

    @Test
    fun handlesZeroInterestScheduleAndSingleDigitInstallments() {
        val fixture = """
            1 2026-01-01 10000.00 1000.00 1000.00 0.00 9000.00
            2 2026-02-01 9000.00 1000.00 1000.00 0.00 8000.00
            3 2026-03-01 8000.00 1000.00 1000.00 0.00 7000.00
        """.trimIndent()
        val rows = parseLoanPdfScheduleRows(fixture)
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.second == 1000.0 })
    }

    @Test
    fun ignoresScheduleRowsWithInsufficientFinancialColumns() {
        val fixture = """
            1 04-FEB-26 569000.00 12657.00
            2 04-MAR-26 561464.00 12657.00 7042.00
            3 04-APR-26 554422.00 12657.00 7113.00 5544.00
        """.trimIndent()
        assertTrue(parseLoanPdfScheduleRows(fixture).isEmpty())
    }

    @Test
    fun readsLargeTenureSerialNumbersWithoutConfusingEmi() {
        val fixture = """
            59 04-DEC-30 25000.00 12657.00 12300.00 357.00 12700.00
            60 04-JAN-31 12700.00 12657.00 12500.00 157.00 200.00
        """.trimIndent()
        val rows = parseLoanPdfScheduleRows(fixture)
        assertEquals(listOf(59, 60), rows.map { it.first })
        assertEquals(listOf(12657.0, 12657.0), rows.map { it.second })
    }

    @Test
    fun rejectsUnsupportedDateShapesWithoutRejectingSupportedRows() {
        val fixture = """
            1 2026/01/01 10000.00 1000.00 900.00 100.00 9100.00
            2 01-XYZ-2026 9100.00 1000.00 900.00 100.00 8200.00
            3 2026-03-01 8200.00 1000.00 900.00 100.00 7300.00
        """.trimIndent()
        assertEquals(listOf(3), parseLoanPdfScheduleRows(fixture).map { it.first })
    }

}
