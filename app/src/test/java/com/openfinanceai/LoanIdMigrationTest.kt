package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanIdMigrationTest {
    private val twoLoansSameBank = """[
        {"lender":"HDFC Bank","balance":100000,"emi":5000,"due":"05","annualRate":10.5,"originalPrincipal":150000,"paymentsMade":10,"totalPayments":36,"borrowerName":"A","agreementNumber":"AG1","loanNumber":"LN1","startDate":"2025-01-05"},
        {"lender":"hdfc bank ","balance":20000,"emi":2000,"due":"10"}
    ]"""

    @Test fun parsingUsesTheSameDefaultsAsTheLegacyLoader() {
        val loans = LoanIdMigration.parseLegacy(twoLoansSameBank)!!
        assertEquals(2, loans.size)
        val second = loans[1]
        assertEquals(20000L, second.originalPrincipal)
        assertEquals("10", second.startDate)
        assertEquals("", second.agreementNumber)
        assertEquals(0.0, second.annualRate, 0.0)
    }

    @Test fun twoLoansFromTheSameBankGetDifferentPermanentIds() {
        val plan = LoanIdMigration.plan(LoanIdMigration.parseLegacy(twoLoansSameBank)!!)
        assertEquals(2, plan.loans.map { it.loanId }.toSet().size)
    }

    @Test fun idsAreDeterministicSoARetryReusesThem() {
        val legacy = LoanIdMigration.parseLegacy(twoLoansSameBank)!!
        assertEquals(LoanIdMigration.plan(legacy).loans.map { it.loanId }, LoanIdMigration.plan(legacy).loans.map { it.loanId })
    }

    @Test fun ambiguousLenderKeyIsNeverAssignedToEitherLoan() {
        val plan = LoanIdMigration.plan(LoanIdMigration.parseLegacy(twoLoansSameBank)!!)
        assertTrue(plan.lenderToLoanId.isEmpty())
        assertEquals(setOf("hdfc bank"), plan.ambiguousLenders)
    }

    @Test fun aUniqueLenderKeyCanBeAssignedSafely() {
        val legacy = LoanIdMigration.parseLegacy("""[{"lender":"ICICI Bank","balance":1000,"emi":100,"due":"01"}]""")!!
        val plan = LoanIdMigration.plan(legacy)
        assertEquals(plan.loans.single().loanId, plan.lenderToLoanId["icici bank"])
        assertTrue(plan.ambiguousLenders.isEmpty())
    }

    @Test fun unreadableJsonIsRejectedInsteadOfGuessing() {
        assertNull(LoanIdMigration.parseLegacy("not json"))
        assertNull(LoanIdMigration.parseLegacy("""[{"lender":"X"}]"""))
        assertNull(LoanIdMigration.parseLegacy(null))
    }

    @Test fun verifyAcceptsExactRecordsAndRejectsMissingOrChangedOnes() {
        val plan = LoanIdMigration.plan(LoanIdMigration.parseLegacy(twoLoansSameBank)!!)
        val records = plan.loans.map { it.toRecord() }
        assertTrue(LoanIdMigration.verify(plan, records).isEmpty())
        assertTrue(LoanIdMigration.verify(plan, records.drop(1)).isNotEmpty())
        assertTrue(LoanIdMigration.verify(plan, listOf(records[0].copy(balance = 1L), records[1])).isNotEmpty())
    }

    @Test fun recordsPreserveEveryLegacyField() {
        val legacy = LoanIdMigration.parseLegacy(twoLoansSameBank)!!
        val record = LoanIdMigration.plan(legacy).loans[0].toRecord()
        assertEquals("HDFC Bank", record.lender)
        assertEquals(100000L, record.balance)
        assertEquals(150000L, record.originalPrincipal)
        assertEquals(10L, record.paymentsMade)
        assertEquals("LN1", record.loanNumber)
        assertNotEquals("", record.loanId)
    }
}
