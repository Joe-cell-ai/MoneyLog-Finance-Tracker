package com.openfinanceai

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a dated, restorable MoneyLog JSON backup into a folder selected by the user.
 * Selecting the folder through Android's Storage Access Framework avoids requesting
 * broad access to the user's Google Drive files.
 */
class GoogleDriveBackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
        val treeString = prefs.getString("drive_backup_tree_uri", null)
            ?: return fail(prefs, "Connect a Google Drive folder before backing up.")
        return try {
            val treeUri = Uri.parse(treeString)
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val fileUri = DocumentsContract.createDocument(
                applicationContext.contentResolver, rootUri, "application/json",
                "MoneyLog-backup-$timestamp.json"
            ) ?: error("Google Drive could not create the backup file.")

            val root = JSONObject()
                .put("format", "open-finance-backup")
                .put("version", 1)
                .put("transactions", JSONArray(prefs.getString("transactions", "[]") ?: "[]"))
                .put("loans", JSONArray(prefs.getString("loans", "[]") ?: "[]"))
                .put("categories", JSONArray(prefs.getString("custom_categories", "[]") ?: "[]"))
                .put("tags", JSONArray(prefs.getString("custom_tags", "[]") ?: "[]"))
                .put("appearanceTheme", prefs.getString("appearance_theme", "Light"))
                .put("appearanceCurrency", prefs.getString("appearance_currency", "INR (₹)"))
                .put("appearanceAccent", prefs.getString("appearance_accent", "Green"))
                .put("appearanceLayout", prefs.getString("appearance_layout", "Comfortable"))
                .put("appearanceFontSize", prefs.getString("appearance_font_size", "Medium"))
                .put("settingsBudgetAlerts", prefs.getBoolean("settings_budget_alerts", true))
                .put("settingsEmiReminders", prefs.getBoolean("settings_emi_reminders", true))
                .put("settingsMonthlySummary", prefs.getBoolean("settings_monthly_summary", false))
                .put("userReportedCreditScore", prefs.getInt("user_reported_credit_score", 0))
                .put("monthlyCategoryBudgets", JSONObject(prefs.getString("monthly_category_budgets", "{}") ?: "{}"))

            val schedules = JSONObject()
            prefs.all.keys.filter { it.startsWith("bank_schedule_") }.forEach { key ->
                val raw = prefs.getString(key, "[]") ?: "[]"
                schedules.put(key, JSONArray(raw))
            }
            root.put("bankSchedules", schedules)

            applicationContext.contentResolver.openOutputStream(fileUri, "wt")?.bufferedWriter()?.use {
                it.write(root.toString(2))
            } ?: error("Could not write the backup to the selected folder.")

            prefs.edit().putString("drive_backup_status", "Last successful backup: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())).apply()
            Result.success()
        } catch (e: Exception) {
            fail(prefs, "Google Drive backup failed: " + (e.message ?: "unknown error"))
        }
    }

    private fun fail(prefs: android.content.SharedPreferences, message: String): Result {
        prefs.edit().putString("drive_backup_status", message).apply()
        return Result.retry()
    }
}
