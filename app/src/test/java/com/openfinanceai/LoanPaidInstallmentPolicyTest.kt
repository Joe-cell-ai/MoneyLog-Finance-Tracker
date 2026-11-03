package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LoanPaidInstallmentPolicyTest {
    @Test fun countsOnlyInstallmentsDueBeforeToday() {
        val rows = listOf(
            installment(1, "2026-10-04"),
            installment(2, "2026-11-02"),
            installment(3, "2026-11-03"),
            installment(4, "2026-12-03")
        )
        assertEquals(2, LoanPaidInstallmentPolicy.countPastDue(rows, LocalDate.parse("2026-11-03")))
    }

    @Test fun stopsAtFirstMissingOrInvalidDateRatherThanGuessing() {
        val rows = listOf(
            installment(1, "2026-10-04"),
            installment(2, "not-a-date"),
            installment(3, "2026-10-20")
        )
        assertEquals(1, LoanPaidInstallmentPolicy.countPastDue(rows, LocalDate.parse("2026-11-03")))
    }

    @Test fun rejectsLegalBoilerplateAsBorrowerOrLender() {
        val bad = "Bank name, and to the Lenders hereunder or in relation to any breach/default/contravention/non-"
        assertNull(LoanIdentitySanitizer.borrower(bad))
        assertNull(LoanIdentitySanitizer.lender(bad))
        assertNull(LoanIdentitySanitizer.borrower("Party shall be referred to individually as Parties"))
    }

    @Test fun retainsClearlyLabeledNames() {
        assertEquals("NELATOORI VIJAY KUMAR REDDY", LoanIdentitySanitizer.borrower("NELATOORI VIJAY KUMAR REDDY"))
        assertEquals("YES BANK Limited", LoanIdentitySanitizer.lender("YES BANK Limited"))
    }

    private fun installment(number: Int, date: String) = LoanDatedInstallment(
        date = date, amount = 100_000L, installmentNumber = number,
        openingBalance = 100_000L, principal = 90_000L, interest = 10_000L,
        closingBalance = 10_000L
    )
}
