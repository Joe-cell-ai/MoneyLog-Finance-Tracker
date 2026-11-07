package com.openfinanceai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Shared, idempotent SMS persistence for live receiver and hourly inbox sync. */
internal object SmsTransactionImporter {
    private val lock = Any()

    internal fun availableCategories(context: Context): List<String> {
        val prefs = context.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
        val raw = prefs.getString("custom_categories", null)
        if (raw.isNullOrBlank()) {
            return if (prefs.getBoolean("custom_categories_initialized", false)) emptyList()
            else DEFAULT_MONEY_CATEGORIES
        }
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.optString(it).trim() }.filter { it.isNotBlank() }
        }.getOrDefault(DEFAULT_MONEY_CATEGORIES)
    }

    // Compatibility entry point retained for callers/tests; the engine remains the
    // single source of categorisation rules.
    internal fun categoryForSms(merchant: String?, body: String, income: Boolean): String {
        val decision = SmsCategorizationEngine.categorize(
            null, merchant, body, income, DEFAULT_MONEY_CATEGORIES
        )
        if (income) return if (Regex("""(?i)\\b(salary|payroll|wages)\\b""").containsMatchIn("$merchant $body")) "Salary" else "Other income"
        return when {
            decision.transferKind.isNotBlank() -> "Bank transfer"
            decision.category == "Household" -> "Shopping"
            decision.category == "Food/Takeout" -> "Food & Dining"
            decision.category == "Food" -> "Groceries"
            decision.category == "Transport/Taxi" -> "Taxi"
            decision.category == "Transport/Fuel" -> "Fuel"
            decision.category == "Utilities/Electricity" -> "Bills & Utilities"
            decision.category == "Healthcare" -> "Health"
            decision.category == "Leisure" -> "Entertainment"
            else -> decision.category
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

        val decision = SmsCategorizationEngine.categorize(
            sender, parsed.merchant, body, parsed.income, availableCategories(context)
        )
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
            .put("category", decision.category)
            .put("date", parsed.date)
            .put("amount", parsed.amount)
            .put("income", parsed.income)
            .put("source", "SMS auto import")
            .put("notificationKey", key)
            .put("captureStatus", "")
            .put("paymentMethod", "Not set")
            .put("notes", notes)
            .put("tag", "")
            .put("transactionKind", decision.transferKind)
            .put("categoryManuallySelected", false))
        prefs.edit().putString("transactions", rows.toString()).commit()
    }
}
