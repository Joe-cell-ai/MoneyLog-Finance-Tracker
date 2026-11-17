package com.openfinanceai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Single source of truth for the restorable MoneyLog JSON backup payload. */
object MoneyLogBackupPayload {
    fun create(context: Context): JSONObject {
        CreditCardBills.read(context)
        val prefs = context.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
            val root = JSONObject()
                .put("format", "open-finance-backup")
                .put("version", 1)
                .put("transactions", JSONArray(prefs.getString("transactions", "[]") ?: "[]"))
                .put("loans", JSONArray(prefs.getString("loans", "[]") ?: "[]"))
                .put("categories", JSONArray(prefs.getString("custom_categories", "[]") ?: "[]"))
                .put("tags", JSONArray(prefs.getString("custom_tags", "[]") ?: "[]"))
                .put("moneyNotes", JSONArray(prefs.getString("money_notes", "[]") ?: "[]"))
                .put("creditCards", JSONArray(prefs.getString("cc_cards_v2", "[]") ?: "[]"))
                .put("creditCardBills", JSONArray(prefs.getString("cc_bills_v2", "[]") ?: "[]"))
                .put("creditCardPayments", JSONArray(prefs.getString("cc_payments_v2", "[]") ?: "[]"))
                .put("creditCardAmountUnit", "PAISE")
                .put("creditCardReminders", JSONObject()
                    .put("7Days", prefs.getBoolean("cc_reminder_7", true))
                    .put("3Days", prefs.getBoolean("cc_reminder_3", true))
                    .put("1Day", prefs.getBoolean("cc_reminder_1", true)))
                .put("appearanceTheme", prefs.getString("appearance_theme", "Light"))
                .put("appearanceCurrency", prefs.getString("appearance_currency", "INR (₹)"))
                .put("appearanceAccent", prefs.getString("appearance_accent", "Green"))
                .put("appearanceCustomAccent", prefs.getString("appearance_custom_accent", "#9333EA"))
                .put("appearanceLayout", prefs.getString("appearance_layout", "Comfortable"))
                .put("appearanceFontSize", prefs.getString("appearance_font_size", "Medium"))
                .put("settingsBudgetAlerts", prefs.getBoolean("settings_budget_alerts", true))
                .put("settingsEmiReminders", prefs.getBoolean("settings_emi_reminders", true))
                .put("settingsMonthlySummary", prefs.getBoolean("settings_monthly_summary", false))
                .put("userReportedCreditScore", prefs.getInt("user_reported_credit_score", 0))
                .put("monthlyCategoryBudgets", JSONObject(prefs.getString("monthly_category_budgets", "{}") ?: "{}"))

            val loanMetadata = JSONObject()
            prefs.all.keys.filter { it.startsWith("loan_document_metadata_") }.forEach { key ->
                loanMetadata.put(key, prefs.getString(key, "{}") ?: "{}")
            }
            val emiPreferences = JSONObject()
            prefs.all.keys.filter { key ->
                key.startsWith("emi_status_") || key.startsWith("emi_amount_") || key.startsWith("emi_rescheduled_")
            }.forEach { key ->
                when (val value = prefs.all[key]) {
                    is Number -> emiPreferences.put(key, value.toLong())
                    is String -> emiPreferences.put(key, value)
                }
            }
            root.put("loanDocumentMetadata", loanMetadata)
                .put("emiPreferences", emiPreferences)

            val schedules = JSONObject()
            prefs.all.keys.filter { it.startsWith("bank_schedule_") }.forEach { key ->
                val raw = prefs.getString(key, "[]") ?: "[]"
                schedules.put(key, JSONArray(raw))
            }
            root.put("bankSchedules", schedules)

            return root
    }
}
