package com.openfinanceai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class SmsHourlySyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (applicationContext.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        return try {
            val cutoff = System.currentTimeMillis() - 90L * 24L * 60L * 60L * 1000L
            val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
            var added = 0
            applicationContext.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                Telephony.Sms.DATE + " >= ?",
                arrayOf(cutoff.toString()),
                Telephony.Sms.DATE + " DESC"
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
                var scanned = 0
                while (cursor.moveToNext() && scanned < 1000) {
                    scanned++
                    val sender = if (addressIndex >= 0) cursor.getString(addressIndex).orEmpty() else ""
                    val body = cursor.getString(bodyIndex).orEmpty()
                    val timestamp = cursor.getLong(dateIndex)
                    if (SmsTransactionImporter.import(applicationContext, sender, timestamp, body)) added++
                }
            }
            if (added > 0) applicationContext.sendBroadcast(
                android.content.Intent(SmsAutoImportReceiver.ACTION_IMPORTED).setPackage(applicationContext.packageName)
            )
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK = "moneylog_sms_hourly_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SmsHourlySyncWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
