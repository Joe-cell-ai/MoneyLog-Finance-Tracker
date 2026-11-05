package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoanMoneyTest {
    @Test fun parsesWholeAndFractionalRupeesWithoutRounding() {
        assertEquals(561938L, LoanMoney.parsePaise("5,619.38"))
        assertEquals(100000L, LoanMoney.parsePaise("1,000"))
        assertEquals(5L, LoanMoney.parsePaise("0.05"))
    }

    @Test fun rejectsValuesWithMoreThanTwoDecimalPlaces() {
        assertNull(LoanMoney.parsePaise("5,619.381"))
        assertNull(LoanMoney.parsePaise("-1.00"))
        assertNull(LoanMoney.parsePaise("not money"))
    }

    @Test fun wholeRupeeConversionIsExplicitAndOverflowSafe() {
        assertEquals(561900L, LoanMoney.rupeesToPaise(5619L))
        assertEquals(5619L, LoanMoney.paiseToRupeesExact(561900L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToDiscardFractionalPaiseWhenConvertingToRupees() {
        LoanMoney.paiseToRupeesExact(561938L)
    }

    @Test fun paiseTotalsDoNotLoseFractions() {
        assertEquals(1665103L, LoanMoney.sumPaise(listOf(561938L, 555057L, 548108L)))
    }

    @Test fun roundsCalculatedRupeesToNearestPaiseInsteadOfRejectingFloatingPrecision() {
        assertEquals(1_500_012L, LoanMoney.calculatedRupeesToPaise(15000.123456))
        assertEquals(403_540L, LoanMoney.calculatedRupeesToPaise(4035.40))
        assertEquals(0L, LoanMoney.calculatedRupeesToPaise(0.0))
        assertEquals(null, LoanMoney.calculatedRupeesToPaise(Double.NaN))
    }
}
