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
import java.util.Calendar

/**
 * Local EMI reminders. A due-date notification offers explicit payment actions.
 * Follow-up alarms are independent of the monthly due-date alarm so marking one
 * installment paid stops its daily reminders without disabling future installments.
 */
class EmiReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val lender = intent.getStringExtra(EXTRA_LENDER) ?: "Loan"
        val amount = intent.getLongExtra(EXTRA_AMOUNT, 0L)
        val id = intent.getIntExtra(EXTRA_ID, DEFAULT_ID)
        val day = intent.getIntExtra(EXTRA_DAY, 1).coerceIn(1, 28)
        val action = intent.action ?: ACTION_DUE
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        when (action) {
            ACTION_PAID -> {
                prefs.edit().putBoolean(paidKey(id), true).apply()
                cancelReminder(context, followupRequestCode(id))
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id)
                showNotification(context, id, lender, amount, "Payment marked as paid", "Your EMI payment of ₹$amount for $lender is marked as paid.", showActions = false)
            }
            ACTION_YET_TO_PAY -> {
                prefs.edit().putBoolean(paidKey(id), false).apply()
                scheduleAt(context, ACTION_CONFIRM, lender, amount, id, day, nextTime(21, 0))
            }
            ACTION_OVERDUE -> {
                prefs.edit().putBoolean(paidKey(id), false).apply()
                scheduleAt(context, ACTION_OVERDUE_DAILY, lender, amount, id, day, nextTime(9, 0, tomorrowIfPassed = true))
            }
            ACTION_CONFIRM -> {
                if (!prefs.getBoolean(paidKey(id), false)) {
                    showNotification(context, id, lender, amount, "EMI payment confirmation", "Your EMI of ₹$amount for $lender is still unpaid. Please confirm its status.", true)
                }
            }
            ACTION_OVERDUE_DAILY -> {
                if (!prefs.getBoolean(paidKey(id), false)) {
                    showNotification(context, id, lender, amount, "EMI overdue: $lender", "Your EMI of ₹$amount is overdue. Mark it as paid when payment is complete.", true)
                    scheduleAt(context, ACTION_OVERDUE_DAILY, lender, amount, id, day, nextTime(9, 0, tomorrowIfPassed = true))
                }
            }
            else -> {
                // A new monthly installment starts a new payment cycle.
                prefs.edit().putBoolean(paidKey(id), false).apply()
                scheduleMonthly(context, lender, amount, day, id)
                showNotification(context, id, lender, amount, "EMI due today: $lender", "Your EMI of ₹$amount is due today. Please confirm your payment status.", true)
            }
        }
    }

    private fun showNotification(
        context: Context,
        id: Int,
        lender: String,
        amount: Long,
        title: String,
        message: String,
        showActions: Boolean
    ) {
        val channelId = "emi_reminders"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "EMI reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "EMI due-date, payment confirmation, and overdue reminders"
                }
            )
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        if (showActions) {
            builder.addAction(0, "Paid", actionIntent(context, ACTION_PAID, lender, amount, id, 30000 + id))
            builder.addAction(0, "Yet to Pay", actionIntent(context, ACTION_YET_TO_PAY, lender, amount, id, 31000 + id))
            builder.addAction(0, "Overdue", actionIntent(context, ACTION_OVERDUE, lender, amount, id, 32000 + id))
        }
        manager.notify(id, builder.build())
    }

    private fun actionIntent(context: Context, action: String, lender: String, amount: Long, id: Int, requestCode: Int): PendingIntent {
        val intent = Intent(context, EmiReminderReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_LENDER, lender)
            putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val PREFS = "open_finance_data"
        private const val EXTRA_LENDER = "lender"
        private const val EXTRA_AMOUNT = "amount"
        private const val EXTRA_ID = "notificationId"
        private const val EXTRA_DAY = "dayOfMonth"
        private const val DEFAULT_ID = 1001
        private const val ACTION_DUE = "com.openfinanceai.EMI_DUE"
        private const val ACTION_CONFIRM = "com.openfinanceai.EMI_CONFIRM"
        private const val ACTION_OVERDUE_DAILY = "com.openfinanceai.EMI_OVERDUE_DAILY"
        private const val ACTION_PAID = "com.openfinanceai.EMI_PAID"
        private const val ACTION_YET_TO_PAY = "com.openfinanceai.EMI_YET_TO_PAY"
        private const val ACTION_OVERDUE = "com.openfinanceai.EMI_OVERDUE"

        private fun paidKey(id: Int) = "emi_paid_$id"
        private fun followupRequestCode(id: Int) = 40000 + id

        fun scheduleMonthly(context: Context, lender: String, amount: Long, dayOfMonth: Int, notificationId: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, EmiReminderReceiver::class.java).apply {
                action = ACTION_DUE
                putExtra(EXTRA_LENDER, lender)
                putExtra(EXTRA_AMOUNT, amount)
                putExtra(EXTRA_ID, notificationId)
                putExtra(EXTRA_DAY, dayOfMonth.coerceIn(1, 28))
            }
            val pending = PendingIntent.getBroadcast(
                context, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val next = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceIn(1, 28))
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.MONTH, 1)
            }
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
        }

        private fun scheduleAt(
            context: Context,
            action: String,
            lender: String,
            amount: Long,
            notificationId: Int,
            dayOfMonth: Int,
            time: Calendar
        ) {
            val intent = Intent(context, EmiReminderReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_LENDER, lender)
                putExtra(EXTRA_AMOUNT, amount)
                putExtra(EXTRA_ID, notificationId)
                putExtra(EXTRA_DAY, dayOfMonth)
            }
            val pending = PendingIntent.getBroadcast(
                context, followupRequestCode(notificationId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.timeInMillis, pending)
        }

        private fun nextTime(hour: Int, minute: Int, tomorrowIfPassed: Boolean = false): Calendar =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    if (tomorrowIfPassed) add(Calendar.DAY_OF_YEAR, 1)
                    else add(Calendar.DAY_OF_YEAR, 1)
                }
            }

        fun cancelMonthly(context: Context, lender: String) {
            cancelReminder(context, 3000 + (lender.lowercase(java.util.Locale.ROOT).hashCode() and 0x3fff))
        }

        fun cancelReminder(context: Context, notificationId: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val dueIntent = Intent(context, EmiReminderReceiver::class.java).apply { action = ACTION_DUE }
            val duePending = PendingIntent.getBroadcast(
                context, notificationId, dueIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (duePending != null) {
                alarmManager.cancel(duePending)
                duePending.cancel()
            }
            listOf(ACTION_OVERDUE_DAILY, ACTION_CONFIRM).forEach { action ->
                val followIntent = Intent(context, EmiReminderReceiver::class.java).apply { this.action = action }
                val followPending = PendingIntent.getBroadcast(
                    context, followupRequestCode(notificationId), followIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (followPending != null) {
                    alarmManager.cancel(followPending)
                    followPending.cancel()
                }
            }
        }
    }
}
