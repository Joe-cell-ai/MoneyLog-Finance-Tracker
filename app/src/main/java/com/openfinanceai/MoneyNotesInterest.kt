package com.openfinanceai

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Live balance for a Money Note. Interest is simple interest, accrued daily against remaining principal. */
data class MoneyNoteBalance(
    val principalPaise: Long,
    val unpaidInterestPaise: Long
) {
    val totalPaise: Long get() = principalPaise + unpaidInterestPaise
}

/**
 * Annual rate is stored in basis points: 150 = 1.50% per year.
 * Interest accrues daily at annual rate / 365 against the remaining principal.
 * The start date is excluded and the as-of date is excluded, so a loan started
 * on the 3rd accrues one day by the 4th.
 */
internal fun calculateMoneyNoteBalance(
    note: MoneyNote,
    asOf: LocalDate = LocalDate.now()
): MoneyNoteBalance {
    if (note.interestRateBasisPoints <= 0) {
        val startDate = runCatching { LocalDate.parse(note.date) }.getOrNull()
        val datedPayments = note.payments.filter { payment ->
            val paymentDate = runCatching { LocalDate.parse(payment.date) }.getOrNull()
            paymentDate != null && (startDate == null || !paymentDate.isBefore(startDate)) && !paymentDate.isAfter(asOf)
        }
        val paid = (if (note.payments.isEmpty()) note.paidPaise else datedPayments.sumOf { it.amountPaise })
            .coerceAtMost(note.principalPaise)
        return MoneyNoteBalance((note.principalPaise - paid).coerceAtLeast(0L), 0L)
    }

    val start = runCatching { LocalDate.parse(note.date) }.getOrNull()
        ?: return MoneyNoteBalance(note.principalPaise, 0L)
    if (!asOf.isAfter(start)) return MoneyNoteBalance(note.principalPaise, 0L)

    var principal = note.principalPaise
    var interest = BigDecimal.ZERO
    var cursor = start
    val payments = note.payments
        .mapNotNull { payment ->
            val date = runCatching { LocalDate.parse(payment.date) }.getOrNull()
            if (date == null || date.isBefore(start) || date.isAfter(asOf)) null else payment to date
        }
        .sortedBy { it.second }

    fun accrueUntil(endExclusive: LocalDate) {
        while (cursor.isBefore(endExclusive)) {
            val daily = BigDecimal.valueOf(principal)
                .multiply(BigDecimal.valueOf(note.interestRateBasisPoints.toLong()))
                .divide(BigDecimal.valueOf(10_000L * 365L), 16, RoundingMode.HALF_UP)
            interest = interest.add(daily)
            cursor = cursor.plusDays(1)
        }
    }

    payments.forEach { (payment, paymentDate) ->
        if (paymentDate.isBefore(cursor)) return@forEach
        accrueUntil(paymentDate)
        var remaining = payment.amountPaise.coerceAtLeast(0L)
        when (payment.allocation) {
            MoneyNotePaymentAllocation.INTEREST_ONLY -> {
                val paidInterest = minOf(remaining, interest.setScale(0, RoundingMode.HALF_UP).longValueExact())
                interest = interest.subtract(BigDecimal.valueOf(paidInterest)).max(BigDecimal.ZERO)
            }
            MoneyNotePaymentAllocation.PRINCIPAL_ONLY -> {
                val paidPrincipal = minOf(remaining, principal)
                principal -= paidPrincipal
            }
            MoneyNotePaymentAllocation.BOTH -> {
                val interestDue = interest.setScale(0, RoundingMode.HALF_UP).longValueExact()
                val paidInterest = minOf(remaining, interestDue)
                interest = interest.subtract(BigDecimal.valueOf(paidInterest)).max(BigDecimal.ZERO)
                remaining -= paidInterest
                val paidPrincipal = minOf(remaining, principal)
                principal -= paidPrincipal
            }
        }
    }
    accrueUntil(asOf)
    val roundedInterest = interest.setScale(0, RoundingMode.HALF_UP).longValueExact().coerceAtLeast(0L)
    return MoneyNoteBalance(principal.coerceAtLeast(0L), roundedInterest)
}
