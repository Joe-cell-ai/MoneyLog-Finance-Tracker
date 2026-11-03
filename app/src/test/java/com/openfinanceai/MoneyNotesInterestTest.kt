package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MoneyNotesInterestTest {
    private fun note(
        principalRupees: Long = 90_000L,
        rateBasisPoints: Int = 150,
        payments: List<MoneyNotePayment> = emptyList()
    ) = MoneyNote(
        id = "test",
        direction = MoneyNoteDirection.I_NEED_TO_PAY,
        personName = "Test lender",
        principalPaise = principalRupees * 100L,
        date = "2026-10-01",
        payments = payments,
        interestRateBasisPoints = rateBasisPoints
    )

    @Test
    fun oneFullCalendarMonthAccruesTheEnteredMonthlyRate() {
        val balance = calculateMoneyNoteBalance(note(), LocalDate.parse("2026-11-01"))
        assertEquals(9_000_000L, balance.principalPaise)
        assertEquals(135_000L, balance.unpaidInterestPaise) // ₹1,350 at 1.5%
    }

    @Test
    fun interestOnlyGoldLoanPaymentDoesNotReducePrincipal() {
        val payment = MoneyNotePayment(
            amountPaise = 135_000L,
            date = "2026-11-01",
            allocation = MoneyNotePaymentAllocation.INTEREST_ONLY
        )
        val balance = calculateMoneyNoteBalance(note(payments = listOf(payment)), LocalDate.parse("2026-11-01"))
        assertEquals(9_000_000L, balance.principalPaise)
        assertEquals(0L, balance.unpaidInterestPaise)
    }

    @Test
    fun principalOnlyPaymentReducesFutureInterestBase() {
        val payment = MoneyNotePayment(
            amountPaise = 1_000_000L,
            date = "2026-10-16",
            allocation = MoneyNotePaymentAllocation.PRINCIPAL_ONLY
        )
        val balance = calculateMoneyNoteBalance(note(payments = listOf(payment)), LocalDate.parse("2026-11-01"))
        assertEquals(8_000_000L, balance.principalPaise)
        assert(balance.unpaidInterestPaise in 130_000L..136_000L)
    }
}
