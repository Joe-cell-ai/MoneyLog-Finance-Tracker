package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanDocumentScheduleMapperTest {
    @Test fun preservesPdfFinancialBreakdownAndEachVariableInstallment() {
        val rows = listOf(
            LoanDocumentScheduleRow(1, 10000000L, 700000L, 100000L, 800000L, null, "2026-11-05", 9300000L),
            LoanDocumentScheduleRow(2, 9300000L, 710000L, 90000L, 800000L, null, "2026-12-05", 8590000L),
            LoanDocumentScheduleRow(3, 8590000L, 730000L, 70000L, 800000L, null, "2027-01-05", 7860000L),
            LoanDocumentScheduleRow(4, 7860000L, 7860000L, 50000L, 7910000L, null, "2027-02-05", 0L)
        )
        val mapped = LoanDocumentScheduleMapper.map(rows, null)
        assertEquals(listOf("2026-11-05", "2026-12-05", "2027-01-05", "2027-02-05"), mapped.map { it.date })
        assertEquals(listOf((8000L * 100L), (8000L * 100L), (8000L * 100L), (79100L * 100L)), mapped.map { it.amount })
        assertEquals(listOf((100000L * 100L), (93000L * 100L), (85900L * 100L), (78600L * 100L)), mapped.map { it.openingBalance })
        assertEquals(listOf((7000L * 100L), (7100L * 100L), (7300L * 100L), (78600L * 100L)), mapped.map { it.principal })
        assertEquals(listOf((1000L * 100L), (900L * 100L), (700L * 100L), (500L * 100L)), mapped.map { it.interest })
        assertEquals(listOf((93000L * 100L), (85900L * 100L), (78600L * 100L), (0L * 100L)), mapped.map { it.closingBalance })
        assertEquals((103100L * 100L), mapped.sumOf { it.amount })
    }

    @Test fun doesNotInventClosingBalanceWhenPdfDoesNotStateIt() {
        val rows = listOf(
            LoanDocumentScheduleRow(1, 1000000L, 90000L, 10000L, 100000L, null, "2026-11-05"),
            LoanDocumentScheduleRow(2, 910000L, 910000L, 9000L, 919000L, null, "2026-12-05")
        )
        val mapped = LoanDocumentScheduleMapper.map(rows, null)
        assertEquals(listOf(null, null), mapped.map { it.closingBalance })
    }

    @Test fun keepsSourceAnnualRateWhenPresentAndUnknownWhenAbsent() {
        val rows = listOf(
            LoanDocumentScheduleRow(1, 3000000L, 900000L, 30000L, 930000L, 12.0, "2026-11-05", 2100000L),
            LoanDocumentScheduleRow(2, 2100000L, 900000L, 21000L, 921000L, null, "2026-12-07", 1200000L)
        )
        val mapped = LoanDocumentScheduleMapper.map(rows, null)
        assertEquals(listOf(12.0, null), mapped.map { it.annualRatePercent })
    }

    @Test fun anchorsEndOfMonthDatesWithoutDrifting() {
        val rows = (1..3).map { LoanDocumentScheduleRow(it, 10000, 900, 100, 1000) }
        val mapped = LoanDocumentScheduleMapper.map(rows, "2027-01-31")
        assertEquals(listOf("2027-01-31", "2027-02-28", "2027-03-31"), mapped.map { it.date })
    }

    @Test fun refusesToInventDatesForMissingOrInvalidStartDate() {
        val rows = listOf(LoanDocumentScheduleRow(1, 1000000L, 90000L, 10000L, 100000L))
        assertTrue(LoanDocumentScheduleMapper.map(rows, null).isEmpty())
        assertTrue(LoanDocumentScheduleMapper.map(rows, "05/11/2026").isEmpty())
    }

    @Test fun refusesIncompleteOrOutOfOrderSchedules() {
        val rows = listOf(
            LoanDocumentScheduleRow(1, 1000000L, 90000L, 10000L, 100000L),
            LoanDocumentScheduleRow(3, 910000L, 90000L, 10000L, 100000L)
        )
        assertTrue(LoanDocumentScheduleMapper.map(rows, "2026-11-05").isEmpty())
    }

    @Test fun prefersExplicitIrregularDueDatesFromEachPdfRow() {
        val rows = listOf(
            LoanDocumentScheduleRow(1, 3000000L, 900000L, 30000L, 930000L, 12.0, "2026-11-05"),
            LoanDocumentScheduleRow(2, 2100000L, 900000L, 21000L, 921000L, 12.0, "2026-12-07"),
            LoanDocumentScheduleRow(3, 1200000L, 900000L, 12000L, 912000L, 11.5, "2027-01-10")
        )
        val mapped = LoanDocumentScheduleMapper.map(rows, null)
        assertEquals(listOf("2026-11-05", "2026-12-07", "2027-01-10"), mapped.map { it.date })
        assertEquals(listOf((9300L * 100L), (9210L * 100L), (9120L * 100L)), mapped.map { it.amount })
        assertEquals(listOf((300L * 100L), (210L * 100L), (120L * 100L)), mapped.map { it.interest })
    }

    @Test fun refusesMixedExplicitAndMissingRowDatesRatherThanInventingDates() {
        val rows = listOf(
            LoanDocumentScheduleRow(1, 2000000L, 900000L, 20000L, 920000L, null, "2026-11-05"),
            LoanDocumentScheduleRow(2, 1100000L, 900000L, 11000L, 911000L)
        )
        assertTrue(LoanDocumentScheduleMapper.map(rows, "2026-12-05").isEmpty())
    }
}
