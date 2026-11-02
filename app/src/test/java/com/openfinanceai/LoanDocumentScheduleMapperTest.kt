package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanDocumentScheduleMapperTest {
    @Test fun preservesEachPdfInstallmentAmountIncludingChangedFirstAndLastEmis() {
        val rows = listOf(
            LoanDocumentScheduleRow(1, 100000, 7000, 1000, 8000),
            LoanDocumentScheduleRow(2, 93000, 7100, 900, 8000),
            LoanDocumentScheduleRow(3, 85900, 7300, 700, 8000),
            LoanDocumentScheduleRow(4, 78600, 78600, 500, 79100)
        )
        val mapped = LoanDocumentScheduleMapper.map(rows, "2026-11-05")
        assertEquals(listOf("2026-11-05", "2026-12-05", "2027-01-05", "2027-02-05"), mapped.map { it.date })
        assertEquals(listOf(8000L, 8000L, 8000L, 79100L), mapped.map { it.amount })
        assertEquals(103100L, mapped.sumOf { it.amount })
    }

    @Test fun anchorsEndOfMonthDatesWithoutDrifting() {
        val rows = (1..3).map { LoanDocumentScheduleRow(it, 10000, 900, 100, 1000) }
        val mapped = LoanDocumentScheduleMapper.map(rows, "2027-01-31")
        assertEquals(listOf("2027-01-31", "2027-02-28", "2027-03-31"), mapped.map { it.date })
    }

    @Test fun refusesToInventDatesForMissingOrInvalidStartDate() {
        val rows = listOf(LoanDocumentScheduleRow(1, 10000, 900, 100, 1000))
        assertTrue(LoanDocumentScheduleMapper.map(rows, null).isEmpty())
        assertTrue(LoanDocumentScheduleMapper.map(rows, "05/11/2026").isEmpty())
    }

    @Test fun refusesIncompleteOrOutOfOrderSchedules() {
        val rows = listOf(
            LoanDocumentScheduleRow(1, 10000, 900, 100, 1000),
            LoanDocumentScheduleRow(3, 9100, 900, 100, 1000)
        )
        assertTrue(LoanDocumentScheduleMapper.map(rows, "2026-11-05").isEmpty())
    }
    @Test fun prefersExplicitIrregularDueDatesFromEachPdfRow() {
        val rows = listOf(
            LoanDocumentScheduleRow(1, 30000, 9000, 300, 9300, 12.0, "2026-11-05"),
            LoanDocumentScheduleRow(2, 21000, 9000, 210, 9210, 12.0, "2026-12-07"),
            LoanDocumentScheduleRow(3, 12000, 9000, 120, 9120, 11.5, "2027-01-10")
        )
        val mapped = LoanDocumentScheduleMapper.map(rows, null)
        assertEquals(listOf("2026-11-05", "2026-12-07", "2027-01-10"), mapped.map { it.date })
        assertEquals(listOf(9300L, 9210L, 9120L), mapped.map { it.amount })
    }

    @Test fun refusesMixedExplicitAndMissingRowDatesRatherThanInventingDates() {
        val rows = listOf(
            LoanDocumentScheduleRow(1, 20000, 9000, 200, 9200, null, "2026-11-05"),
            LoanDocumentScheduleRow(2, 11000, 9000, 110, 9110)
        )
        assertTrue(LoanDocumentScheduleMapper.map(rows, "2026-12-05").isEmpty())
    }


}
