package com.openfinanceai

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * Opt-in listener for future bank transaction alerts.
 * Only notification text that matches the existing transaction parser is retained.
 * No notification content is uploaded or forwarded.
 */
class BankNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val body = listOf(title, text).filter { it.isNotBlank() }.distinct().joinToString(": ")
        if (body.isBlank()) return

        val postedAt = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        val receivedDate = Instant.ofEpochMilli(postedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        if (BankSmsParser.parse(body, receivedDate) == null) return

        val prefs = getSharedPreferences("open_finance_data", MODE_PRIVATE)
        synchronized(LOCK) {
            val existing = runCatching {
                JSONArray(prefs.getString(STORAGE_KEY, "[]") ?: "[]")
            }.getOrDefault(JSONArray())
            val key = sbn.key
            val updated = JSONArray()
            for (i in 0 until existing.length()) {
                val item = existing.optJSONObject(i) ?: continue
                if (item.optString("key") != key) updated.put(item)
            }
            updated.put(JSONObject().apply {
                put("key", key)
                put("packageName", sbn.packageName)
                put("body", body)
                put("postedAt", postedAt)
            })
            // Bound local storage to the latest 200 matching bank alerts.
            val bounded = JSONArray()
            val start = (updated.length() - MAX_ALERTS).coerceAtLeast(0)
            for (i in start until updated.length()) bounded.put(updated.getJSONObject(i))
            prefs.edit().putString(STORAGE_KEY, bounded.toString()).apply()
        }
    }

    companion object {
        private const val STORAGE_KEY = "captured_bank_notifications"
        private const val MAX_ALERTS = 200
        private val LOCK = Any()
    }
}
