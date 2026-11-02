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
 * Writes a dated, restorable MoneyLog JSON backup into a user-selected folder.
 * The folder may be local device storage or Google Drive, selected through SAF.
 */
class GoogleDriveBackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
        val destination = prefs.getString("automatic_backup_destination", "device") ?: "device"
        val folderKey = if (destination == "drive") "drive_backup_tree_uri" else "device_backup_tree_uri"
        val treeString = prefs.getString(folderKey, null)
            ?: return fail(prefs, if (destination == "drive") "Connect a Google Drive folder before backing up." else "Choose a device backup folder before backing up.")

        return try {
            val treeUri = Uri.parse(treeString)
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val fileName = "MoneyLog-backup-$timestamp.json"

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

            val fileUri = DocumentsContract.createDocument(
                applicationContext.contentResolver, rootUri, "application/json", fileName
            ) ?: error("The selected folder could not create the backup file.")

            applicationContext.contentResolver.openOutputStream(fileUri, "wt")?.bufferedWriter()?.use {
                it.write(root.toString(2))
            } ?: error("Could not write the backup to the selected folder.")

            val retention = prefs.getInt("automatic_backup_retention", 30).coerceIn(1, 90)
            pruneOldBackups(treeUri, rootId, retention)

            val destinationLabel = if (destination == "drive") "Google Drive" else "device folder"
            val status = "Last successful $destinationLabel backup: " +
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            prefs.edit().putString("drive_backup_status", status).apply()
            Result.success()
        } catch (e: Exception) {
            fail(prefs, "Backup failed: " + (e.message ?: "unknown error"))
        }
    }

    private fun pruneOldBackups(treeUri: Uri, rootId: String, retention: Int) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        data class BackupFile(val uri: Uri, val modified: Long)
        val backups = mutableListOf<BackupFile>()
        applicationContext.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                if (!name.startsWith("MoneyLog-backup-") || !name.endsWith(".json")) continue
                val documentId = cursor.getString(idColumn) ?: continue
                val modified = if (modifiedColumn >= 0) cursor.getLong(modifiedColumn) else 0L
                backups += BackupFile(DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId), modified)
            }
        }
        backups.sortedBy { it.modified }
            .take((backups.size - retention).coerceAtLeast(0))
            .forEach { oldFile ->
                runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, oldFile.uri) }
            }
    }

    private fun fail(prefs: android.content.SharedPreferences, message: String): Result {
        prefs.edit().putString("drive_backup_status", message).apply()
        return Result.retry()
    }
}
