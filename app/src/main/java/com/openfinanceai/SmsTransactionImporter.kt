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

    private const val OWN_ACCOUNT_KEYS = "sms_own_account_keys"

    /** Accounts (for example "icici:450") that bank SMS have shown to belong to this user. */
    fun ownAccountKeys(context: Context): Set<String> =
        context.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
            .getStringSet(OWN_ACCOUNT_KEYS, emptySet()).orEmpty().toSet()

    /**
     * Remembers the account an SMS was sent about so later transfers to it are recognised as
     * own-account transfers. Only the bank name and last digits are stored.
     */
    fun learnOwnAccount(context: Context, body: String) {
        val key = SmsCategorizer.ownAccountKeyFrom(body) ?: return
        val prefs = context.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
        val current = prefs.getStringSet(OWN_ACCOUNT_KEYS, emptySet()).orEmpty()
        if (key in current) return
        prefs.edit().putStringSet(OWN_ACCOUNT_KEYS, HashSet(current).apply { add(key) }).apply()
    }

    /** The single categorisation entry point shared by automatic and manual SMS import. */
    fun categorize(context: Context, sender: String, body: String, parsed: ParsedBankSmsTransaction): SmsCategorization =
        SmsCategorizer.categorize(
            SmsCategoryInput(
                sender = sender,
                payee = parsed.merchant,
                body = body,
                income = parsed.income,
                ownAccountKeys = ownAccountKeys(context)
            )
        )

    fun import(context: Context, senderRaw: String, timestamp: Long, body: String): Boolean = synchronized(lock) {
        val sender = senderRaw.trim()
        learnOwnAccount(context, body)
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
            .put("category", categorize(context, sender, body, parsed).category)
            .put("date", parsed.date)
            .put("amount", parsed.amount)
            .put("income", parsed.income)
            .put("source", "SMS auto import")
            .put("notificationKey", key)
            .put("captureStatus", "")
            .put("paymentMethod", "Not set")
            .put("notes", notes)
            .put("tag", "")
            .put("categoryOrigin", CategoryOrigin.AUTO))
        prefs.edit().putString("transactions", rows.toString()).commit()
    }
}

