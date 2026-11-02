package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanDocumentParserTest {
    private val finnableKfs = """
        SANCTION LETTER CUM KEY FACTS STATEMENT
        Lender : Finnable Credit Private Limited
        Dear VIJAY KUMAR REDDY NELATOORI
        2 Name of the Borrower (\"Borrower\") VIJAY KUMAR REDDY NELATOORI
        Date: 2026-10-08
        This is reference to your Loan ID. CL491032662
        5 Loan Amount (INR)
        Total Loan Amount (Principal Amount): 29205.0
        6 Annual Percentage Rate 31.65 % p.a.
        1. Fixed Rate of Interest : 22.0 % per annum
        8 Tenure (in months) 9
        9 Interest Rate per annum (%) 22.0
        10 Net Disbursed amount (INR) 29205.0
        3550.0
        11 Amount of EMI Payable (INR)
        a Processing Fees (INR) (inclusive of 18% GST) 1292.0
        b NA
        Prepayment Charges (INR)
        c Bounce Charge (INR) As per Schedule A
        (iii) Foreclosure charges, if applicable 4.0% of the Principal Outstanding + GST
        Key Facts Statement
        Name of the Regulated Entity: Finnable Credit Private Limited
        5 Instalment details
        Monthly 9 3550.0 2026-11-05
        6 Interest rate (%) and type (fixed or floating or hybrid) 22.0 % per annum and fixed
        9 Annual Percentage Rate (APR) (%) 31.65 % p.a.
        (iii) Foreclosure charges, if applicable 4.0% of the Principal Outstanding + GST
        Bounce Charges Flat charge of INR 300
        on every Mandate Bounce (Upto- 2 Times)
        Computation of APR
        Total Interest Amount to be charged during the entire tenor of the loan as per the rate
        5 2680.0
        prevailing on sanction date (in Rupees)
        Total amount to be paid by the borrower (sum of 1,5 and 6) (in Rupees) 32980.0
        Illustrative Repayment Schedule
        Instalment No. Outstanding Principal Principal Interest Installment
        1 29205.0 3057.0 493.0 4842.0
        2 26148.0 3077.0 473.0 3550.0
        3 23071.0 3119.0 431.0 3550.0
        4 19952.0 3177.0 373.0 3550.0
        5 16775.0 3267.0 283.0 3550.0
        6 13508.0 3298.0 252.0 3550.0
        7 10210.0 3365.0 185.0 3550.0
        8 6845.0 3422.0 128.0 3550.0
        9 3423.0 3423.0 62.0 3485.0
        Please Note: The above repayment schedule is for illustrative purposes only.
    """.trimIndent()

    @Test fun extractsKfsFieldsWithoutConfusingAprWithFixedRate() {
        val result = LoanDocumentParser.parse(finnableKfs)
        assertEquals("Finnable Credit Private Limited", result.lender)
        assertEquals("CL491032662", result.loanNumber)
        assertEquals("VIJAY KUMAR REDDY NELATOORI", result.borrowerName)
        assertEquals(29205L, result.principal)
        assertEquals(3550L, result.emi)
        assertEquals(22.0, result.annualInterestRate!!, 0.0)
        assertEquals(31.65, result.apr!!, 0.0)
        assertEquals(9, result.tenureMonths)
        assertEquals("2026-11-05", result.repaymentStartDate)
        assertEquals(1292L, result.processingFee)
        assertEquals(2680L, result.totalInterest)
        assertEquals(32980L, result.declaredTotalRepayment)
        assertEquals(29205L, result.netDisbursedAmount)
        assertEquals("NA", result.prepaymentCharge)
        assertTrue(result.foreclosureCharge!!.contains("4.0%"))
        assertTrue(result.bounceCharge!!.contains("300"))
        assertTrue(result.bounceCharge!!.contains("every Mandate Bounce"))
        assertEquals("Flat charge of INR 300 on every Mandate Bounce (Upto- 2 Times)", result.bounceCharge)
    }

    @Test fun extractsEveryScheduleRowAndPreservesConflictingSourceAmount() {
        val result = LoanDocumentParser.parse(finnableKfs)
        assertEquals(9, result.scheduleRows.size)
        assertEquals(4842L, result.scheduleRows.first().installment)
        assertEquals(3550L, result.scheduleRows.first().principal + result.scheduleRows.first().interest)
        assertEquals(3485L, result.scheduleRows.last().installment)
        assertEquals(29205L, result.scheduleRows.sumOf { it.principal })
        assertEquals(2680L, result.scheduleRows.sumOf { it.interest })
    }

    @Test fun reportsAllKnownKfsArithmeticConflictsInsteadOfSilentlyCorrectingThem() {
        val warnings = LoanDocumentParser.parse(finnableKfs).warnings.joinToString("\n")
        assertTrue(warnings.contains("Instalment 1"))
        assertTrue(warnings.contains("processing fee"))
        assertTrue(warnings.contains("APR total repayment mismatch"))
        assertTrue(warnings.contains("illustrative schedule"))
        assertTrue(warnings.contains("Some schedule instalment amounts differ"))
        assertTrue(warnings.contains("prepayment charges as NA"))
    }

    @Test fun incompleteScheduleDoesNotInventMissingRows() {
        val partial = """
            Lender : Example Finance
            Total Loan Amount (Principal Amount): 10000
            Amount of EMI Payable (INR)
            1100
            Illustrative Repayment Schedule
            1 10000 900 200 1100
            3 8200 950 150 1100
        """.trimIndent()
        val result = LoanDocumentParser.parse(partial)
        assertEquals(2, result.scheduleRows.size)
        assertTrue(result.warnings.any { it.contains("missing, duplicated or out of order") })
        assertFalse(result.scheduleRows.any { it.installmentNumber == 2 })
    }

    @Test fun doesNotTreatAprAsFixedInterestRateWhenNoFixedRateIsPresent() {
        val result = LoanDocumentParser.parse("Annual Percentage Rate (APR) (%) 31.65")
        assertEquals(null, result.annualInterestRate)
        assertEquals(31.65, result.apr!!, 0.0)
    }
}
