package com.openfinanceai

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Calendar

/** Monthly local reminder for the accrued interest on each active Gold Loan note. */
class GoldLoanInterestReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val noteId = intent?.getStringExtra(EXTRA_NOTE_ID) ?: return
        val note = loadMoneyNotes(context).firstOrNull { it.id == noteId }
        if (note == null || note.loanType != MoneyNoteLoanType.GOLD_LOAN ||
            note.interestRateBasisPoints <= 0 || note.status == MoneyNoteStatus.SETTLED) {
            cancel(context, noteId)
            return
        }

        val interest = note.balance.unpaidInterestPaise
        val channelId = "gold_loan_interest"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Gold loan interest reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Monthly reminders to pay accrued gold loan interest"
                }
            )
        }
        val amount = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN"))
            .apply { minimumFractionDigits = 0; maximumFractionDigits = 2 }
            .format(interest / 100.0)
        val message = if (interest > 0L) {
            "Accrued interest of $amount is due for ${note.personName}. Principal is not included."
        } else {
            "Check your monthly interest payment for ${note.personName}."
        }
        manager.notify(notificationId(noteId), NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Gold loan interest payment")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build())
        schedule(context, note)
    }

    companion object {
        private const val EXTRA_NOTE_ID = "goldLoanNoteId"
        private const val ACTION = "com.openfinanceai.GOLD_LOAN_INTEREST_DUE"

        private fun notificationId(noteId: String): Int =
            50_000 + (noteId.hashCode() and 0x3fff)

        fun sync(context: Context, notes: List<MoneyNote>) {
            val activeIds = notes.filter {
                it.loanType == MoneyNoteLoanType.GOLD_LOAN &&
                    it.interestRateBasisPoints > 0 && it.status != MoneyNoteStatus.SETTLED
            }.map { it.id }.toSet()
            notes.filter { it.loanType == MoneyNoteLoanType.GOLD_LOAN }.forEach { note ->
                if (note.id in activeIds) schedule(context, note) else cancel(context, note.id)
            }
            val prefs = context.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
            val old = prefs.getStringSet("gold_loan_reminder_ids", emptySet()).orEmpty()
            (old - activeIds).forEach { cancel(context, it) }
            prefs.edit().putStringSet("gold_loan_reminder_ids", activeIds).apply()
        }

        private fun schedule(context: Context, note: MoneyNote) {
            val day = runCatching { LocalDate.parse(note.date).dayOfMonth }.getOrDefault(1)
            val today = LocalDate.now()
            var dueMonth = YearMonth.from(today)
            var due = dueMonth.atDay(minOf(day, dueMonth.lengthOfMonth()))
            if (!due.isAfter(today) || (due == today && Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 9)) {
                dueMonth = dueMonth.plusMonths(1)
                due = dueMonth.atDay(minOf(day, dueMonth.lengthOfMonth()))
            }
            val trigger = due.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val intent = Intent(context, GoldLoanInterestReminderReceiver::class.java).apply {
                action = ACTION
                putExtra(EXTRA_NOTE_ID, note.id)
            }
            val pending = PendingIntent.getBroadcast(
                context, notificationId(note.id), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }

        private fun cancel(context: Context, noteId: String) {
            val intent = Intent(context, GoldLoanInterestReminderReceiver::class.java).apply { action = ACTION }
            val pending = PendingIntent.getBroadcast(
                context, notificationId(noteId), intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
            pending.cancel()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(notificationId(noteId))
        }
    }
}
