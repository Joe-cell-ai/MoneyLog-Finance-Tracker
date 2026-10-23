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

class EmiReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val lender = intent.getStringExtra("lender") ?: "Loan"
        val amount = intent.getLongExtra("amount", 0L)
        val channelId = "emi_reminders"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "EMI reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders before loan EMI due dates"
                }
            )
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Upcoming EMI: $lender")
            .setContentText(if (amount > 0) "Please prepare your EMI payment of ₹$amount." else "Your EMI is coming up. Check your loan schedule.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(intent.getIntExtra("notificationId", 1001), notification)
        val day = intent.getIntExtra("dayOfMonth", 1).coerceIn(1, 28)
        scheduleMonthly(context, lender, amount, day, intent.getIntExtra("notificationId", 1001))
    }

    companion object {
        fun scheduleMonthly(context: Context, lender: String, amount: Long, dayOfMonth: Int, notificationId: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, EmiReminderReceiver::class.java).apply {
                putExtra("lender", lender)
                putExtra("amount", amount)
                putExtra("notificationId", notificationId)
                putExtra("dayOfMonth", dayOfMonth.coerceIn(1, 28))
            }
            val pending = PendingIntent.getBroadcast(
                context, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val next = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.MONTH, 1)
            }
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
        }

        fun cancelMonthly(context: Context, lender: String) {
            cancelReminder(context, 3000 + (lender.lowercase(java.util.Locale.ROOT).hashCode() and 0x3fff))
        }

        fun cancelReminder(context: Context, notificationId: Int) {
            val pending = PendingIntent.getBroadcast(
                context, notificationId, Intent(context, EmiReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pending != null) {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
                pending.cancel()
            }
        }
    }
}