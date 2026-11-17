package com.openfinanceai

import java.time.LocalDate

internal data class ParsedCreditCardStatement(
    val last4: String,
    val bankName: String,
    val dueDate: String,
    val totalPaise: Long,
    val minimumPaise: Long
)

internal data class ParsedCreditCardPayment(
    val last4: String,
    val amountPaise: Long
)

internal object CreditCardSmsParser {
    private fun findLastFour(body: String): String? =
        Regex("""(?i)(?:xx|x{2,}|\*{2,})(\d{4})""").find(body)?.groupValues?.get(1)
            ?: Regex("""(?i)(?:card|account|a/c)[^0-9]{0,12}(\d{4})""").find(body)?.groupValues?.get(1)

    private fun moneyToPaise(value: String): Long? = runCatching {
        java.math.BigDecimal(value.replace(",", "").trim()).movePointRight(2).longValueExact()
    }.getOrNull()

    fun parseStatement(body: String): ParsedCreditCardStatement? {
        val last4 = findLastFour(body) ?: return null
        if (!Regex("""(?i)(statement|total\s+amt|amount\s+due)""").containsMatchIn(body)) return null
        val bankName = Regex("""(?i)statement\s+for\s+(.+?)\s+credit\s+card(?:\s+no\.?)?""")
            .find(body)?.groupValues?.get(1)?.trim().orEmpty()
        val dueText = Regex("""(?i)due\s+on\s*:?\s*(\d{1,2}[-/]\d{1,2}[-/]\d{2,4})""")
            .find(body)?.groupValues?.get(1) ?: return null
        val parts = dueText.split('-', '/')
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val yearValue = parts[2].toIntOrNull() ?: return null
        val year = if (yearValue < 100) 2000 + yearValue else yearValue
        val dueDate = runCatching { LocalDate.of(year, month, day).toString() }.getOrNull() ?: return null

        val totalText = Regex(
            """(?i)(?:total\s+amt|total\s+amount|amount\s+due)\s*:?\s*(?:inr|rs\.?|₹)?\s*(?:dr\.?\s*)?([0-9,]+(?:\.[0-9]{1,2})?)"""
        ).find(body)?.groupValues?.get(1) ?: return null
        val minimumText = Regex(
            """(?i)min(?:imum)?\s+amt(?:ount)?\s+due\s*:?\s*(?:inr|rs\.?|₹)?\s*(?:dr\.?\s*)?([0-9,]+(?:\.[0-9]{1,2})?)"""
        ).find(body)?.groupValues?.get(1) ?: totalText
        val totalPaise = moneyToPaise(totalText) ?: return null
        val minimumPaise = moneyToPaise(minimumText) ?: return null
        if (totalPaise <= 0L || minimumPaise < 0L || minimumPaise > totalPaise) return null
        return ParsedCreditCardStatement(last4, bankName, dueDate, totalPaise, minimumPaise)
    }

    fun parsePayment(body: String): ParsedCreditCardPayment? {
        val last4 = findLastFour(body) ?: return null
        val successful = Regex("""(?i)(payment|bill).{0,35}(successful|received|credited|paid)""")
            .containsMatchIn(body)
        val failed = Regex("""(?i)(failed|declined|unsuccessful)""").containsMatchIn(body)
        if (!successful || failed) return null
        val amountText = Regex(
            """(?i)(?:inr|rs\.?|₹)\s*(?:dr\.?\s*)?([0-9,]+(?:\.[0-9]{1,2})?)"""
        ).find(body)?.groupValues?.get(1) ?: return null
        val amountPaise = moneyToPaise(amountText) ?: return null
        if (amountPaise <= 0L) return null
        return ParsedCreditCardPayment(last4, amountPaise)
    }
}
