package com.openfinanceai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Shared, idempotent SMS persistence used by the live receiver and hourly inbox sync.
 * All work is local; entries are regular transactions, not review-queue items.
 */
internal object SmsTransactionImporter {
    private val lock = Any()

    private data class MerchantRule(val category: String, val pattern: Regex)

    // Match recognizable merchants independently of how the bank formats the SMS.
    // Keep this list local and deterministic so categorisation works offline.
    private val merchantRules = listOf(
        MerchantRule("Shopping", Regex("""(?i)\b(amazon|amazon\s+pay|flipkart|myntra|ajio|meesho|nykaa|tata\s*cliq|jiomart|croma|reliance\s+digital|decathlon|shoppers\s+stop|lifestyle|westside|ikea|zara|h\s*&\s*m)\b""")),
        MerchantRule("Food & Dining", Regex("""(?i)\b(swiggy|zomato|dominos|domino'?s|mcdonald'?s|kfc|pizza\s*hut|burger\s*king|subway|starbucks|cafe\s*coffee\s*day|ccd|behrouz|faasos|eatclub)\b""")),
        MerchantRule("Groceries", Regex("""(?i)\b(bigbasket|bbnow|blinkit|zepto|instamart|dmart|d\s*mart|more\s+supermarket|reliance\s+fresh|nature'?s\s+basket)\b""")),
        MerchantRule("Taxi", Regex("""(?i)\b(ola|uber|rapido|namma\s+metro|metro\s+rail|irctc|redbus|makemytrip|goibibo|cleartrip)\b""")),
        MerchantRule("Fuel", Regex("""(?i)\b(indian\s+oil|bharat\s+petroleum|hpcl|hindustan\s+petroleum|iocl|shell|essar\s+oil|nayara\s+energy)\b""")),
        MerchantRule("Bills & Utilities", Regex("""(?i)\b(airtel|jio\s*fiber|jio|vodafone|vi\s+recharge|bsnl|tata\s+power|bescom|adani\s+electricity|electricity\s+board)\b""")),
        MerchantRule("Health", Regex("""(?i)\b(apollo\s+pharmacy|1mg|tata\s*1mg|pharmeasy|netmeds|medplus|wellness\s+forever|practo)\b""")),
        MerchantRule("Entertainment", Regex("""(?i)\b(netflix|spotify|hotstar|jio\s*hotstar|prime\s+video|youtube\s+premium|bookmyshow|pvr|inox)\b""")),
        MerchantRule("Education", Regex("""(?i)\b(udemy|coursera|unacademy|byju'?s|upgrad|vedantu|simplilearn)\b"""))
    )

    internal fun categoryForSms(merchant: String?, body: String, income: Boolean): String {
        val text = "${merchant.orEmpty()} $body".lowercase(Locale.ROOT)
        if (income) {
            return if (Regex("""(?i)\b(salary|payroll|wages)\b""").containsMatchIn(text)) "Salary" else "Other income"
        }

        // A known merchant wins over generic UPI/payment words in the SMS.
        merchantRules.firstOrNull { it.pattern.containsMatchIn(text) }?.let { return it.category }

        return when {
            Regex("""(?i)\b(neft|imps|rtgs|bank transfer|own account transfer|self transfer|transfer between accounts|transferred to (?:your )?(?:own )?account)\b""")
                .containsMatchIn(text) -> "Bank transfer"
            Regex("""(?i)\b(shopping|retail|purchase|online order|e-commerce)\b""").containsMatchIn(text) -> "Shopping"
            Regex("""(?i)\b(ola|uber|rapido|taxi|cab ride|metro|namma metro|irctc|redbus|makemytrip)\b""").containsMatchIn(text) -> "Taxi"
            Regex("""(?i)\b(swiggy|zomato|restaurant|cafe|food delivery|dominos|mcdonald|kfc|pizzahut|food order)\b""").containsMatchIn(text) -> "Food & Dining"
            Regex("""(?i)\b(bigbasket|blinkit|zepto|instamart|grocery|groceries)\b""").containsMatchIn(text) -> "Groceries"
            Regex("""(?i)\b(petrol|fuel|diesel|indian oil|bharat petroleum|hpcl|shell fuel)\b""").containsMatchIn(text) -> "Fuel"
            Regex("""(?i)\b(electricity|water bill|gas bill|broadband|mobile recharge|utility|jio fiber|airtel)\b""").containsMatchIn(text) -> "Bills & Utilities"
            Regex("""(?i)\b(pharmacy|medical|hospital|clinic|medicine|apollo pharmacy)\b""").containsMatchIn(text) -> "Health"
            Regex("""(?i)\b(movie|cinema|netflix|spotify|hotstar|prime video|bookmyshow)\b""").containsMatchIn(text) -> "Entertainment"
            Regex("""(?i)\b(school|college|university|tuition|course fee|education|udemy|coursera)\b""").containsMatchIn(text) -> "Education"
            Regex("""(?i)\b(insurance|premium payment|lic premium)\b""").containsMatchIn(text) -> "Insurance"
            else -> "Other"
        }
    }


    fun import(context: Context, senderRaw: String, timestamp: Long, body: String): Boolean = synchronized(lock) {
        val sender = senderRaw.trim()
        val receivedDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val parsed = BankSmsParser.parse(body, receivedDate) ?: return false
        val key = "sms-import:${sender.lowercase(Locale.ROOT)}:$timestamp:${body.hashCode()}"
        val prefs = context.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
        val rows = runCatching { JSONArray(prefs.getString("transactions", "[]") ?: "[]") }
            .getOrElse { JSONArray() }
        for (i in 0 until rows.length()) {
            if (rows.optJSONObject(i)?.optString("notificationKey") == key) return false
        }

        val title = parsed.merchant?.let { (if (parsed.income) "From " else "To ") + it }
            ?: if (sender.isBlank()) "Bank SMS transaction" else "Bank SMS · $sender"
        val notes = buildString {
            append("Imported automatically from a bank SMS.")
            if (parsed.exactAmount.contains('.')) {
                append(" Exact SMS amount: ₹").append(parsed.exactAmount)
                append(". Stored amount is rounded to the nearest rupee.")
            }
        }
        rows.put(JSONObject()
            .put("title", title)
            .put("category", categoryForSms(parsed.merchant, body, parsed.income))
            .put("date", parsed.date)
            .put("amount", parsed.amount)
            .put("income", parsed.income)
            .put("source", "SMS auto import")
            .put("notificationKey", key)
            .put("captureStatus", "")
            .put("paymentMethod", "Not set")
            .put("notes", notes)
            .put("tag", ""))
        prefs.edit().putString("transactions", rows.toString()).commit()
    }
}
