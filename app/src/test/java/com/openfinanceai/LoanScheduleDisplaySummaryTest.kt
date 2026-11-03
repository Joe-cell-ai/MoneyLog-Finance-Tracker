package com.openfinanceai

import org.junit.Assert.assertEquals
import org.junit.Test

class LoanScheduleDisplaySummaryTest {
    private val rows = listOf(
        LoanScheduleDisplayRow(1, 100_000, 8_000, 2_000, 10_000, null, "2026-01-05", null),
        // Missing opening balance must not discard principal/interest/EMI data.
        LoanScheduleDisplayRow(2, null, 8_200, 1_800, 10_000, null, "2026-02-05", 83_800),
        LoanScheduleDisplayRow(3, 83_800, 8_400, 1_600, 10_000, null, "2026-03-05", 75_400)
    )

    @Test fun sumsRemainingInterestFromScheduleEvenWhenABalanceIsMissing() {
        assertEquals(5_400L, LoanScheduleDisplaySummary.remainingInterest(rows, 0))
        assertEquals(3_400L, LoanScheduleDisplaySummary.remainingInterest(rows, 1))
    }

    @Test fun usesNextKnownScheduleBalanceOrSavedBalanceWhenCurrentRowBalanceIsMissing() {
        assertEquals(100_000L, LoanScheduleDisplaySummary.outstandingBalance(rows, 0, 50_000))
        assertEquals(83_800L, LoanScheduleDisplaySummary.outstandingBalance(rows, 1, 50_000))
        assertEquals(75_400L, LoanScheduleDisplaySummary.outstandingBalance(rows, 3, 50_000))
    }

    @Test fun fallsBackToSavedBalanceWhenNoScheduleRowsExist() {
        assertEquals(47_500L, LoanScheduleDisplaySummary.outstandingBalance(emptyList(), 0, 47_500))
    }
}
