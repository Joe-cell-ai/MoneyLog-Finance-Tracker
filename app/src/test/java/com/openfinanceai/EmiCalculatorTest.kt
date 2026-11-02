package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmiCalculatorTest {
    @Test
    fun reducingBalanceEmiMatchesStandardLoanCalculation() {
        assertEquals(11011L, calculateLoanEmi(1_000_000.0, 12.0, 240))
    }

    @Test
    fun zeroInterestEmiDividesPrincipalAcrossMonths() {
        assertEquals(8_333L, calculateLoanEmi(100_000.0, 0.0, 12))
    }

    @Test
    fun rejectsInvalidPrincipalRateAndTenure() {
        assertNull(calculateLoanEmi(0.0, 12.0, 12))
        assertNull(calculateLoanEmi(100_000.0, -1.0, 12))
        assertNull(calculateLoanEmi(100_000.0, 12.0, 0))
        assertNull(calculateLoanEmi(Double.NaN, 12.0, 12))
    }
    @Test
    fun pendingTotalSumsEveryUnpaidInstallment() {
        val installments = (1..5).map { "date-$it" to 3_750L }
        assertEquals(18_750L, calculatePendingEmiTotal(installments, emptySet()))
    }

    @Test
    fun pendingTotalExcludesPaidRowsAndUsesEditedInstallmentAmounts() {
        val installments = (1..5).map { "date-$it" to 3_750L }
        assertEquals(
            13_500L,
            calculatePendingEmiTotal(
                installments = installments,
                paidDates = setOf("date-1"),
                amountOverrides = mapOf("date-3" to 2_250L)
            )
        )
    }

    @Test
    fun importedScheduleCanSkipAlreadyPaidSequentialRows() {
        val installments = (1..5).map { "date-$it" to 3_750L }
        assertEquals(11_250L, calculatePendingEmiTotal(installments, emptySet(), alreadyPaidCount = 2L))
    }

    @Test
    fun pendingTotalUsesEveryVariablePdfInstallmentAmount() {
        val installments = listOf(
            "2026-11-05" to 4_842L,
            "2026-12-05" to 3_550L,
            "2027-01-05" to 3_550L,
            "2027-02-05" to 3_485L
        )
        assertEquals(15_427L, calculatePendingEmiTotal(installments, emptySet()))
    }

    @Test
    fun variablePdfPendingTotalExcludesPaidRowsAndHonoursThisInstallmentOverride() {
        val installments = listOf(
            "2026-11-05" to 4_842L,
            "2026-12-05" to 3_550L,
            "2027-01-05" to 3_550L,
            "2027-02-05" to 3_485L
        )
        assertEquals(
            10_335L,
            calculatePendingEmiTotal(
                installments = installments,
                paidDates = setOf("2026-11-05"),
                amountOverrides = mapOf("2027-01-05" to 3_300L)
            )
        )
    }


}
