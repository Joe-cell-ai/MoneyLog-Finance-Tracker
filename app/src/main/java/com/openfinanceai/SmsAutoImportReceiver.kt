package com.openfinanceai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Saves clear bank transaction alerts as locally stored, reviewable transactions.
 * The existing parser rejects OTPs, failed payments and ambiguous messages.
 */
class SmsAutoImportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val prefs = context.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
        val transactions = runCatching {
            JSONArray(prefs.getString("transactions", "[]") ?: "[]")
        }.getOrElse { JSONArray() }

        val knownKeys = buildSet {
            for (i in 0 until transactions.length()) {
                add(transactions.optJSONObject(i)?.optString("notificationKey").orEmpty())
            }
        }
        var added = 0

        messages.forEach { sms ->
            val body = sms.messageBody.orEmpty()
            val sender = sms.originatingAddress.orEmpty().trim()
            val receivedAt = sms.timestampMillis
            val receivedDate = Instant.ofEpochMilli(receivedAt)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            val parsed = BankSmsParser.parse(body, receivedDate) ?: return@forEach
            val stableId = "${sender.lowercase(Locale.ROOT)}:$receivedAt:${body.hashCode()}"
            val notificationKey = "sms-import:$stableId"
            if (notificationKey in knownKeys) return@forEach

            val notes = buildString {
                append("Automatically detected from a bank SMS. Verify this entry against your bank statement.")
                if (parsed.exactAmount.contains('.')) {
                    append(" Exact SMS amount: ₹").append(parsed.exactAmount)
                    append(". Stored amount is rounded to the nearest rupee.")
                }
            }
            val title = parsed.merchant?.let { (if (parsed.income) "From " else "To ") + it }
                ?: if (sender.isBlank()) "Bank SMS transaction" else "Bank SMS · " + sender

            transactions.put(JSONObject()
                .put("title", title)
                .put("category", if (parsed.income) "Other income" else "Bank transfer")
                .put("date", parsed.date)
                .put("amount", parsed.amount)
                .put("income", parsed.income)
                .put("source", "SMS auto import")
                .put("notificationKey", notificationKey)
                .put("captureStatus", "Needs review")
                .put("paymentMethod", "Not set")
                .put("notes", notes)
                .put("tag", "")
            )
            added++
        }

        if (added > 0) {
            prefs.edit().putString("transactions", transactions.toString()).apply()
            context.sendBroadcast(
                Intent(ACTION_IMPORTED).setPackage(context.packageName)
            )
        }
    }

    companion object {
        const val ACTION_IMPORTED = "com.openfinanceai.ACTION_SMS_TRANSACTION_IMPORTED"
    }
}
