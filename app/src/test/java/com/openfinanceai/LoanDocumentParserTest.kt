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
        assertEquals(listOf(4842L, 3550L, 3550L, 3550L, 3550L, 3550L, 3550L, 3550L, 3485L), result.scheduleRows.map { it.installment })
        assertEquals(33177L, result.scheduleRows.sumOf { it.installment })
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
    @Test fun extractsVariableMonthlyEmiAndChangingAnnualRateWithoutFlatteningRows() {
        val statement = """
            Lender: Floating Rate Finance
            Total Loan Amount (Principal Amount): 30000
            Amount of EMI Payable (INR) 10500
            Fixed Rate of Interest: 12.0
            Tenure (in months) 3
            Illustrative Repayment Schedule
            Instalment No. Outstanding Principal Interest Rate (%) Principal Interest Installment
            1 30000 12.0 10000 300 10300
            2 20000 13.5 10000 225 10225
            3 10000 14.0 10000 117 10117
        """.trimIndent()
        val result = LoanDocumentParser.parse(statement)
        assertEquals(3, result.scheduleRows.size)
        assertEquals(listOf(10300L, 10225L, 10117L), result.scheduleRows.map { it.installment })
        assertEquals(listOf(300L, 225L, 117L), result.scheduleRows.map { it.interest })
        assertEquals(listOf(12.0, 13.5, 14.0), result.scheduleRows.map { it.annualRate })
        assertEquals(30642L, result.scheduleRows.sumOf { it.installment })
    }

    @Test fun retainsDifferentMonthlyInstallmentAmountsAndInterestValues() {
        val statement = """
            Lender: Step EMI Finance
            Total Loan Amount (Principal Amount): 20000
            Amount of EMI Payable (INR) 7000
            Fixed Rate of Interest: 12.0
            Tenure (in months) 3
            Illustrative Repayment Schedule
            Instalment No. Outstanding Principal Principal Interest Installment
            1 20000 6500 200 6700
            2 13500 6600 135 6735
            3 6900 6900 69 6969
        """.trimIndent()
        val result = LoanDocumentParser.parse(statement)
        assertEquals(listOf(6700L, 6735L, 6969L), result.scheduleRows.map { it.installment })
        assertEquals(listOf(200L, 135L, 69L), result.scheduleRows.map { it.interest })
    }

    @Test fun extractsExplicitIrregularDatesAcrossMultiplePdfPages() {
        val statement = """
            Lender: Date Specific Finance
            Total Loan Amount (Principal Amount): 20000
            Amount of EMI Payable (INR) 9000
            Fixed Rate of Interest: 12.0
            Tenure (in months) 2
            Repayment Schedule
            Instalment No. Due Date Outstanding Principal Principal Interest Installment
            1 05-Nov-2026 20000 9000 200 9200
        """.trimIndent() + '\u000C' + """
            Continued schedule
            2 07-Dec-2026 11000 9000 110 9110
        """.trimIndent()
        val result = LoanDocumentParser.parse(statement)
        assertEquals(2, result.scheduleRows.size)
        assertEquals("2026-11-05", result.scheduleRows[0].dueDate)
        assertEquals("2026-12-07", result.scheduleRows[1].dueDate)
        assertEquals(listOf(9200L, 9110L), result.scheduleRows.map { it.installment })
    }


    @Test fun parsesYesBankFiveAmountScheduleWithOpeningInstallmentPrincipalInterestClosingAndTwoDigitDates() {
        val statement = """
            Customer Name : NELATOORI VIJAY KUMAR REDDY
            Agreement No : PLN002202247931
            Repayment Start Date : 04-FEB-26
            Repayment End Date : 04-JAN-31
            Loan Amount(INR) : INR 5,69,000.00
            Tenure : 60 Months
            Repayment Schedule
            Serial No. Due Date Opening Principal (INR) Installment Amount (INR) Principal Amount (INR) Interest Amount (INR) Closing Principal (INR)
            1 04-FEB-26 569,000.00 12,657.00 7,536.00 5,121.00 561,464.00
            2 04-MAR-26 561,464.00 12,657.00 7,042.00 5,615.00 554,422.00
            3 04-APR-26 554,422.00 12,657.00 7,113.00 5,544.00 547,309.00
        """.trimIndent()
        val result = LoanDocumentParser.parse(statement)
        assertEquals(3, result.scheduleRows.size)
        assertEquals("2026-02-04", result.scheduleRows[0].dueDate)
        assertEquals(569000L, result.scheduleRows[0].openingPrincipal)
        assertEquals(7536L, result.scheduleRows[0].principal)
        assertEquals(5121L, result.scheduleRows[0].interest)
        assertEquals(12657L, result.scheduleRows[0].installment)
        assertEquals("2026-04-04", result.scheduleRows[2].dueDate)
        assertEquals(554422L, result.scheduleRows[2].openingPrincipal)
        assertEquals(547309L, result.scheduleRows[2].openingPrincipal - result.scheduleRows[2].principal)
    }


}
