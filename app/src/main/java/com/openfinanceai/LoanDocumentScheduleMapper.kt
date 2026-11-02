package com.openfinanceai

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * A UI-ready instalment. Financial values are copied from the source PDF row;
 * this mapper never recomputes principal, interest, EMI, or balances.
 */
internal data class LoanDatedInstallment(
    val date: String,
    val amount: Long,
    val installmentNumber: Int,
    val openingBalance: Long,
    val principal: Long,
    val interest: Long,
    val closingBalance: Long?,
    val annualRatePercent: Double? = null
)

internal object LoanDocumentScheduleMapper {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    fun map(
        rows: List<LoanDocumentScheduleRow>,
        firstDueDate: String?
    ): List<LoanDatedInstallment> {
        if (rows.isEmpty() || rows.size > 600) return emptyList()
        if (rows.map { it.installmentNumber } != (1..rows.size).toList()) return emptyList()
        if (rows.any {
                it.installment <= 0L || it.openingPrincipal < 0L ||
                    it.principal < 0L || it.interest < 0L ||
                    (it.closingPrincipal != null && it.closingPrincipal < 0L)
            }) return emptyList()

        val hasExplicitDates = rows.any { !it.dueDate.isNullOrBlank() }
        val dates = if (hasExplicitDates) {
            // A partially extracted date column is unsafe: never fill gaps with guessed dates.
            rows.map { row ->
                runCatching { LocalDate.parse(row.dueDate?.trim(), iso) }.getOrNull()
                    ?: return emptyList()
            }
        } else {
            val firstDate = runCatching { LocalDate.parse(firstDueDate?.trim(), iso) }.getOrNull()
                ?: return emptyList()
            rows.mapIndexed { index, _ ->
                val month = YearMonth.from(firstDate).plusMonths(index.toLong())
                month.atDay(minOf(firstDate.dayOfMonth, month.lengthOfMonth()))
            }
        }

        if (dates.distinct().size != dates.size ||
            dates.zipWithNext().any { (a, b) -> !b.isAfter(a) }) return emptyList()

        return rows.mapIndexed { index, row ->
            LoanDatedInstallment(
                date = dates[index].format(iso),
                amount = row.installment,
                installmentNumber = row.installmentNumber,
                openingBalance = row.openingPrincipal,
                principal = row.principal,
                interest = row.interest,
                closingBalance = row.closingPrincipal,
                annualRatePercent = row.annualRate
            )
        }
    }
}
