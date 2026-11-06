package com.openfinanceai

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/**
 * Pure parser for bank SMS alerts. It deliberately returns a reviewable candidate;
 * callers must not silently save parsed messages as confirmed transactions.
 */
internal data class ParsedBankSmsTransaction(
    val amount: Long,
    val exactAmount: String,
    val income: Boolean,
    val date: String,
    val merchant: String?
)

internal object BankSmsParser {
    private val currencyAmount = Regex("""(?i)(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val debit = Regex("""(?i)\b(debited|debit|withdrawn|withdrawal|spent|paid|purchase|sent|transferred to|upi txn debit)\b""")
    private val credit = Regex("""(?i)\b(credited|credit|received|deposited|refund received|cashback received|transferred from)\b""")
    private val ignored = Regex("""(?i)\b(otp|one[- ]time password|verification code|failed|declined|unsuccessful|not debited|not credited|will be debited|mandate created|mandate registered|autopay scheduled|standing instruction created)\b""")
    private val bankDate = Regex("""(?i)\bon\s+(\d{1,2}[- ](?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[- ]\d{2,4})\b""")
    private val isoDate = Regex("""\b(20\d{2}-\d{2}-\d{2})\b""")
    private val scheduledWords = Regex("""(?i)\b(autopay|auto-pay|mandate|scheduled|standing instruction|create\+mandate|unique mandate number)\b""")

    fun parse(body: String, receivedDate: LocalDate): ParsedBankSmsTransaction? {
        if (body.isBlank() || ignored.containsMatchIn(body)) return null

        val matches = currencyAmount.findAll(body).toList()
        if (matches.isEmpty()) return null

        // Prefer amounts immediately associated with a debit/credit verb. This helps
        // avoid picking a balance or limit amount elsewhere in the same message.
        val amountMatch = matches.firstOrNull { match ->
            val prefix = body.substring(0, match.range.first).takeLast(65)
            Regex("""(?i)\b(debited|debit|withdrawn|withdrawal|spent|paid|purchase|sent|transferred|credited|credit|received|deposited)\b.{0,35}$""")
                .containsMatchIn(prefix)
        } ?: matches.first()

        val prefix = body.substring(0, amountMatch.range.first)
        val debitBefore = debit.containsMatchIn(prefix)
        val creditBefore = credit.containsMatchIn(prefix)
        val isIncome = when {
            creditBefore && !debitBefore -> true
            debitBefore && !creditBefore -> false
            else -> {
                val hasDebit = debit.containsMatchIn(body)
                val hasCredit = credit.containsMatchIn(body)
                if (hasDebit == hasCredit) return null
                hasCredit
            }
        }

        val exact = amountMatch.groupValues[1].replace(",", "").toBigDecimalOrNull() ?: return null
        if (exact <= BigDecimal.ZERO) return null
        val rounded = try {
            exact.setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: ArithmeticException) {
            return null
        }

        val transactionDate = parseDate(body) ?: receivedDate
        // Future-dated autopay/mandate notices are instructions, not completed
        // payments. Do not create a candidate that could be imported as a real debit.
        if (transactionDate.isAfter(receivedDate) && scheduledWords.containsMatchIn(body)) return null

        return ParsedBankSmsTransaction(
            amount = rounded,
            exactAmount = exact.stripTrailingZeros().toPlainString(),
            income = isIncome,
            date = transactionDate.toString(),
            merchant = extractMerchant(body, amountMatch.range.first, amountMatch.range.last + 1)
        )
    }

    private fun parseDate(body: String): LocalDate? {
        val rawBankDate = bankDate.find(body)?.groupValues?.getOrNull(1)
        if (rawBankDate != null) {
            val normalized = rawBankDate.replace(' ', '-')
            val formats = listOf("d-MMM-yy", "d-MMM-yyyy", "dd-MMM-yy", "dd-MMM-yyyy")
            for (format in formats) {
                val parsed = runCatching {
                    LocalDate.parse(
                        normalized,
                        DateTimeFormatter.ofPattern(format, Locale.ENGLISH)
                            .withResolverStyle(ResolverStyle.SMART)
                    )
                }.getOrNull()
                if (parsed != null) return parsed
            }
        }
        return isoDate.find(body)?.groupValues?.getOrNull(1)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
    }

    private fun extractMerchant(body: String, amountStart: Int, amountEnd: Int): String? {
        // Some banks/apps put the payee before the amount (for example,
        // "Paid to AMAZON for Rs. 499"). Check explicit payee labels and
        // directional phrases on the complete message before using the old
        // after-amount extraction.
        val beforeAmount = body.substring(0, amountEnd.coerceIn(0, body.length))
        val currencyStart = currencyAmount.findAll(body).firstOrNull()?.range?.first ?: body.length
        val preAmountText = body.substring(0, currencyStart.coerceIn(0, body.length))
        val labeled = Regex(
            """(?i)\b(?:merchant|payee|beneficiary)\s*[:=-]\s*([^.;,\n]+)"""
        ).find(body)?.groupValues?.getOrNull(1)
        val beforeAmountDirectional = Regex(
            """(?i)\b(?:paid\s+to|payment\s+to|purchase\s+at|sent\s+to|transferred\s+to|to|at|towards)\s+(.+?)(?=\s+(?:for\s+)?(?:₹|rs\.?|inr)\s*[0-9]|[.;,\n]|$)"""
        ).find(preAmountText)?.groupValues?.getOrNull(1)

        val afterAmount = body.substring(amountEnd.coerceIn(0, body.length))
            .replace(Regex("""(?i)\bUPI\s*[:#].*$"""), "")
            .replace(Regex("""(?i)\b(call|customer care|sms block)\b.*$"""), "")
            .replace(Regex("""(?i)\b(unique mandate number)\b.*$"""), "")
            .trim(' ', '.', ',', ';', ':', '-', '\n', '\r')

        val fromDate = Regex("""(?i)\bon\s+\d{1,2}[- ](?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[- ]\d{2,4}\s+from\s+([^.;]+)""")
            .find(afterAmount)?.groupValues?.getOrNull(1)?.trim()

        val directional = Regex("""(?i)\b(?:to|at|from|towards)\s+(.+?)(?=\s+(?:on\s+\d{1,2}[- ]|UPI\b|ref(?:erence)?\b|txn\b|transaction\b|for dispute\b|is successful\b)|[.;,\n]|$)""")
            .find(afterAmount)?.groupValues?.getOrNull(1)?.trim()

        val afterSemicolon = afterAmount.substringAfter(';', "").trim()
        val fallback = afterSemicolon
            .replace(Regex("""(?i)\b(?:was\s+)?(?:credited|debited)\b.*$"""), "")
            .replace(Regex("""(?i)\b(?:payment|transaction)\s+(?:is\s+)?(?:successful|completed)\b.*$"""), "")
            .trim(' ', '.', ',', ';', ':', '-')

        val candidate = (labeled ?: beforeAmountDirectional ?: fromDate ?: directional ?: fallback)
            ?.replace(Regex("""(?i)\s+on\s+\d{1,2}[- ](?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[- ]\d{2,4}.*$"""), "")
            ?.replace(Regex("""(?i)\s+(?:via|using)\s+(?:upi|imps|neft|rtgs).*$"""), "")
            ?.trim(' ', '.', ',', ';', ':', '-')
            ?.replace(Regex("""\s+"""), " ")
            ?.take(60)
            ?: return null

        if (candidate.isBlank()) return null
        if (Regex("""(?i)^(autopay|auto-pay|mandate|upi|payment|transaction|icici bank|bank account|savings account|current account|acct|account|available balance)$""")
                .matches(candidate)) return null
        // Reject obvious reference/account fragments rather than treating them as a merchant.
        if (Regex("""(?i)^(?:\d{5,}|[a-z0-9]{8,}@(?:ibl|ybl|ok\w+|paytm))$""").matches(candidate)) return null
        return candidate
    }
}
