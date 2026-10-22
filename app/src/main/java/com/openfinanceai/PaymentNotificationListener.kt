package com.openfinanceai

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Optional notification capture; it does not verify settlement with a bank. */
class PaymentNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        if (packageName == applicationContext.packageName) return
        val extras = sbn.notification.extras ?: return
        val lines = mutableListOf<String>()
        listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT).forEach { key ->
            extras.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(lines::add)
        }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach {
            it?.toString()?.trim()?.takeIf { line -> line.isNotBlank() }?.let(lines::add)
        }
        val body = lines.distinct().joinToString(" · ")
        if (body.isBlank()) return
        val paymentWords = Regex("(?i)\\b(paid|payment|debited|debit|sent|transferred|transfer successful|transaction successful|UPI txn|UPI payment|received|credited)\\b")
        val amountMatch = Regex("(?i)(?:₹|INR|Rs\\.?\\s*)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)").find(body)
            ?: Regex("(?i)\\b(?:paid|debited|credited|sent|received)\\s+(?:of\\s+)?(?:INR|Rs\\.?\\s*)?([0-9,]+(?:\\.[0-9]{1,2})?)").find(body)
            ?: return
        if (!paymentWords.containsMatchIn(body)) return
        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull()?.toLong() ?: return
        if (amount <= 0L) return
        val titleLine = lines.firstOrNull().orEmpty()
        val source = when {
            packageName.contains("dreamplug", true) || titleLine.contains("CRED", true) -> "CRED"
            packageName.contains("paisa", true) || titleLine.contains("Google Pay", true) -> "Google Pay"
            packageName.contains("phonepe", true) || titleLine.contains("PhonePe", true) -> "PhonePe"
            packageName.contains("paytm", true) || titleLine.contains("Paytm", true) -> "Paytm"
            else -> applicationLabel(packageName)
        }
        val title = extractRecipient(body, titleLine, source)
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(sbn.postTime))
        val isIncome = Regex("(?i)\\b(received|credited)\\b").containsMatchIn(body) &&
            !Regex("(?i)\\b(debited|paid|sent)\\b").containsMatchIn(body)
        val prefs = getSharedPreferences("open_finance_data", MODE_PRIVATE)
        val transactions = runCatching { JSONArray(prefs.getString("transactions", "[]")) }.getOrDefault(JSONArray())
        val notificationKey = sbn.key + "|" + amount
        val duplicate = (0 until transactions.length()).any { i ->
            val old = transactions.optJSONObject(i) ?: return@any false
            old.optString("source") == source && old.optString("notificationKey") == notificationKey
        }
        if (duplicate) return
        transactions.put(JSONObject().apply {
            put("title", title)
            put("category", if (isIncome) "Income" else "UPI / payment")
            put("date", date)
            put("amount", amount)
            put("income", isIncome)
            put("source", source)
            put("notificationKey", notificationKey)
            put("captureStatus", "From notification; verify against bank record")
        })
        prefs.edit().putString("transactions", transactions.toString()).apply()
        sendBroadcast(Intent(ACTION_TRANSACTION_CAPTURED).setPackage(applicationContext.packageName))
    }

    private fun applicationLabel(packageName: String): String = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault("Payment notification")

    private fun extractRecipient(body: String, title: String, source: String): String {
        val patterns = listOf(
            Regex("(?i)(?:paid to|sent to|transferred to|payment to|merchant)\\s*[:：-]?\\s*([^·,\\n]+)"),
            Regex("(?i)(?:from)\\s+([^·,\\n]+)")
        )
        for (pattern in patterns) {
            val candidate = pattern.find(body)?.groupValues?.getOrNull(1)?.trim()
            if (!candidate.isNullOrBlank() && candidate.length <= 80) return source + " · " + candidate
        }
        return source + " · " + title.ifBlank { "Payment transaction" }
    }

    companion object {
        const val ACTION_TRANSACTION_CAPTURED = "com.openfinanceai.TRANSACTION_CAPTURED"
    }
}
