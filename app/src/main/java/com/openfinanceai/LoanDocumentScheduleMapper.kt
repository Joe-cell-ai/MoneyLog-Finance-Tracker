package com.openfinanceai

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal data class LoanDatedInstallment(
    val date: String,
    val amount: Long,
    val installmentNumber: Int
)

/**
 * Applies a lender-provided first due date to the exact monthly rows from its PDF.
 * The day-of-month is anchored to the first due date (e.g. 31 Jan -> 28 Feb -> 31 Mar),
 * and each row's own amount is preserved; no fixed EMI is substituted.
 */
internal object LoanDocumentScheduleMapper {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    fun map(
        rows: List<LoanDocumentScheduleRow>,
        firstDueDate: String?
    ): List<LoanDatedInstallment> {
        if (rows.isEmpty() || rows.size > 600) return emptyList()
        val firstDate = runCatching { LocalDate.parse(firstDueDate?.trim(), iso) }.getOrNull()
            ?: return emptyList()
        if (rows.map { it.installmentNumber } != (1..rows.size).toList()) return emptyList()
        if (rows.any { it.installment <= 0L || it.principal < 0L || it.interest < 0L }) return emptyList()
        return rows.mapIndexed { index, row ->
            val month = YearMonth.from(firstDate).plusMonths(index.toLong())
            val dueDate = month.atDay(minOf(firstDate.dayOfMonth, month.lengthOfMonth()))
            LoanDatedInstallment(dueDate.format(iso), row.installment, row.installmentNumber)
        }
    }
}
