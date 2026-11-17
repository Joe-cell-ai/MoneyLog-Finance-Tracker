package com.openfinanceai

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Uploads the shared MoneyLog backup payload to the user's OneDrive using Microsoft Graph. */
class OneDriveBackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
        return try {
            check(BuildConfig.MICROSOFT_CLIENT_ID.isNotBlank()) {
                "OneDrive is not configured in this build. Add the Microsoft application Client ID."
            }
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val fileName = "MoneyLog-backup-" + timestamp + ".json"
            val root = MoneyLogBackupPayload.create(applicationContext)
            OneDriveGraph.uploadBackup(applicationContext, fileName, root.toString(2))
            val status = "Last successful OneDrive backup: " +
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            prefs.edit().putString("drive_backup_status", status).apply()
            Result.success()
        } catch (error: Exception) {
            val message = "OneDrive backup failed: " + (error.message ?: "unknown error")
            prefs.edit().putString("drive_backup_status", message).apply()
            Result.retry()
        }
    }
}
