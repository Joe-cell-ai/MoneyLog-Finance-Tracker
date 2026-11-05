package com.openfinanceai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

class SalaryAutoWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("salary_auto_enabled", false)) return Result.success()
        val amount = prefs.getLong("salary_auto_amount", 0L)
        if (amount <= 0L) return Result.success()

        return try {
            val today = LocalDate.now()
            val month = YearMonth.from(today)
            val payday = paydayFor(month)
            if (today.isBefore(payday)) return Result.success()

            synchronized(SalaryAutoWorker::class.java) {
                val transactions = runCatching {
                    JSONArray(prefs.getString("transactions", "[]") ?: "[]")
                }.getOrElse { JSONArray() }
                val key = "salary-auto:${month}"
                for (i in 0 until transactions.length()) {
                    if (transactions.optJSONObject(i)?.optString("notificationKey") == key) {
                        return@synchronized
                    }
                }
                transactions.put(JSONObject()
                    .put("title", "Monthly salary")
                    .put("category", "Salary")
                    .put("date", payday.toString())
                    .put("amount", amount)
                    .put("income", true)
                    .put("source", "Automatic salary")
                    .put("notificationKey", key)
                    .put("captureStatus", "")
                    .put("paymentMethod", "Bank transfer")
                    .put("notes", "Automatically recorded using your configured monthly payday rule.")
                    .put("tag", ""))
                prefs.edit().putString("transactions", transactions.toString()).commit()
                applicationContext.sendBroadcast(
                    android.content.Intent(SmsAutoImportReceiver.ACTION_IMPORTED)
                        .setPackage(applicationContext.packageName)
                )
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK = "moneylog_salary_daily"

        internal fun paydayFor(month: YearMonth): LocalDate {
            var date = month.atDay(month.lengthOfMonth().coerceAtMost(31))
            while (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
                date = date.minusDays(1)
            }
            return date
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SalaryAutoWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<SalaryAutoWorker>().build())
        }
    }
}
