package com.openfinanceai

/** UI-safe row for imported schedules. Balance columns may genuinely be absent in a lender PDF. */
internal data class LoanScheduleDisplayRow(
    val installmentNumber: Int,
    val openingBalance: Long?,
    val principal: Long,
    val interest: Long,
    val installmentAmount: Long,
    val annualRatePercent: Double?,
    val dueDate: String?,
    val closingBalance: Long?
)

internal object LoanScheduleDisplaySummary {
    fun remainingInterest(rows: List<LoanScheduleDisplayRow>, paidInstallments: Int): Long =
        rows.drop(paidInstallments.coerceIn(0, rows.size)).fold(0L) { total, row ->
            Math.addExact(total, row.interest)
        }

    /** Prefer the exact schedule balance; fall back to the loan's saved balance, never zero-fill. */
    fun outstandingBalance(
        rows: List<LoanScheduleDisplayRow>,
        paidInstallments: Int,
        savedBalance: Long
    ): Long {
        if (rows.isEmpty()) return savedBalance.coerceAtLeast(0L)
        val paid = paidInstallments.coerceIn(0, rows.size)
        val scheduleBalance = when {
            paid == 0 -> rows.first().openingBalance
            paid == rows.size -> rows.last().closingBalance
            else -> rows[paid - 1].closingBalance ?: rows.drop(paid).firstNotNullOfOrNull { it.openingBalance }
        }
        return (scheduleBalance ?: savedBalance).coerceAtLeast(0L)
    }
}
