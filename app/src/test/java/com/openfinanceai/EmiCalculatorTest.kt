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
}
