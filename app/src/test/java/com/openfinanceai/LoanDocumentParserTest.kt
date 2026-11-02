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
        assertEquals(758402L, result.scheduleRows.sumOf { it.installment })
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


    @Test fun parsesAllSixtyRowsFromUploadedYesBankRepaymentStatement() {
        val statement = """
            Customer Name : NELATOORI VIJAY KUMAR REDDY
            Agreement No : PLN002202247931
            Repayment Start Date : 04-FEB-26
            Repayment End Date : 04-JAN-31
            Loan Amount(INR) : INR 5,69,000.00
            Tenure : 60 Months
            Repayment Schedule
            Serial No. Due Date Opening Principal (INR) Installment Amount (INR) Principal Amount (INR) Interest Amount (INR) Closing Principal (INR)
            1         04-FEB-26              569,000.00           12,657.00            7,536.00           5,121.00        561,464.00
            2        04-MAR-26               561,464.00           12,657.00            7,042.00           5,615.00        554,422.00
            3        04-APR-26               554,422.00           12,657.00            7,113.00           5,544.00        547,309.00
            4        04-MAY-26               547,309.00           12,657.00            7,184.00           5,473.00        540,125.00
            5        04-JUN-26               540,125.00           12,657.00            7,256.00           5,401.00        532,869.00
            6         04-JUL-26              532,869.00           12,657.00            7,328.00           5,329.00        525,541.00
            7        04-AUG-26               525,541.00           12,657.00            7,402.00           5,255.00        518,139.00
            8         04-SEP-26              518,139.00           12,657.00            7,476.00           5,181.00        510,663.00
            9        04-OCT-26               510,663.00           12,657.00            7,550.00           5,107.00        503,113.00
            10        04-NOV-26               503,113.00           12,657.00            7,626.00           5,031.00        495,487.00
            11        04-DEC-26               495,487.00           12,657.00            7,702.00           4,955.00        487,785.00
            12        04-JAN-27               487,785.00           12,657.00            7,779.00           4,878.00        480,006.00
            13         04-FEB-27              480,006.00           12,657.00            7,857.00           4,800.00        472,149.00
            14        04-MAR-27               472,149.00           12,657.00            7,936.00           4,721.00        464,213.00
            15        04-APR-27               464,213.00           12,657.00            8,015.00           4,642.00        456,198.00
            16        04-MAY-27               456,198.00           12,657.00            8,095.00           4,562.00        448,103.00
            17        04-JUN-27               448,103.00           12,657.00            8,176.00           4,481.00        439,927.00
            18         04-JUL-27              439,927.00           12,657.00            8,258.00           4,399.00        431,669.00
            19        04-AUG-27               431,669.00           12,657.00            8,340.00           4,317.00        423,329.00
            20         04-SEP-27              423,329.00           12,657.00            8,424.00           4,233.00        414,905.00
            21        04-OCT-27               414,905.00           12,657.00            8,508.00           4,149.00        406,397.00
            22        04-NOV-27               406,397.00           12,657.00            8,593.00           4,064.00        397,804.00
            23        04-DEC-27               397,804.00           12,657.00            8,679.00           3,978.00        389,125.00
            24        04-JAN-28               389,125.00           12,657.00            8,766.00           3,891.00        380,359.00
            25         04-FEB-28              380,359.00           12,657.00            8,853.00           3,804.00        371,506.00
            26        04-MAR-28               371,506.00           12,657.00            8,942.00           3,715.00        362,564.00
            27        04-APR-28               362,564.00           12,657.00            9,031.00           3,626.00        353,533.00
            28        04-MAY-28               353,533.00           12,657.00            9,122.00           3,535.00        344,411.00
            29        04-JUN-28               344,411.00           12,657.00            9,213.00           3,444.00        335,198.00
            30         04-JUL-28              335,198.00           12,657.00            9,305.00           3,352.00        325,893.00
            31        04-AUG-28               325,893.00           12,657.00            9,398.00           3,259.00        316,495.00
            32         04-SEP-28              316,495.00           12,657.00            9,492.00           3,165.00        307,003.00
            33        04-OCT-28               307,003.00           12,657.00            9,587.00           3,070.00        297,416.00
            34        04-NOV-28               297,416.00           12,657.00            9,683.00           2,974.00        287,733.00
            35        04-DEC-28               287,733.00           12,657.00            9,780.00           2,877.00        277,953.00
            36        04-JAN-29               277,953.00           12,657.00            9,877.00           2,780.00        268,076.00
            37         04-FEB-29              268,076.00           12,657.00            9,976.00           2,681.00        258,100.00
            38        04-MAR-29               258,100.00           12,657.00           10,076.00           2,581.00        248,024.00
            39        04-APR-29               248,024.00           12,657.00           10,177.00           2,480.00        237,847.00
            40        04-MAY-29               237,847.00           12,657.00           10,279.00           2,378.00        227,568.00
            41        04-JUN-29               227,568.00           12,657.00           10,381.00           2,276.00        217,187.00
            42         04-JUL-29              217,187.00           12,657.00           10,485.00           2,172.00        206,702.00
            43        04-AUG-29               206,702.00           12,657.00           10,590.00           2,067.00        196,112.00
            44         04-SEP-29              196,112.00           12,657.00           10,696.00           1,961.00        185,416.00
            45        04-OCT-29               185,416.00           12,657.00           10,803.00           1,854.00        174,613.00
            46        04-NOV-29               174,613.00           12,657.00           10,911.00           1,746.00        163,702.00
            47        04-DEC-29               163,702.00           12,657.00           11,020.00           1,637.00        152,682.00
            48        04-JAN-30               152,682.00           12,657.00           11,130.00           1,527.00        141,552.00
            49         04-FEB-30              141,552.00           12,657.00           11,241.00           1,416.00        130,311.00
            50        04-MAR-30               130,311.00           12,657.00           11,354.00           1,303.00        118,957.00
            51        04-APR-30               118,957.00           12,657.00           11,467.00           1,190.00        107,490.00
            52        04-MAY-30               107,490.00           12,657.00           11,582.00           1,075.00         95,908.00
            53        04-JUN-30                95,908.00           12,657.00           11,698.00             959.00         84,210.00
            54         04-JUL-30               84,210.00           12,657.00           11,815.00             842.00         72,395.00
            55        04-AUG-30                72,395.00           12,657.00           11,933.00             724.00         60,462.00
            56         04-SEP-30               60,462.00           12,657.00           12,052.00             605.00         48,410.00
            57        04-OCT-30                48,410.00           12,657.00           12,173.00             484.00         36,237.00
            58        04-NOV-30                36,237.00           12,657.00           12,295.00             362.00         23,942.00
            59        04-DEC-30                23,942.00           12,657.00           12,418.00             239.00         11,524.00
            60        04-JAN-31                11,524.00           11,639.00           11,524.00             115.00              0.00
        """.trimIndent()
        val result = LoanDocumentParser.parse(statement)
        assertEquals(60, result.scheduleRows.size)
        assertEquals((1..60).toList(), result.scheduleRows.map { it.installmentNumber })
        assertEquals("2026-02-04", result.scheduleRows.first().dueDate)
        assertEquals("2031-01-04", result.scheduleRows.last().dueDate)
        assertEquals(569000L, result.scheduleRows.first().openingPrincipal)
        assertEquals(0L, result.scheduleRows.last().openingPrincipal - result.scheduleRows.last().principal)
        assertEquals(33177L, result.scheduleRows.sumOf { it.installment })
        assertEquals(569000L, result.scheduleRows.sumOf { it.principal })
        assertEquals(189402L, result.scheduleRows.sumOf { it.interest })
    }


}
