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

    internal fun categoryForSms(merchant: String?, body: String, income: Boolean): String {
        val text = "${merchant.orEmpty()} $body".lowercase(Locale.ROOT)
        if (income) return if (Regex("(?i)\\b(salary|payroll|wages)\\b").containsMatchIn(text)) "Salary" else "Other income"
        return when {
            Regex("(?i)\\b(amazon|flipkart|myntra|ajio|meesho|nykaa|shopping|retail|purchase)\\b").containsMatchIn(text) -> "Shopping"
            Regex("(?i)\\b(ola|uber|rapido|taxi|cab ride)\\b").containsMatchIn(text) -> "Taxi"
            Regex("(?i)\\b(swiggy|zomato|restaurant|cafe|food delivery|dominos|mcdonald)\\b").containsMatchIn(text) -> "Food & Dining"
            Regex("(?i)\\b(bigbasket|blinkit|zepto|instamart|grocery|groceries)\\b").containsMatchIn(text) -> "Groceries"
            Regex("(?i)\\b(petrol|fuel|diesel|indian oil|bharat petroleum|hpcl)\\b").containsMatchIn(text) -> "Fuel"
            Regex("(?i)\\b(electricity|water bill|gas bill|broadband|mobile recharge|utility)\\b").containsMatchIn(text) -> "Bills & Utilities"
            Regex("(?i)\\b(pharmacy|medical|hospital|clinic|medicine)\\b").containsMatchIn(text) -> "Health"
            Regex("(?i)\\b(movie|cinema|netflix|spotify|hotstar)\\b").containsMatchIn(text) -> "Entertainment"
            Regex("(?i)\\b(neft|imps|rtgs|bank transfer|own account|transferred to|upi transfer)\\b").containsMatchIn(body) -> "Bank transfer"
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
