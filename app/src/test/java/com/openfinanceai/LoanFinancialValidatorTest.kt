package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanFinancialValidatorTest {
    @Test fun acceptsConsistentThreeMonthSchedule() {
        val rows = listOf(
            FinancialScheduleRow(1, "2026-01-04", 10000, 3500, 3000, 500, 7000),
            FinancialScheduleRow(2, "2026-02-04", 7000, 3500, 3200, 300, 3800),
            FinancialScheduleRow(3, "2026-03-04", 3800, 3900, 3800, 100, 0)
        )
        val result = LoanFinancialValidator.validate(rows, 3, 10000, 900, 10900)
        assertTrue(result.accepted)
        assertEquals(10000L, result.principalTotal)
        assertEquals(900L, result.interestTotal)
        assertEquals(10900L, result.installmentTotal)
    }

    @Test fun rejectsPrincipalInterestMismatch() {
        val rows = listOf(FinancialScheduleRow(1, "2026-01-04", 10000, 3500, 3000, 400, 7000))
        val result = LoanFinancialValidator.validate(rows)
        assertFalse(result.accepted)
        assertTrue(result.errors.any { it.contains("principal + interest") })
    }

    @Test fun rejectsBrokenBalanceChainAndTenureMismatch() {
        val rows = listOf(
            FinancialScheduleRow(1, "2026-01-04", 10000, 3500, 3000, 500, 7000),
            FinancialScheduleRow(2, "2026-02-04", 6900, 3500, 3200, 300, 3700)
        )
        val result = LoanFinancialValidator.validate(rows, declaredTenure = 3)
        assertFalse(result.accepted)
        assertTrue(result.errors.any { it.contains("continuity") })
        assertTrue(result.errors.any { it.contains("declares 3") })
    }

    @Test fun unknownRatesAndMissingDatesAreNotInvented() {
        val rows = listOf(FinancialScheduleRow(1, null, null, 1000, 900, 100, null))
        val result = LoanFinancialValidator.validate(rows)
        assertTrue(result.accepted)
        assertTrue(result.warnings.any { it.contains("due dates are missing") })
        assertTrue(result.warnings.any { it.contains("balance is not available") })
    }

    @Test fun rejectsDeclaredTotalMismatch() {
        val rows = listOf(FinancialScheduleRow(1, "2026-01-04", 1000, 1100, 1000, 100, 0))
        val result = LoanFinancialValidator.validate(rows, declaredRepayment = 1200)
        assertFalse(result.accepted)
        assertTrue(result.errors.any { it.contains("does not reconcile") })
    }
}
