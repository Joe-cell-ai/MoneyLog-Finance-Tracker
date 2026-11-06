package com.openfinanceai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import java.util.Locale

/**
 * Android clears AlarmManager alarms on reboot. Restore the saved EMI schedule and
 * the periodic SMS inbox sync after boot or an app update.
 */
class MoneyLogBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val prefs = context.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
        if (context.checkSelfPermission(android.Manifest.permission.READ_SMS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            SmsHourlySyncWorker.schedule(context)
        }

        if (!prefs.getBoolean("settings_emi_reminders", true)) return
        val loans = runCatching {
            JSONArray(prefs.getString("loans", "[]") ?: "[]")
        }.getOrElse { JSONArray() }

        for (index in 0 until loans.length()) {
            val loan = loans.optJSONObject(index) ?: continue
            val lender = loan.optString("lender").trim()
            val due = loan.optString("due")
            val amount = loan.optLong("emi", 0L)
            if (lender.isBlank() || due.isBlank() ||
                due.equals("Set due date", ignoreCase = true) || amount <= 0L) continue
            val dueDay = Regex("^\\s*(\\d{1,2})").find(due)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 28) ?: continue
            val id = 3000 + (lender.lowercase(Locale.ROOT).hashCode() and 0x3fff)
            EmiReminderReceiver.scheduleMonthly(context, lender, amount, dueDay, id)
        }
    }
}
