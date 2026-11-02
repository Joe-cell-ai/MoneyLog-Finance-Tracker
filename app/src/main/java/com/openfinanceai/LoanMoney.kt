package com.openfinanceai

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Exact INR arithmetic for lender repayment schedules.
 *
 * All persisted and calculated schedule amounts use integer paise (1 rupee = 100 paise).
 * BigDecimal is used only at text/formatting boundaries; Double is never used for money.
 */
internal object LoanMoney {
    const val PAISE_PER_RUPEE = 100L

    /** Parses a lender amount such as "5,619.38" into exact paise. */
    fun parsePaise(raw: String): Long? = runCatching {
        val normalized = raw.trim()
            .replace("₹", "")
            .replace("INR", "", ignoreCase = true)
            .replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true)
            .replace(",", "")
            .trim()
        require(normalized.isNotEmpty())
        BigDecimal(normalized)
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
            .also { require(it >= 0L) }
    }.getOrNull()

    fun rupeesToPaise(rupees: Long): Long = Math.multiplyExact(rupees, PAISE_PER_RUPEE)

    fun paiseToRupeesExact(paise: Long): Long {
        require(paise % PAISE_PER_RUPEE == 0L) { "Amount contains paise and cannot be represented as whole rupees." }
        return paise / PAISE_PER_RUPEE
    }

    fun formatPaise(paise: Long): String {
        val amount = BigDecimal.valueOf(paise, 2)
        return NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            roundingMode = RoundingMode.UNNECESSARY
        }.format(amount)
    }

    fun sumPaise(values: Iterable<Long>): Long =
        values.fold(0L) { total, value -> Math.addExact(total, value) }
}
