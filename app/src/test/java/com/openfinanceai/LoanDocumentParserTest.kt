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
        assertEquals((29205L * 100L), result.principal)
        assertEquals((3550L * 100L), result.emi)
        assertEquals(22.0, result.annualInterestRate!!, 0.0)
        assertEquals(31.65, result.apr!!, 0.0)
        assertEquals(9, result.tenureMonths)
        assertEquals("2026-11-05", result.repaymentStartDate)
        assertEquals((1292L * 100L), result.processingFee)
        assertEquals((2680L * 100L), result.totalInterest)
        assertEquals((32980L * 100L), result.declaredTotalRepayment)
        assertEquals((29205L * 100L), result.netDisbursedAmount)
        assertEquals("NA", result.prepaymentCharge)
        assertTrue(result.foreclosureCharge!!.contains("4.0%"))
        assertTrue(result.bounceCharge!!.contains("300"))
        assertTrue(result.bounceCharge!!.contains("every Mandate Bounce"))
        assertEquals("Flat charge of INR 300 on every Mandate Bounce (Upto- 2 Times)", result.bounceCharge)
    }

    @Test fun extractsEveryScheduleRowAndPreservesConflictingSourceAmount() {
        val result = LoanDocumentParser.parse(finnableKfs)
        assertEquals(9, result.scheduleRows.size)
        assertEquals((4842L * 100L), result.scheduleRows.first().installment)
        assertEquals((3550L * 100L), result.scheduleRows.first().principal + result.scheduleRows.first().interest)
        assertEquals((3485L * 100L), result.scheduleRows.last().installment)
        assertEquals((29205L * 100L), result.scheduleRows.sumOf { it.principal })
        assertEquals((2680L * 100L), result.scheduleRows.sumOf { it.interest })
        assertEquals(listOf((4842L * 100L), (3550L * 100L), (3550L * 100L), (3550L * 100L), (3550L * 100L), (3550L * 100L), (3550L * 100L), (3550L * 100L), (3485L * 100L)), result.scheduleRows.map { it.installment })
        assertEquals((33177L * 100L), result.scheduleRows.sumOf { it.installment })
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
        assertEquals(listOf((10300L * 100L), (10225L * 100L), (10117L * 100L)), result.scheduleRows.map { it.installment })
        assertEquals(listOf((300L * 100L), (225L * 100L), (117L * 100L)), result.scheduleRows.map { it.interest })
        assertEquals(listOf(12.0, 13.5, 14.0), result.scheduleRows.map { it.annualRate })
        assertEquals((30642L * 100L), result.scheduleRows.sumOf { it.installment })
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
        assertEquals(listOf((6700L * 100L), (6735L * 100L), (6969L * 100L)), result.scheduleRows.map { it.installment })
        assertEquals(listOf((200L * 100L), (135L * 100L), (69L * 100L)), result.scheduleRows.map { it.interest })
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
        assertEquals(listOf((9200L * 100L), (9110L * 100L)), result.scheduleRows.map { it.installment })
    }


    @Test fun parsesYesBankFiveAmountScheduleWithOpeningInstallmentPrincipalInterestClosingAndTwoDigitDates() {
        val statement = """
            Corporate & Registered oﬃce: YES BANK Limited, YES BANK House, Off Western Express Highway
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
        assertEquals((569000L * 100L), result.scheduleRows[0].openingPrincipal)
        assertEquals((7536L * 100L), result.scheduleRows[0].principal)
        assertEquals((5121L * 100L), result.scheduleRows[0].interest)
        assertEquals((12657L * 100L), result.scheduleRows[0].installment)
        assertEquals("2026-04-04", result.scheduleRows[2].dueDate)
        assertEquals((554422L * 100L), result.scheduleRows[2].openingPrincipal)
        assertEquals((547309L * 100L), result.scheduleRows[2].openingPrincipal - result.scheduleRows[2].principal)
    }


    @Test fun mapsInterestBeforePrincipalForDifferentLenderAmortizationLayout() {
        val statement = """
            APEX NATIONAL BANK
            Customer Name: Rohan Sharma
            Loan Amount: INR 10,000
            Agreement No: LN-TEST-1001
            Tenure: 3 Months
            Monthly EMI: INR 4,000
            Repayment Start Date: 10-Nov-2026
            Repayment End Date: 10-Jan-2027
            Interest Rate: 12.00% p.a. (Reducing)
            Official Amortization Timeline Statement
            Opening Principal Monthly EMI Interest Component Principal Component Closing Balance
            1 10000 4000 1000 3000 7000
            2 7000 4000 900 3100 3900
            3 3900 4000 100 3900 0
        """.trimIndent()
        val result = LoanDocumentParser.parse(statement)
        assertEquals(3, result.scheduleRows.size)
        assertEquals((10000L * 100L), result.principal)
        assertEquals((4000L * 100L), result.emi)
        assertEquals(12.0, result.annualInterestRate!!, 0.0)
        assertEquals(listOf((1000L * 100L), (900L * 100L), (100L * 100L)), result.scheduleRows.map { it.interest })
        assertEquals(listOf((3000L * 100L), (3100L * 100L), (3900L * 100L)), result.scheduleRows.map { it.principal })
        assertEquals(listOf((7000L * 100L), (3900L * 100L), (0L * 100L)), result.scheduleRows.map { it.closingPrincipal })
    }

    @Test fun preservesEveryPaiseInDecimalAmortizationSchedule() {
        val statement = """
            APEX NATIONAL BANK
            Loan Amount: INR 561938.00
            Tenure: 3 Months
            Monthly EMI: INR 12500.00
            Official Amortization Timeline Statement
            Opening Principal Monthly EMI Interest Component Principal Component Closing Balance
            1 561938.00 12500.00 5619.38 6880.62 555057.38
            2 555057.38 12500.00 5550.57 6949.43 548107.95
            3 548107.95 12500.00 5481.08 7018.92 541089.03
        """.trimIndent()
        val result = LoanDocumentParser.parse(statement)
        assertEquals(3, result.scheduleRows.size)
        assertEquals(56193800L, result.principal)
        assertEquals(56193800L, result.scheduleRows.first().openingPrincipal)
        assertEquals(561938L, result.scheduleRows.first().interest)
        assertEquals(688062L, result.scheduleRows.first().principal)
        assertEquals(1250000L, result.scheduleRows.first().installment)
        assertEquals(55505738L, result.scheduleRows.first().closingPrincipal)
        assertEquals(54108903L, result.scheduleRows.last().closingPrincipal)
        assertFalse(result.warnings.any { it.contains("paise-level decimal amounts") })
    }

    @Test fun parsesAllSixtyRowsFromUploadedApexDecimalPaiseStatement() {
        val statement = """
            APEX NATIONAL BANK
            Customer Name: Sample Borrower
            Loan Amount: INR 5,61,938.00
            Agreement No: TEST-APEX-60
            Tenure: 60 Months
            Repayment Start Date: 10-Nov-2026
            Repayment End Date: 10-Oct-2031
            Monthly EMI: INR 12,500.00
            Interest Rate: 12.00% p.a. (Reducing)
            Official Amortization Timeline Statement
            Opening Principal Monthly EMI Interest Component Principal Component Closing Balance
            1            561,938.00           12,500.00          5,619.38               6,880.62            555,057.38
            2            555,057.38           12,500.00          5,550.57               6,949.43            548,107.95
            3            548,107.95           12,500.00          5,481.08               7,018.92            541,089.03
            4            541,089.03           12,500.00          5,410.89               7,089.11            533,999.92
            5            533,999.92           12,500.00          5,340.00               7,160.00            526,839.92
            6            526,839.92           12,500.00          5,268.40               7,231.60            519,608.32
            7            519,608.32           12,500.00          5,196.08               7,303.92            512,304.40
            8            512,304.40           12,500.00          5,123.04               7,376.96            504,927.44
            9            504,927.44           12,500.00          5,049.27               7,450.73            497,476.71
            10            497,476.71           12,500.00          4,974.77               7,525.23            489,951.48
            11            489,951.48           12,500.00          4,899.51               7,600.49            482,350.99
            12            482,350.99           12,500.00          4,823.51               7,676.49            474,674.50
            13            474,674.50           12,500.00          4,746.74               7,753.26            466,921.24
            14            466,921.24           12,500.00          4,669.21               7,830.79            459,090.45
            15            459,090.45           12,500.00          4,590.90               7,909.10            451,181.35
            16            451,181.35           12,500.00          4,511.81               7,988.19            443,193.16
            17            443,193.16           12,500.00          4,431.93               8,068.07            435,125.09
            18            435,125.09           12,500.00          4,351.25               8,148.75            426,976.34
            19            426,976.34           12,500.00          4,269.76               8,230.24            418,746.10
            20            418,746.10           12,500.00          4,187.46               8,312.54            410,433.56
            21            410,433.56           12,500.00          4,104.34               8,395.66            402,037.90
            22            402,037.90           12,500.00          4,020.38               8,479.62            393,558.28
            23            393,558.28           12,500.00          3,935.58               8,564.42            384,993.86
            24            384,993.86           12,500.00          3,849.94               8,650.06            376,343.80
            25            376,343.80           12,500.00          3,763.44               8,736.56            367,607.24
            26        367,607.24        12,500.00         3,676.07              8,823.93          358,783.31
            27        358,783.31        12,500.00         3,587.83              8,912.17          349,871.14
            28        349,871.14        12,500.00         3,498.71              9,001.29          340,869.85
            29        340,869.85        12,500.00         3,408.70              9,091.30          331,778.55
            30        331,778.55        12,500.00         3,317.79              9,182.21          322,596.34
            31        322,596.34        12,500.00         3,225.96              9,274.04          313,322.30
            32        313,322.30        12,500.00         3,133.22              9,366.78          303,955.52
            33        303,955.52        12,500.00         3,039.56              9,460.44          294,495.08
            34        294,495.08        12,500.00         2,944.95              9,555.05          284,940.03
            35        284,940.03        12,500.00         2,849.40              9,650.60          275,289.43
            36        275,289.43        12,500.00         2,752.89              9,747.11          265,542.32
            37        265,542.32        12,500.00         2,655.42              9,844.58          255,697.74
            38        255,697.74        12,500.00         2,556.98              9,943.02          245,754.72
            39        245,754.72        12,500.00         2,457.55             10,042.45          235,712.27
            40        235,712.27        12,500.00         2,357.12             10,142.88          225,569.39
            41        225,569.39        12,500.00         2,255.69             10,244.31          215,325.08
            42        215,325.08        12,500.00         2,153.25             10,346.75          204,978.33
            43        204,978.33        12,500.00         2,049.78             10,450.22          194,528.11
            44        194,528.11        12,500.00         1,945.28             10,554.72          183,973.39
            45        183,973.39        12,500.00         1,839.73             10,660.27          173,313.12
            46        173,313.12        12,500.00         1,733.13             10,766.87          162,546.25
            47        162,546.25        12,500.00         1,625.46             10,874.54          151,671.71
            48        151,671.71        12,500.00         1,516.72             10,983.28          140,688.43
            49        140,688.43        12,500.00         1,406.88             11,093.12          129,595.31
            50        129,595.31        12,500.00         1,295.95             11,204.05          118,391.26
            51        118,391.26        12,500.00         1,183.91             11,316.09          107,075.17
            52        107,075.17        12,500.00         1,070.75             11,429.25           95,645.92
            53         95,645.92        12,500.00          956.46              11,543.54           84,102.38
            54         84,102.38        12,500.00          841.02              11,658.98           72,443.40
            55         72,443.40        12,500.00          724.43              11,775.57           60,667.83
            56         60,667.83        12,500.00          606.68              11,893.32           48,774.51
            57         48,774.51        12,500.00          487.75              12,012.25           36,762.26
            58         36,762.26        12,500.00          367.62              12,132.38           24,629.88
            59         24,629.88        12,500.00          246.30              12,253.70           12,376.18
            60         12,376.18        12,499.94          123.76              12,376.18             0.00
        """.trimIndent()
        val result = LoanDocumentParser.parse(statement)
        assertEquals(60, result.scheduleRows.size)
        assertEquals(56193800L, result.principal)
        assertEquals(56193800L, result.scheduleRows.sumOf { it.principal })
        assertEquals(0L, result.scheduleRows.last().closingPrincipal)
        assertEquals(18806194L, result.scheduleRows.sumOf { it.interest })
        assertEquals(74999994L, result.scheduleRows.sumOf { it.installment })
        assertEquals(561938L, result.scheduleRows.first().interest)
        assertEquals(688062L, result.scheduleRows.first().principal)
        assertEquals(1250000L, result.scheduleRows.first().installment)
        assertEquals(1249994L, result.scheduleRows.last().installment)
        val dated = LoanDocumentScheduleMapper.map(result.scheduleRows, result.repaymentStartDate)
        assertEquals(60, dated.size)
        val validation = LoanFinancialValidator.validate(
            dated.map { FinancialScheduleRow(it.installmentNumber, it.date, it.openingBalance, it.amount, it.principal, it.interest, it.closingBalance) },
            declaredTenure = 60, declaredPrincipal = result.principal
        )
        assertTrue(validation.errors.joinToString("\n"), validation.accepted)
        assertTrue(result.warnings.none { it.contains("paise-level decimal amounts") })
    }

    @Test fun illustrativeFinnableRowsValidateWithoutCertifyingConflictingDeclaredTotal() {
        val result = LoanDocumentParser.parse(finnableKfs)
        assertTrue(result.illustrativeSchedule)
        val rows = result.scheduleRows.map { row ->
            FinancialScheduleRow(row.installmentNumber, row.dueDate, row.openingPrincipal,
                row.installment, row.principal, row.interest, row.closingPrincipal)
        }
        val validation = LoanFinancialValidator.validate(
            rows,
            declaredTenure = result.tenureMonths,
            declaredPrincipal = result.principal,
            declaredInterest = result.totalInterest,
            declaredRepayment = null,
            processingFee = result.processingFee
        )
        assertTrue(validation.errors.joinToString("\n"), validation.accepted)
        assertTrue(validation.warnings.any { it.contains("processing fee") })
        assertEquals((33177L * 100L), validation.installmentTotal)
        assertEquals((32980L * 100L), result.declaredTotalRepayment)
    }

    @Test fun parsesAllSixtyRowsFromUploadedYesBankRepaymentStatement() {
        val statement = """
            Corporate & Registered oﬃce: YES BANK Limited, YES BANK House, Off Western Express Highway
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
        assertEquals("YES BANK Limited", result.lender)
        assertEquals("PLN002202247931", result.loanNumber)
        assertEquals("NELATOORI VIJAY KUMAR REDDY", result.borrowerName)
        assertEquals((569000L * 100L), result.principal)
        assertEquals((12657L * 100L), result.emi)
        assertEquals(60, result.tenureMonths)
        assertEquals(null, result.annualInterestRate) // Annual ROI is not stated in this source PDF.
        assertEquals("2031-01-04", result.repaymentEndDate)
        assertEquals("2026-02-04", result.repaymentStartDate)
        assertEquals(60, result.scheduleRows.size)
        assertEquals((1..60).toList(), result.scheduleRows.map { it.installmentNumber })
        assertEquals("2026-02-04", result.scheduleRows.first().dueDate)
        assertEquals("2031-01-04", result.scheduleRows.last().dueDate)
        assertEquals((569000L * 100L), result.scheduleRows.first().openingPrincipal)
        assertEquals((0L * 100L), result.scheduleRows.last().openingPrincipal - result.scheduleRows.last().principal)
        assertEquals((758402L * 100L), result.scheduleRows.sumOf { it.installment })
        assertEquals((569000L * 100L), result.scheduleRows.sumOf { it.principal })
        assertEquals((189402L * 100L), result.scheduleRows.sumOf { it.interest })
        val dated = LoanDocumentScheduleMapper.map(result.scheduleRows, result.repaymentStartDate)
        assertEquals(60, dated.size)
        val validation = LoanFinancialValidator.validate(
            dated.map { FinancialScheduleRow(it.installmentNumber, it.date, it.openingBalance, it.amount, it.principal, it.interest, it.closingBalance) },
            declaredTenure = 60, declaredPrincipal = result.principal, declaredInterest = (189402L * 100L),
            declaredRepayment = (758402L * 100L)
        )
        assertTrue(validation.errors.joinToString("\n"), validation.accepted)
    }


    @Test fun doesNotGuessLenderFromUnverifiedRegisteredOfficeName() {
        val statement = """
            Corporate & Registered office: RANDOM CAPITAL LIMITED
            Customer Name: Sample Borrower
            Loan Amount: INR 10,000
            Tenure: 2 Months
            Repayment Schedule
            1 10000 5000 100 5100
            2 5000 5000 50 5050
        """.trimIndent()
        val result = LoanDocumentParser.parse(statement)
        assertEquals(null, result.lender)
        assertTrue(result.warnings.any { it.contains("will not guess") })
    }

    @Test fun conflictingLenderLabelsRemainUnidentified() {
        val statement = """
            Lender: Alpha Finance Limited
            Name of the Regulated Entity: Beta Credit Limited
            Loan Amount: INR 10,000
            Repayment Schedule
            1 10000 5000 100 5100
            2 5000 5000 50 5050
        """.trimIndent()
        val result = LoanDocumentParser.parse(statement)
        assertEquals(null, result.lender)
        assertTrue(result.warnings.any { it.contains("conflicting lender names") })
    }

    @Test fun extractsYesBankOnlyFromItsVerifiedStatementIssuerLine() {
        val statement = """
            Corporate & Registered office: YES BANK Limited, YES BANK House
            Loan Amount(INR): INR 10,000
            Tenure: 2 Months
            Repayment Schedule
            1 10000 5000 100 5100
            2 5000 5000 50 5050
        """.trimIndent()
        assertEquals("YES BANK Limited", LoanDocumentParser.parse(statement).lender)
    }


    @Test fun parsesBranchNorthernArcLoanDetailsAndNineRowRepaymentTable() {
        val statement = """
            NAME: NELATOORI VIJAY KUMAR REDDY
            LOAN DETAILS
            Term loan of an amount INR 40000.0/- (Rupees Forty Thousand only).
            Lender 1: INR 4000.0/- | Lender 2: INR 36000.0/-
            Interest Rate 35.43% per annum calculated on reducing balance basis.
            Tenor (in months) 9 months
            Processing Charges INR 2360.0/- plus applicable taxes.
            REPAYMENT SCHEDULE In Indian Rupees
            Installment No. Outstanding Principal (in Rupees) Principal (in Rupees) Interest (in Rupees) Installment (in Rupees)
            1 40000.0 3955.07 1180.93 5136.0
            2 36044.93 4036.36 1099.64 5136.0
            3 32008.57 4159.5 976.5 5136.0
            4 27849.07 4368.62 767.38 5136.0
            5 23480.45 4419.67 716.33 5136.0
            6 19060.78 4573.26 562.74 5136.0
            7 14487.52 4694.02 441.98 5136.0
            8 9793.5 4846.86 289.14 5136.0
            9 4946.64 4946.64 150.91 5097.55
            "Lender 1" means Branch International Financial Services Private Limited, a company incorporated under the Companies Act.
        """.trimIndent()
        val result = LoanDocumentParser.parse(statement)
        assertEquals("NELATOORI VIJAY KUMAR REDDY", result.borrowerName)
        assertEquals("Branch International Financial Services Private Limited", result.lender)
        assertEquals(4_000_000L, result.principal)
        assertEquals(35.43, result.annualInterestRate!!, 0.0)
        assertEquals(9, result.tenureMonths)
        assertEquals(236_000L, result.processingFee)
        assertEquals(9, result.scheduleRows.size)
        assertEquals(5_136_00L, result.scheduleRows.first().installment)
        assertEquals(509_755L, result.scheduleRows.last().installment)
        assertEquals(4_000_000L, result.scheduleRows.sumOf { it.principal })
        assertEquals(618_555L, result.scheduleRows.sumOf { it.interest })
    }

    @Test fun parsesGenericPaymentScheduleExportWithRateAndEmiHolidayColumns() {
        val statement = """
            Customer : Nelatoori Vijay Kumar Reddy
            Loan Reference : XPLEFCLS000F032
            Amount Financed : 50,000.00
            Tenure(In Months) : 24
            Interest Rate : 21.49% Eff.
            Payment Schedule
            Schedule Details
            Instl Due Date Opening Principal Instl Amount Principal Interest Closing Principal Rate(%) Instl Type EMI Holiday Due
            1 05-Mar-26 50,000.00 2,581.00 2,022.00 559.00 47,978.00 21.49 EMI 0.00 N
            2 05-Apr-26 47,978.00 2,581.00 1,705.00 876.00 46,273.00 21.49 EMI 0.00 N
            3 05-May-26 46,273.00 2,581.00 1,764.00 817.00 44,509.00 21.49 EMI 0.00 N
        """.trimIndent()
        val result = LoanDocumentParser.parse(statement)
        assertEquals("XPLEFCLS000F032", result.loanNumber)
        assertEquals(5_000_000L, result.principal)
        assertEquals(24, result.tenureMonths)
        assertEquals(21.49, result.annualInterestRate!!, 0.0)
        assertEquals(3, result.scheduleRows.size)
        assertEquals("2026-03-05", result.scheduleRows.first().dueDate)
        assertEquals(258_100L, result.scheduleRows.first().installment)
        assertEquals(202_200L, result.scheduleRows.first().principal)
        assertEquals(55_900L, result.scheduleRows.first().interest)
        assertEquals(4_797_800L, result.scheduleRows.first().closingPrincipal)
        assertEquals(258_100L, result.emi)
    }

    @Test fun parsesHdbEarlySalaryScheduleOneSummaryWithoutInventingInstallmentSplits() {
        val statement = """
            HDB FINANCIAL SERVICES LIMITED in its capacity as a lender (hereinafter referred to as Lender 1)
            LOAN AGREEMENT NUMBER APPL00208750
            Name: Nelatoori Vijay Kumar Reddy
            FIXED RATE OF INTEREST/ IRR (P.A.) 30.00% p.a.
            ANNUAL PERCENTAGE RATE 46.29%
            LOAN AMOUNT (IN RUPEES) INR 45,000.00
            TENURE OF THE LOAN (in
            9
            months)
            PROCESSING CHARGE AMOUNT INR 2,124.00
            NUMBER OF INSTALLMENTS 9
            FIRST INSTALLMENT DATE 04-Aug-2026
            LAST INSTALLMENT DATE 04-Apr-2027
            EMI AMOUNT INR 5,646.00
            INTEREST AMOUNT INR 6,335.00
            TOTAL AMOUNT PAYABLE INR 50,810.00
        """.trimIndent()
        val result = LoanDocumentParser.parse(statement)
        assertEquals("HDB FINANCIAL SERVICES LIMITED", result.lender)
        assertEquals("APPL00208750", result.loanNumber)
        assertEquals("Nelatoori Vijay Kumar Reddy", result.borrowerName)
        assertEquals(4_500_000L, result.principal)
        assertEquals(30.0, result.annualInterestRate!!, 0.0)
        assertEquals(46.29, result.apr!!, 0.0)
        assertEquals(9, result.tenureMonths)
        assertEquals(212_400L, result.processingFee)
        assertEquals(564_600L, result.emi)
        assertEquals(633_500L, result.totalInterest)
        assertEquals(5_081_000L, result.declaredTotalRepayment)
        assertEquals("2026-08-04", result.repaymentStartDate)
        assertEquals("2027-04-04", result.repaymentEndDate)
        assertTrue(result.scheduleRows.isEmpty())
    }
}